package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RiftBlockMetadataTest {
    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void unmarkedBlockIsNotTreatedAsRiftBlock() {
        Player player = server.addPlayer();
        var block = player.getWorld().getBlockAt(0, 64, 0);
        block.setType(Material.STONE);
        BlockBreakEvent event = new BlockBreakEvent(block, player);

        new RiftManager(plugin).onRiftBlockBreak(event);

        assertFalse(event.isCancelled());
    }
}
