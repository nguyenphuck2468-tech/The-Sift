package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class EchoGolemManagerTest {

    private JavaPlugin mockPlugin;
    private EchoGolemManager echoGolemManager;

    @BeforeEach
    void setUp() {
        mockPlugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(mockPlugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        Mockito.when(mockPlugin.getServer()).thenReturn(Mockito.mock(org.bukkit.Server.class));
        
        echoGolemManager = new EchoGolemManager(mockPlugin);
    }

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> echoGolemManager.shutdown());
    }

    @Test
    void start_doesNotThrow() {
        assertDoesNotThrow(() -> echoGolemManager.start());
    }
}

class RiftManagerTest {

    private JavaPlugin mockPlugin;
    private RiftManager riftManager;

    @BeforeEach
    void setUp() {
        mockPlugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(mockPlugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        Mockito.when(mockPlugin.getServer()).thenReturn(Mockito.mock(org.bukkit.Server.class));
        
        riftManager = new RiftManager(mockPlugin);
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