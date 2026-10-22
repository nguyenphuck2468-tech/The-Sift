package com.gamepathics.thesift.foundation;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class ConfigMigration {
    public static final int CURRENT_VERSION = 2;

    private ConfigMigration() {
    }

    public static boolean migrate(JavaPlugin plugin) {
        Objects.requireNonNull(plugin);
        FileConfiguration config = plugin.getConfig();
        int version = config.getInt("config-version", 1);
        if (version > CURRENT_VERSION) {
            plugin.getLogger().log(Level.SEVERE,
                    "Config version {0} is newer than supported version {1}; refusing to rewrite it.",
                    new Object[]{version, CURRENT_VERSION});
            return false;
        }
        if (version < CURRENT_VERSION) {
            config.set("config-version", CURRENT_VERSION);
            plugin.saveConfig();
            plugin.getLogger().info("Migrated The Sift foundation config to version " + CURRENT_VERSION + ".");
        }
        return true;
    }
}
