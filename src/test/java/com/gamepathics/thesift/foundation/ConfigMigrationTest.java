package com.gamepathics.thesift.foundation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigMigrationTest {

    private JavaPlugin mockPlugin;
    private File mockDataFolder;
    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(JavaPlugin.class);
        mockDataFolder = new File("target/test-data");
        mockDataFolder.mkdirs();
        config = new YamlConfiguration();
        when(mockPlugin.getConfig()).thenReturn(config);
        when(mockPlugin.getDataFolder()).thenReturn(mockDataFolder);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("test"));
    }

    @Test
    void migrate_whenConfigVersionMissing_defaultsToVersion1AndMigratesTo2() {
        // config-version not set, defaults to 1
        boolean result = ConfigMigration.migrate(mockPlugin);
        
        assertTrue(result);
        assertEquals(2, config.getInt("config-version"));
        verify(mockPlugin).saveConfig();
    }

    @Test
    void migrate_whenConfigVersionIs1_migratesTo2() {
        config.set("config-version", 1);
        
        boolean result = ConfigMigration.migrate(mockPlugin);
        
        assertTrue(result);
        assertEquals(2, config.getInt("config-version"));
        verify(mockPlugin).saveConfig();
    }

    @Test
    void migrate_whenConfigVersionIs2_noMigrationNeeded() {
        config.set("config-version", 2);
        
        boolean result = ConfigMigration.migrate(mockPlugin);
        
        assertTrue(result);
        assertEquals(2, config.getInt("config-version"));
        // saveConfig should not be called since already at current version
        verify(mockPlugin, never()).saveConfig();
    }

    @Test
    void migrate_whenConfigVersionIsNewer_returnsFalseAndLogsSevere() {
        config.set("config-version", 3); // newer than CURRENT_VERSION (2)
        
        boolean result = ConfigMigration.migrate(mockPlugin);
        
        assertFalse(result);
        assertEquals(3, config.getInt("config-version")); // unchanged
        verify(mockPlugin, never()).saveConfig();
    }

    @Test
    void migrate_withNullPlugin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> ConfigMigration.migrate(null));
    }

    @Test
    void currentVersionIs2() {
        assertEquals(2, ConfigMigration.CURRENT_VERSION);
    }
}