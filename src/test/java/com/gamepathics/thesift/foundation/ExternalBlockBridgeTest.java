package com.gamepathics.thesift.foundation;

import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import com.gamepathics.thesift.foundation.api.SiftBlockProvider;
import com.gamepathics.thesift.foundation.api.SiftItemProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class ExternalBlockBridgeTest {
    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        MockBukkit.createMockPlugin("ItemsAdder");
        plugin.getConfig().set("integrations.blocks.provider", "itemsadder");
        plugin.getConfig().set("integrations.blocks.definitions.charoite_ore", "thesift:charoite_ore");
        CustomBlock.setPlacedIdForTest(null);
        FixtureProvider.ownsNote = false;
    }

    @AfterEach
    void tearDown() {
        CustomBlock.setPlacedIdForTest(null);
        FixtureProvider.ownsNote = false;
        MockBukkit.unmock();
    }

    @Test
    void itemsAdderBlockMustMatchConfiguredNamespacedId() {
        var block = server.addSimpleWorld("block-bridge-test").getBlockAt(0, 64, 0);
        ExternalBlockBridge bridge = new ExternalBlockBridge(plugin);
        CustomBlock.setPlacedIdForTest("otherpack:charoite_ore");
        assertFalse(bridge.isBlock("charoite_ore", block));

        CustomBlock.setPlacedIdForTest("thesift:charoite_ore");
        assertTrue(bridge.isBlock("charoite_ore", block));
    }

    @Test
    void portalMelodyRequiresTaggedDeepslateAndLeavesProviderBlocksAlone() {
        var world = server.addSimpleWorld("sonorous-fixture");
        Block base = world.getBlockAt(0, 63, 0);
        Block note = world.getBlockAt(0, 64, 0);
        base.setType(Material.DEEPSLATE);
        note.setType(Material.NOTE_BLOCK);
        RiftManager rifts = new RiftManager(plugin);
        SonorousBlockRegistry tags = new SonorousBlockRegistry(plugin);
        assertFalse(rifts.canHandlePortalSong(note), "untagged vanilla note blocks keep vanilla handling");
        tags.mark(base);
        assertTrue(rifts.canHandlePortalSong(note));

        plugin.getConfig().set("integrations.blocks.provider", "fixture");
        plugin.getConfig().set("integrations.blocks.definitions.siftslate", "thesift:siftslate");
        plugin.getServer().getServicesManager().register(SiftBlockProvider.class, new FixtureProvider(), plugin,
                ServicePriority.Normal);
        assertTrue(rifts.canHandlePortalSong(note), "a tagged provider-supplied Sonorous Deepslate base is a valid station");
        FixtureProvider.ownsNote = true;
        assertTrue(new ExternalBlockBridge(plugin).isProviderOwned(note));
        assertFalse(rifts.canHandlePortalSong(note), "provider-owned note blocks must keep their interaction");
    }

    @Test
    void providerCreatedSonorousItemPlacedThroughListenerRegistersStation() {
        var world = server.addSimpleWorld("sonorous-provider-place");
        Block base = world.getBlockAt(0, 63, 0);
        Block note = world.getBlockAt(0, 64, 0);
        base.setType(Material.DEEPSLATE);
        note.setType(Material.NOTE_BLOCK);
        plugin.getConfig().set("integrations.items.provider", "fixture-items");
        FixtureItemProvider provider = new FixtureItemProvider();
        server.getServicesManager().register(SiftItemProvider.class, provider, plugin, ServicePriority.Normal);

        ItemStack providerItem = provider.createItem("sonorous_deepslate");
        assertNotEquals("sonorous_deepslate", providerItem.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "content"), PersistentDataType.STRING),
                "the provider's native item has no The Sift PDC marker");
        Player player = server.addPlayer();
        SiftiteListener listener = new SiftiteListener(plugin, new SiftiteContent(plugin));
        listener.onSonorousDeepslatePlace(new BlockPlaceEvent(base, base.getState(), world.getBlockAt(0, 62, 0),
                providerItem, player, true, EquipmentSlot.HAND));

        assertTrue(new RiftManager(plugin).canHandlePortalSong(note),
                "the provider's item ID is resolved through the item provider and tagged on placement");
    }

    @Test
    void legacyWorldIndexMigratesProviderBlocksIntoChunkDataAndDropsVanillaFallbacks() {
        var world = server.addSimpleWorld("sonorous-legacy-migration");
        Block providerBlock = world.getBlockAt(0, 63, 0);
        Block vanillaFallback = world.getBlockAt(64, 62, 0);
        providerBlock.setType(Material.DEEPSLATE);
        vanillaFallback.setType(Material.DEEPSLATE);
        plugin.getConfig().set("integrations.blocks.provider", "fixture");
        server.getServicesManager().register(SiftBlockProvider.class, new FixtureProvider(), plugin,
                ServicePriority.Normal);
        NamespacedKey legacy = new NamespacedKey(plugin, "sonorous_deepslate_positions");
        world.getPersistentDataContainer().set(legacy, PersistentDataType.STRING, "0,63,0;64,62,0");

        SonorousBlockRegistry tags = new SonorousBlockRegistry(plugin);
        assertTrue(tags.contains(providerBlock));
        assertFalse(tags.contains(vanillaFallback));
        assertNull(world.getPersistentDataContainer().get(legacy, PersistentDataType.STRING));
        assertFalse(tags.contains(world.getBlockAt(0, 63, 1)), "lookup stays scoped to the tagged coordinate");
    }

    @Test
    void breakingOrReplacingSonorousBlockRemovesItsStationMarker() {
        var world = server.addSimpleWorld("sonorous-cleanup");
        Block base = world.getBlockAt(0, 63, 0);
        Block note = world.getBlockAt(0, 64, 0);
        base.setType(Material.DEEPSLATE);
        note.setType(Material.NOTE_BLOCK);
        Player player = server.addPlayer();
        SiftiteListener listener = new SiftiteListener(plugin, new SiftiteContent(plugin));
        ItemStack tag = new ItemStack(Material.DEEPSLATE);
        var meta = tag.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "content"),
                PersistentDataType.STRING, "sonorous_deepslate");
        tag.setItemMeta(meta);
        listener.onSonorousDeepslatePlace(new BlockPlaceEvent(base, base.getState(), world.getBlockAt(0, 62, 0),
                tag, player, true, EquipmentSlot.HAND));
        assertTrue(new RiftManager(plugin).canHandlePortalSong(note));

        listener.onSonorousDeepslateBreak(new org.bukkit.event.block.BlockBreakEvent(base, player));
        assertFalse(new RiftManager(plugin).canHandlePortalSong(note));
    }

    private static final class FixtureProvider implements SiftBlockProvider {
        private static boolean ownsNote;
        @Override public String getProviderId() { return "fixture"; }
        @Override public boolean placeBlock(String contentId, Location location) { return false; }
        @Override public boolean supports(String contentId) { return true; }
        @Override public boolean isBlock(String contentId, Block block) { return block.getY() == 63 || ownsNote && block.getY() == 64; }
    }

    private static final class FixtureItemProvider implements SiftItemProvider {
        @Override public String getProviderId() { return "fixture-items"; }
        @Override public ItemStack createItem(String contentId) {
            return "sonorous_deepslate".equals(contentId) ? new ItemStack(Material.DEEPSLATE) : null;
        }
        @Override public boolean supports(String contentId) { return "sonorous_deepslate".equals(contentId); }
    }
}
