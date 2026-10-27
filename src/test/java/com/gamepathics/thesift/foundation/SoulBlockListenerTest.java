package com.gamepathics.thesift.foundation;

import com.gamepathics.thesift.foundation.api.SiftItemProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SoulBlockListenerTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private Player player;
    private SoulBlockListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        player = server.addPlayer();
        plugin.getConfig().set("integrations.items.provider", "fixture-items");
        SiftiteContent content = new SiftiteContent(plugin);
        listener = new SoulBlockListener(plugin, content);
        server.getServicesManager().register(SiftItemProvider.class, new FixtureItemProvider(), plugin,
                ServicePriority.Normal);
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void providerItemMarksSoulBlockAndVanillaReplacementClearsStaleMarker() {
        var world = server.addSimpleWorld("soul-block-provider");
        Block placed = world.getBlockAt(1, 64, 1);
        placed.setType(Material.SOUL_SAND);
        ItemStack providerItem = new FixtureItemProvider().createItem("soul_block");
        listener.onPlace(placeEvent(placed, providerItem));

        BlockBreakEvent customBreak = new BlockBreakEvent(placed, player);
        listener.onBreak(customBreak);
        assertTrue(!customBreak.isDropItems(), "provider's soul_block item is recognized without The Sift PDC");

        // A vanilla replacement at the same location must not inherit the old identity marker.
        listener.onPlace(placeEvent(placed, new ItemStack(Material.SOUL_SAND)));
        BlockBreakEvent vanillaBreak = new BlockBreakEvent(placed, player);
        listener.onBreak(vanillaBreak);
        assertTrue(vanillaBreak.isDropItems(), "ordinary Soul Sand must keep its normal drops");
    }

    @Test
    void pistonTransfersSoulBlockIdentityToMovedBlockPosition() {
        var world = server.addSimpleWorld("soul-block-piston");
        Block source = world.getBlockAt(1, 64, 1);
        Block destination = world.getBlockAt(2, 64, 1);
        source.setType(Material.SOUL_SAND);
        destination.setType(Material.SOUL_SAND); // Represents Paper's post-move block state.
        SoulBlockListener.markGenerated(plugin, source);

        listener.onPistonExtend(new BlockPistonExtendEvent(world.getBlockAt(0, 64, 1),
                java.util.List.of(source), BlockFace.EAST));
        server.getScheduler().performOneTick();

        assertTrue(!listener.isMarked(source));
        assertTrue(listener.isMarked(destination));
    }

    private BlockPlaceEvent placeEvent(Block placed, ItemStack item) {
        return new BlockPlaceEvent(placed, placed.getState(), placed.getRelative(0, -1, 0), item,
                player, true, EquipmentSlot.HAND);
    }

    @Test
    void soulBlockLootTableCarriesTheContentTagUsedByTheBreakHandler() throws Exception {
        // The bundled block loot table must produce exactly the PDC shape that
        // SiftiteContent.isSiftiteItem / SoulBlockListener read (key thesift:content).
        java.nio.file.Path loot = java.nio.file.Path.of(
                "src/main/resources/datapack/data/thesift/loot_table/block/soul_block.json");
        com.google.gson.JsonObject table = com.google.gson.JsonParser
                .parseString(java.nio.file.Files.readString(loot)).getAsJsonObject();
        String entryName = table.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString();
        assertEquals("minecraft:soul_sand", entryName,
                "Soul Block stands in for SOUL_SAND; the loot entry must match");
        String tag = table.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonArray("functions").get(0).getAsJsonObject()
                .getAsJsonObject("components").getAsJsonObject("minecraft:custom_data")
                .get("thesift:content").getAsString();
        assertEquals("soul_block", tag,
                "loot drop must carry the same content id the Java listener expects");
    }

    private static final class FixtureItemProvider implements SiftItemProvider {
        @Override public String getProviderId() { return "fixture-items"; }
        @Override public ItemStack createItem(String contentId) {
            if (!"soul_block".equals(contentId)) return null;
            ItemStack item = new ItemStack(Material.SOUL_SAND);
            item.editMeta(meta -> meta.setDisplayName("Provider Soul Block"));
            return item;
        }
        @Override public boolean supports(String contentId) { return "soul_block".equals(contentId); }
    }
}
