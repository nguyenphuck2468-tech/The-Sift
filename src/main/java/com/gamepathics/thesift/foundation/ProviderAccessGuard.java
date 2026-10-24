package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/** Prevents external teleport/world-management plugins from bypassing strict readiness. */
final class ProviderAccessGuard implements Listener {
    private final JavaPlugin plugin;
    private final ProviderReadiness readiness;

    ProviderAccessGuard(JavaPlugin plugin, ProviderReadiness readiness) {
        this.plugin = plugin;
        this.readiness = readiness;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !shouldBlock(event.getFrom().getWorld().getKey(),
                event.getTo().getWorld().getKey(), readiness.strict(), readiness.dimensionAllowed())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("The Sift is locked because required provider IDs are unavailable. Run /sift doctor.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Player || event.getTo() == null
                || !shouldBlock(event.getFrom().getWorld().getKey(), event.getTo().getWorld().getKey(),
                readiness.strict(), readiness.dimensionAllowed())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!isLockedDestination(event.getPlayer().getWorld())) return;
        Bukkit.getScheduler().runTask(plugin, () -> evacuate(event.getPlayer(), event.getPlayer().getWorld()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (!isLockedDestination(event.getPlayer().getWorld())) return;
        World from = event.getFrom();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline() || !isLockedDestination(event.getPlayer().getWorld())) return;
            evacuate(event.getPlayer(), from);
        });
    }

    private void evacuate(Player player, World preferredWorld) {
        World safeWorld = preferredWorld != null && !preferredWorld.getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                ? preferredWorld : Bukkit.getWorlds().stream()
                .filter(world -> !world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)).findFirst().orElse(null);
        if (safeWorld == null) {
            player.sendMessage("The Sift is locked, and no safe world is loaded. Ask an administrator to load a main world.");
            return;
        }
        Location safe = safeWorld.getSpawnLocation();
        player.teleport(safe);
        player.sendMessage("The Sift is locked because required provider IDs are unavailable. Run /sift doctor.");
    }

    static boolean shouldBlock(NamespacedKey from, NamespacedKey to, boolean strict, boolean ready) {
        return strict && !ready && to != null && to.equals(SiftDimensionManager.DIMENSION_KEY)
                && !to.equals(from);
    }

    private boolean isLockedDestination(World world) {
        return world != null && world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                && readiness.strict() && !readiness.dimensionAllowed();
    }
}
