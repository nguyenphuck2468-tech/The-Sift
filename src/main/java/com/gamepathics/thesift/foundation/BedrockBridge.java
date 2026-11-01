package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Bedrock (Geyser/Floodgate) support, parity-matrix level 2 "operational" tier.
 *
 * <p>Level 1 was detection only. This bridge adds the runtime guards a mixed Java/Bedrock
 * server actually needs, without a Bedrock resource pack (the full pack/mapping tier stays
 * documented as future work in docs/parity-matrix.md):
 *
 * <ul>
 *   <li>Bedrock players are detected through Geyser's API (reflectively, so the plugin
 *       runs unchanged on servers without Geyser).</li>
 *   <li>The Java-only resource pack is never sent to Bedrock players — they cannot use it
 *       and the transfer only wastes bandwidth.</li>
 *   <li>Pack-bundled sounds ({@code thesift:...} OGGs) do not exist on Bedrock clients, so
 *       gameplay sounds fall back to their vanilla equivalents per player.</li>
 * </ul>
 */
final class BedrockBridge {
    private final JavaPlugin plugin;
    private Boolean geyserApiAvailable;
    private Object geyserApi;

    BedrockBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** True when Geyser is installed and its API is reachable. */
    synchronized boolean isAvailable() {
        if (geyserApiAvailable != null) return geyserApiAvailable;
        geyserApiAvailable = false;
        if (plugin.getConfig().getBoolean("providers.bedrock-enabled", false)
                && plugin.getServer().getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = apiClass.getMethod("api").invoke(null);
                geyserApiAvailable = geyserApi != null;
            } catch (LinkageError | ReflectiveOperationException exception) {
                plugin.getLogger().log(Level.INFO,
                        "Geyser detected but its API is unavailable; Bedrock players are treated as Java players.",
                        exception);
            }
        }
        return geyserApiAvailable;
    }

    /** True when the player sits on Bedrock through Geyser; false when unknown or Java. */
    boolean isBedrockPlayer(UUID playerId) {
        if (!isAvailable() || geyserApi == null) return false;
        try {
            Object result = geyserApi.getClass().getMethod("isBedrockPlayer", UUID.class)
                    .invoke(geyserApi, playerId);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    long bedrockPlayersOnline() {
        return Bukkit.getOnlinePlayers().stream().filter(player -> isBedrockPlayer(player.getUniqueId())).count();
    }

    /**
     * Plays a pack-bundled sound for Java clients and its vanilla stand-in for Bedrock
     * clients (whose client cannot resolve custom sound events without a Bedrock pack).
     */
    void playSound(Player player, Location location, String siftSound, Sound vanillaFallback,
                   float volume, float pitch) {
        if (player == null || location == null) return;
        if (isBedrockPlayer(player.getUniqueId())) {
            if (vanillaFallback != null) player.playSound(location, vanillaFallback, volume, pitch);
            return;
        }
        player.playSound(location, siftSound, volume, pitch);
    }
}
