package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SiftDimensionManager {
    public static final NamespacedKey DIMENSION_KEY = NamespacedKey.fromString("thesift:the_sift");
    private final NamespacedKey lastOverworldKey;

    private final JavaPlugin plugin;
    private final ProviderReadiness readiness;

    public SiftDimensionManager(JavaPlugin plugin) {
        this(plugin, new ProviderReadiness(plugin));
    }

    SiftDimensionManager(JavaPlugin plugin, ProviderReadiness readiness) {
        this.plugin = plugin;
        this.readiness = readiness;
        this.lastOverworldKey = new NamespacedKey(plugin, "sift_last_overworld");
    }

    public World getOrCreateWorld() {
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
            player.sendMessage("§c" + Messages.get("dimension.unavailable",
                    "The Sift dimension is unavailable. Run /sift doctor for provider readiness, then check TheSift/provided in /datapack list."));
            plugin.getLogger().warning("Could not load dimension " + DIMENSION_KEY
                    + ". Ensure the bundled TheSift/provided datapack is enabled.");
            return false;
        }

        // Reference-mod parity: the Sift maps 1:1 to the Overworld, so entering keeps the
        // player's X/Z and drops them on the surface there. The Overworld spot is saved per
        // player for /sift return.
        Location from = player.getLocation();
        Location spawn = world.getHighestBlockAt(from.getBlockX(), from.getBlockZ())
                .getLocation().add(0.5, 1.0, 0.5);
        player.getPersistentDataContainer().set(lastOverworldKey, PersistentDataType.INTEGER_ARRAY,
                new int[]{from.getBlockX(), from.getBlockY(), from.getBlockZ()});
        player.teleport(spawn);
        player.sendMessage("§d" + Messages.get("dimension.entered", "You have entered The Sift."));
        return true;
    }

    public boolean returnToOverworld(Player player) {
        World overworld = Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
        if (overworld == null) {
            player.sendMessage("§d" + Messages.get("dimension.no-overworld", "The Overworld is unavailable."));
            return false;
        }

        Location spawn = overworld.getSpawnLocation();
        int[] saved = player.getPersistentDataContainer().get(lastOverworldKey,
                PersistentDataType.INTEGER_ARRAY);
        if (saved != null && saved.length == 3) {
            // Return to the exact spot the player left; fall back to the surface if the
            // destination is now blocked (builds may have changed the landscape).
            Location spot = new Location(overworld, saved[0] + 0.5, saved[1], saved[2] + 0.5);
            Block feet = overworld.getBlockAt(saved[0], saved[1], saved[2]);
            Block head = overworld.getBlockAt(saved[0], saved[1] + 1, saved[2]);
            if (!feet.getType().isSolid() && !head.getType().isSolid()) {
                spawn = spot;
            } else {
                spawn = overworld.getHighestBlockAt(saved[0], saved[2]).getLocation().add(0.5, 1.0, 0.5);
            }
        }
        player.teleport(spawn);
        player.sendMessage("§d" + Messages.get("dimension.returned", "You have returned to the Overworld."));
        return true;
    }
}
