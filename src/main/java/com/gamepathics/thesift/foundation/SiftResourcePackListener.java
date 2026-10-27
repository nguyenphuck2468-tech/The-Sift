package com.gamepathics.thesift.foundation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sends the bundled The Sift resource pack to players when they join, delegating to an
 * installed resource-pack provider plugin when one is detected (see {@link ResourcePackBridge}).
 */
public final class SiftResourcePackListener implements Listener {
    private final JavaPlugin plugin;
    private final ResourcePackBridge bridge;

    public SiftResourcePackListener(JavaPlugin plugin, ResourcePackBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
        // The resource-pack listener is registered for every enabled The Sift installation, so
        // use its lifecycle hook to start the small gameplay observers without another bootstrap path.
        plugin.getServer().getPluginManager().registerEvents(new DarkSnifferDiggingGiftListener(plugin), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        // Default off: auto-delivering at join without a working download URL (or provider)
        // previously handed clients a broken file:// pack. Admins opt in once delivery works.
        if (!plugin.getConfig().getBoolean("resource-pack.auto-send", false)) return;
        bridge.sendTo(event.getPlayer(), false);
    }
}
