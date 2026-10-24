package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
        if (!isLockedDestination(event.getTo() == null ? null : event.getTo().getWorld())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("The Sift is locked because required provider IDs are unavailable. Run /sift doctor.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (!isLockedDestination(event.getPlayer().getWorld())) return;
        World from = event.getFrom();
        Location safe = from.getSpawnLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline() || !isLockedDestination(event.getPlayer().getWorld())) return;
            event.getPlayer().teleport(safe);
            event.getPlayer().sendMessage("The Sift remains locked because required provider IDs are unavailable. Run /sift doctor.");
        });
    }

    private boolean isLockedDestination(World world) {
        return world != null && world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                && readiness.strict() && !readiness.dimensionAllowed();
    }
}
