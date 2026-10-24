package com.gamepathics.thesift.foundation;

import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.plugin.java.JavaPlugin;
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
    }

    @AfterEach
    void tearDown() {
        CustomBlock.setPlacedIdForTest(null);
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
}
