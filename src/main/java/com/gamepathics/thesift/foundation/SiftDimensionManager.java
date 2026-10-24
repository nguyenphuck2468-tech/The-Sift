package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SiftDimensionManager {
    public static final NamespacedKey DIMENSION_KEY = NamespacedKey.fromString("thesift:the_sift");

    private final JavaPlugin plugin;

    public SiftDimensionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public World getOrCreateWorld() {
        ProviderReadiness readiness = new ProviderReadiness(plugin);
        if (readiness.strict() && !readiness.dimensionAllowed()) {
            plugin.getLogger().severe("Blocked The Sift dimension load: provider requirements are unresolved. Run /sift doctor. Existing dimension save was left untouched.");
            return null;
        }
        World world = Bukkit.getWorld(DIMENSION_KEY);
        if (world != null) {
            return world;
        }
        return Bukkit.createWorld(WorldCreator.ofKey(DIMENSION_KEY));
    }

    public boolean enter(Player player) {
        World world = getOrCreateWorld();
        if (world == null) {
            player.sendMessage("The Sift dimension is unavailable. Run /sift doctor for provider readiness, then check TheSift/provided in /datapack list.");
            plugin.getLogger().warning("Could not load dimension " + DIMENSION_KEY
                    + ". Ensure the bundled TheSift/provided datapack is enabled.");
            return false;
        }

        Location spawn = world.getHighestBlockAt(0, 0).getLocation().add(0.5, 1.0, 0.5);
        player.teleport(spawn);
        player.sendMessage("You have entered The Sift.");
        return true;
    }

    public boolean returnToOverworld(Player player) {
        World overworld = Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
        if (overworld == null) {
            player.sendMessage("The Overworld is unavailable.");
            return false;
        }

        Location spawn = overworld.getSpawnLocation();
        player.teleport(spawn);
        player.sendMessage("You have returned to the Overworld.");
        return true;
    }
}
