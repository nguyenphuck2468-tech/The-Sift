package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Location;
import org.bukkit.entity.IronGolem;
import org.bukkit.persistence.PersistentDataType;

import static org.junit.jupiter.api.Assertions.*;

class EchoGolemManagerTest {

    private ServerMock server;
    private JavaPlugin mockPlugin;
    private EchoGolemManager echoGolemManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        mockPlugin = MockBukkit.createMockPlugin("TheSift");
        echoGolemManager = new EchoGolemManager(mockPlugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> echoGolemManager.shutdown());
    }

    @Test
    void start_doesNotThrow() {
        assertDoesNotThrow(() -> echoGolemManager.start());
    }

    @Test
    void reloadedEchoGolemRecreatesItsBossBarOnce() {
        var world = server.addSimpleWorld("golem-load");
        IronGolem golem = world.spawn(new Location(world, 0, 64, 0), IronGolem.class);
        golem.getPersistentDataContainer().set(new NamespacedKey(mockPlugin, "echo_golem_entity"),
                PersistentDataType.STRING, "echo_golem");
        EntityAddToWorldEvent event = new EntityAddToWorldEvent(golem, world);

        echoGolemManager.onEntityAddedToWorld(event);
        echoGolemManager.onEntityAddedToWorld(event);

        assertEquals(1, echoGolemManager.trackedBossBarCount());
    }
}

class RiftManagerTest {

    private JavaPlugin mockPlugin;
    private RiftManager riftManager;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        riftManager = new RiftManager(mockPlugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createRiftKey_returnsItemWithPersistentData() {
        ItemStack key = riftManager.createRiftKey();
        
        assertNotNull(key);
        assertEquals(Material.TRIAL_KEY, key.getType());
        assertTrue(key.hasItemMeta());
        assertEquals("§5§lRift Key", key.getItemMeta().getDisplayName());
    }

    @Test
    void isRiftKey_returnsTrueForCreatedKey() {
        ItemStack key = riftManager.createRiftKey();
        
        assertTrue(riftManager.isRiftKey(key));
    }

    @Test
    void isRiftKey_returnsFalseForNull() {
        assertFalse(riftManager.isRiftKey(null));
    }

    @Test
    void isRiftKey_returnsFalseForRegularItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        
        assertFalse(riftManager.isRiftKey(item));
    }

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> riftManager.shutdown());
    }

    @Test
    void start_doesNotThrow() {
        assertDoesNotThrow(() -> riftManager.start());
    }
}
