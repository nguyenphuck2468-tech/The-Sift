package com.gamepathics.thesift.foundation;

import com.gamepathics.thesift.foundation.api.SiftBlockProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review P2: the growth tick must not sweep every loaded chunk's full world height. Crops
 * register in their chunk's PDC when planted and unregister when they break or mature, and
 * the tick only visits registered positions (stale entries are culled, not retried forever).
 */
class SculkflowerGrowthTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private SculkflowerManager manager;
    private World world;
    private NamespacedKey cropsKey;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("providers.strict", false);
        manager = new SculkflowerManager(plugin, new SiftiteContent(plugin));
        cropsKey = new NamespacedKey(plugin, "sculkflower_crops");
        world = server.addSimpleWorld("sift-test");
        server.getServicesManager().register(SiftBlockProvider.class, new FixtureBlockProvider(),
                plugin, org.bukkit.plugin.ServicePriority.Normal);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** In-memory block provider so crop placement and identity work inside MockBukkit. */
    static final class FixtureBlockProvider implements SiftBlockProvider {
        private final Map<String, Set<Location>> placed = new HashMap<>();

        @Override public String getProviderId() { return "fixture-blocks"; }
        @Override public boolean supports(String contentId) { return contentId.startsWith("sculkflower"); }
        @Override public boolean placeBlock(String contentId, Location location) {
            Block block = location.getBlock();
            block.setType("sculkflower".equals(contentId) ? Material.CACTUS_FLOWER : Material.WHEAT);
            placed.computeIfAbsent(contentId, key -> new HashSet<>()).add(block.getLocation());
            // The placed block replaces whatever stood there: drop other identities at this
            // position so a stage advance really moves the crop between stage sets.
            for (Map.Entry<String, Set<Location>> entry : placed.entrySet()) {
                if (!entry.getKey().equals(contentId)) entry.getValue().remove(block.getLocation());
            }
            return true;
        }
        @Override public boolean isBlock(String contentId, Block block) {
            Set<Location> positions = placed.get(contentId);
            return positions != null && positions.contains(block.getLocation());
        }
    }

    private long[] registry(Block block) {
        return block.getChunk().getPersistentDataContainer().get(cropsKey, PersistentDataType.LONG_ARRAY);
    }

    private Block plantedCrop(int x, int y, int z) {
        Block crop = world.getBlockAt(x, y, z);
        assertTrue(blocksPlace("sculkflower_crop_stage0", crop), "fixture provider must place the crop");
        manager.registerCrop(crop);
        return crop;
    }

    private boolean blocksPlace(String id, Block block) {
        return new ExternalBlockBridge(plugin).place(id, block.getLocation());
    }

    @Test
    void plantedCropRegistersAndMatureFlowerUnregisters() {
        Block crop = plantedCrop(3, 70, 5);
        assertEquals(0, manager.cropAge(crop));
        assertEquals(1, registry(crop).length);

        // Stage 0 -> stage 1: still registered.
        assertTrue(manager.grow(crop, 1));
        assertEquals(1, registry(crop).length);

        // Stage 1 -> flower: the mature plant leaves the registry (nothing left to grow).
        assertTrue(manager.grow(crop, 2));
        assertNull(registry(crop));
    }

    @Test
    void brokenCropUnregisters() {
        Block crop = plantedCrop(7, 72, 9);
        manager.unregisterCrop(crop);
        assertNull(registry(crop));
    }

    @Test
    void duplicateRegistrationsAreIgnored() {
        Block crop = plantedCrop(1, 70, 1);
        manager.registerCrop(crop);
        assertEquals(1, registry(crop).length);
    }

    @Test
    void cropYRoundTripsThroughThePackedEntry() {
        int y = world.getMinHeight() + 2;
        Block crop = plantedCrop(2, y, 12);
        long packed = registry(crop)[0];

        assertEquals(y, (int) (packed >> 8), "Y must survive the packed entry (incl. sign)");
        assertEquals(2, (int) ((packed >> 4) & 0xF));
        assertEquals(12, (int) (packed & 0xF));
    }

    @Test
    void growthTickCullsStaleRegistryEntriesWithoutScanning() {
        Block crop = world.getBlockAt(5, 70, 6);
        manager.registerCrop(crop); // registered, but the block was never placed
        assertEquals(1, registry(crop).length);

        boolean removed = false;
        for (int i = 0; i < 64 && !removed; i++) {
            manager.tickGrowth(world);
            removed = registry(crop) == null;
        }
        assertTrue(removed, "the growth tick must cull a stale entry when it samples it");
        assertEquals(Material.AIR, crop.getType(), "culling must not place blocks at stale positions");
    }

    @Test
    void growthTickGrowsRegisteredCrops() {
        Block crop = plantedCrop(9, 70, 4);

        // The tick can advance stage 0 -> 1 -> flower inside one tick (each sample has a 1/3
        // growth gate), so accept any observable maturation signal; a cull without maturation
        // would leave the type at WHEAT with an empty registry, which must NOT happen here.
        boolean grew = false;
        for (int i = 0; i < 256 && !grew; i++) {
            manager.tickGrowth(world);
            grew = crop.getType() == Material.CACTUS_FLOWER || manager.cropAge(crop) >= 1;
        }
        assertTrue(grew, "a registered stage-0 crop must mature (type=" + crop.getType()
                + ", registry=" + java.util.Arrays.toString(registry(crop)) + ")");
        if (crop.getType() == Material.CACTUS_FLOWER) {
            assertNull(registry(crop), "the mature flower leaves the registry");
        }
    }

    @Test
    void chunksWithoutRegistrationsAreSkipped() {
        manager.tickGrowth(world);
        assertEquals(Material.AIR, world.getBlockAt(0, 70, 0).getType());
    }
}
