package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Static helper so any manager can award a bundled thesift:* advancement. */
final class SiftAdvancementGrant {
    private SiftAdvancementGrant() {}

    static void grant(JavaPlugin plugin, Player player, String... ids) {
        if (player == null) return;
        for (String id : ids) {
            Advancement advancement = Bukkit.getAdvancement(NamespacedKey.fromString("thesift:" + id));
            if (advancement == null) {
                plugin.getLogger().fine("Advancement thesift:" + id + " is not loaded.");
                continue;
            }
            var progress = player.getAdvancementProgress(advancement);
            for (String criterion : progress.getRemainingCriteria().stream().toList()) {
                progress.awardCriteria(criterion);
            }
        }
    }
}
