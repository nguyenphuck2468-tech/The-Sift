package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class IchorState {
    private final JavaPlugin plugin;
    private final Map<String, Integer> cauldrons = new HashMap<>();
    private final Map<String, String> blocks = new HashMap<>();

    public IchorState(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        load();
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
        save();
    }

    public void setBlock(Location location, String id) {
        blocks.put(key(location), id);
        save();
    }

    public String block(Location location) {
        return blocks.get(key(location));
    }

    public void removeBlock(Location location) {
        blocks.remove(key(location));
        save();
    }

    private void load() {
        ConfigurationSection cauldronSection = plugin.getConfig().getConfigurationSection("runtime.ichor-cauldrons");
        if (cauldronSection != null) {
            for (String key : cauldronSection.getKeys(false)) {
                cauldrons.put(key, cauldronSection.getInt(key));
            }
        }
        ConfigurationSection blockSection = plugin.getConfig().getConfigurationSection("runtime.ichor-blocks");
        if (blockSection != null) {
            for (String key : blockSection.getKeys(false)) {
                blocks.put(key, blockSection.getString(key, ""));
            }
        }
    }

    private void save() {
        plugin.getConfig().set("runtime.ichor-cauldrons", cauldrons);
        plugin.getConfig().set("runtime.ichor-blocks", blocks);
        plugin.saveConfig();
    }

    private String key(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }
}
