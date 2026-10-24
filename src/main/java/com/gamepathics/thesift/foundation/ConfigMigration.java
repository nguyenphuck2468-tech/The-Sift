package com.gamepathics.thesift.foundation;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigMigration {
    public static final int CURRENT_VERSION = 3;

    private ConfigMigration() {
    }

    public static boolean migrate(JavaPlugin plugin) {
        Objects.requireNonNull(plugin);
        return migrate(plugin.getConfig(), plugin.getDataFolder(), plugin::saveConfig, plugin.getLogger());
    }

    static boolean migrate(FileConfiguration config, File dataFolder, Runnable saveConfig, Logger logger) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(dataFolder);
        Objects.requireNonNull(saveConfig);
        Objects.requireNonNull(logger);
        int version = config.getInt("config-version", 1);
        if (version > CURRENT_VERSION) {
            logger.log(Level.SEVERE,
                    "Config version {0} is newer than supported version {1}; refusing to rewrite it.",
                    new Object[]{version, CURRENT_VERSION});
            return false;
        }
        if (!IchorState.migrateLegacyConfig(config, dataFolder, saveConfig, logger)) {
            return false;
        }
        if (version < CURRENT_VERSION) {
            config.set("config-version", CURRENT_VERSION);
            try {
                saveConfig.run();
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Could not save migrated The Sift config.", exception);
                return false;
            }
            logger.info("Migrated The Sift foundation config to version " + CURRENT_VERSION + ".");
        }
        return true;
    }
}
