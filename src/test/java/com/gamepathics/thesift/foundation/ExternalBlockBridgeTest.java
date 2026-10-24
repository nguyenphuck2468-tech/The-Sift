package com.gamepathics.thesift.foundation;

import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import com.gamepathics.thesift.foundation.api.SiftBlockProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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

    private static final class FixtureProvider implements SiftBlockProvider {
        private static boolean ownsNote;
        @Override public String getProviderId() { return "fixture"; }
        @Override public boolean placeBlock(String contentId, Location location) { return false; }
        @Override public boolean supports(String contentId) { return true; }
        @Override public boolean isBlock(String contentId, Block block) { return block.getY() == 63 || ownsNote && block.getY() == 64; }
    }
}
