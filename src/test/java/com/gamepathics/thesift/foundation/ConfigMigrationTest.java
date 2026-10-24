package com.gamepathics.thesift.foundation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {

    @TempDir
    Path temporaryDirectory;

    private File dataFolder;
    private File configFile;
    private YamlConfiguration config;
    private int saveCalls;
    private Logger logger;

    @BeforeEach
    void setUp() throws IOException {
        dataFolder = temporaryDirectory.toFile();
        config = new YamlConfiguration();
        saveCalls = 0;
        logger = Logger.getLogger("test");
        configFile = new File(dataFolder, "config.yml");
        config.save(configFile);
    }

    private boolean migrate() {
        return ConfigMigration.migrate(config, dataFolder, () -> {
            saveCalls++;
            try {
                config.save(configFile);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }, logger);
    }

    @Test
    void migrate_whenConfigVersionMissing_defaultsToVersion1AndMigratesTo3() {
        // config-version not set, defaults to 1
        boolean result = migrate();
        
        assertTrue(result);
        assertEquals(3, config.getInt("config-version"));
        assertEquals(1, saveCalls);
    }

    @Test
    void migrate_whenConfigVersionIs1_migratesTo3() {
        config.set("config-version", 1);
        
        boolean result = migrate();
        
        assertTrue(result);
        assertEquals(3, config.getInt("config-version"));
        assertEquals(1, saveCalls);
    }

    @Test
    void migrate_whenConfigVersionIs3_noMigrationNeeded() {
        config.set("config-version", 3);
        
        boolean result = migrate();
        
        assertTrue(result);
        assertEquals(3, config.getInt("config-version"));
        // saveConfig should not be called since already at current version
        assertEquals(0, saveCalls);
    }

    @Test
    void migrate_whenConfigVersionIsNewer_returnsFalseAndLogsSevere() {
        config.set("config-version", 4); // newer than CURRENT_VERSION (3)
        
        boolean result = migrate();
        
        assertFalse(result);
        assertEquals(4, config.getInt("config-version")); // unchanged
        assertEquals(0, saveCalls);
    }

    @Test
    void migrate_withNullPlugin_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> ConfigMigration.migrate(null));
    }

    @Test
    void migrate_movesLegacyIchorStateAndKeepsBackup() throws IOException {
        config.set("config-version", 2);
        config.set("runtime.ichor-cauldrons.world:1:2:3", 4);
        config.set("runtime.ichor-blocks.world:4:5:6", "ichor_spring");
        config.save(new File(dataFolder, "config.yml"));

        assertTrue(migrate());

        YamlConfiguration state = YamlConfiguration.loadConfiguration(
                new File(dataFolder, "ichor-state.yml"));
        assertEquals(4, state.getInt("cauldrons.world:1:2:3"));
        assertEquals("ichor_spring", state.getString("blocks.world:4:5:6"));
        assertFalse(config.contains("runtime.ichor-cauldrons"));
        assertFalse(config.contains("runtime.ichor-blocks"));
        assertEquals(3, config.getInt("config-version"));

        File backup = new File(dataFolder, "config.yml.pre-ichor-state-migration.bak");
        assertTrue(backup.isFile());
        YamlConfiguration backedUpConfig = YamlConfiguration.loadConfiguration(backup);
        assertEquals(4, backedUpConfig.getInt("runtime.ichor-cauldrons.world:1:2:3"));
    }

    @Test
    void migrate_isIdempotentAndDoesNotOverwriteExistingState() throws IOException {
        config.set("config-version", 2);
        config.set("runtime.ichor-cauldrons.world:1:2:3", 4);
        config.set("runtime.ichor-cauldrons.world:9:8:7", 5);
        config.set("runtime.ichor-blocks.world:4:5:6", "legacy_block");
        config.save(new File(dataFolder, "config.yml"));
        YamlConfiguration state = new YamlConfiguration();
        state.set("cauldrons.world:1:2:3", 9);
        state.set("blocks.world:4:5:6", "new_block");
        state.save(new File(dataFolder, "ichor-state.yml"));

        assertTrue(migrate());
        assertTrue(migrate());

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(
                new File(dataFolder, "ichor-state.yml"));
        assertEquals(9, migrated.getInt("cauldrons.world:1:2:3"));
        assertEquals(5, migrated.getInt("cauldrons.world:9:8:7"));
        assertEquals("new_block", migrated.getString("blocks.world:4:5:6"));
        assertTrue(new File(dataFolder, "config.yml.pre-ichor-state-migration.bak").isFile());
    }

    @Test
    void migrate_whenStateWriteFailsRetainsLegacyKeys() throws IOException {
        config.set("config-version", 2);
        config.set("runtime.ichor-cauldrons.world:1:2:3", 4);
        config.save(configFile);
        File statePathAsDirectory = new File(dataFolder, "ichor-state.yml");
        assertTrue(statePathAsDirectory.mkdir());

        assertFalse(migrate());

        assertTrue(config.contains("runtime.ichor-cauldrons"));
        assertTrue(new File(dataFolder, "config.yml.pre-ichor-state-migration.bak").isFile());
        assertEquals(0, saveCalls);
    }

    @Test
    void migrate_withoutLegacyKeysDoesNotCreateIchorStateFile() {
        config.set("config-version", 2);

        assertTrue(migrate());

        assertFalse(new File(dataFolder, "ichor-state.yml").exists());
        assertEquals(3, config.getInt("config-version"));
    }

    @Test
    void currentVersionIs3() {
        assertEquals(3, ConfigMigration.CURRENT_VERSION);
    }
}
