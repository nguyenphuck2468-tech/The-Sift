package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public final class IchorState {
    private final JavaPlugin plugin;
    private final File storageFile;
    private final Map<String, Integer> cauldrons = new HashMap<>();
    private final Map<String, String> blocks = new HashMap<>();
    private boolean dirty = false;

    public IchorState(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.storageFile = new File(plugin.getDataFolder(), "ichor-state.yml");
        load();
        // Schedule periodic saves to avoid saving on every single change
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveIfDirty, 20 * 60, 20 * 60);
    }

    public int level(Location location) {
        return cauldrons.getOrDefault(key(location), 0);
    }

    public void setLevel(Location location, int level) {
        String key = key(location);
        if (level <= 0) {
            cauldrons.remove(key);
        } else {
            cauldrons.put(key, level);
        }
        markDirty();
    }

    public void setBlock(Location location, String id) {
        blocks.put(key(location), id);
        markDirty();
    }

    public String block(Location location) {
        return blocks.get(key(location));
    }

    public void removeBlock(Location location) {
        blocks.remove(key(location));
        markDirty();
    }

    public void shutdown() {
        save();
    }

    private void load() {
        if (!storageFile.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        
        if (config.isConfigurationSection("cauldrons")) {
            for (String key : config.getConfigurationSection("cauldrons").getKeys(false)) {
                cauldrons.put(key, config.getInt("cauldrons." + key));
            }
        }
        if (config.isConfigurationSection("blocks")) {
            for (String key : config.getConfigurationSection("blocks").getKeys(false)) {
                blocks.put(key, config.getString("blocks." + key, ""));
            }
        }
        plugin.getLogger().info("Loaded Ichor state: " + cauldrons.size() + " cauldrons, " + blocks.size() + " blocks");
    }

    private void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private void save() {
        if (!dirty && storageFile.exists()) {
            return;
        }
        FileConfiguration config = new YamlConfiguration();
        config.set("cauldrons", cauldrons);
        config.set("blocks", blocks);
        try {
            config.save(storageFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save Ichor state", e);
        }
    }

    private void markDirty() {
        dirty = true;
    }

    private String key(Location location) {
        UUID worldUid = location.getWorld() != null ? location.getWorld().getUID() : new UUID(0, 0);
        return worldUid + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}