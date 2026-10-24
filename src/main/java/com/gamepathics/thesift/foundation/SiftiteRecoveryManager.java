package com.gamepathics.thesift.foundation;

import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Vanilla-backed Mini Rift recovery for tagged Siftite equipment. */
public final class SiftiteRecoveryManager implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey contentKey;
    private final Map<UUID, List<ItemStack>> recovered = new HashMap<>();

    public SiftiteRecoveryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.contentKey = new NamespacedKey(plugin, "content");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("siftite.mini-rift-recovery", true)) return;
        List<ItemStack> items = new ArrayList<>();
        event.getDrops().removeIf(item -> {
            String id = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer()
                    .get(contentKey, PersistentDataType.STRING) : null;
            if (id != null && List.of("siftite_sword", "siftite_axe", "siftite_pickaxe",
                    "siftite_shovel", "siftite_hoe", "siftite_spear", "siftite_helmet",
                    "siftite_chestplate", "siftite_leggings", "siftite_boots").contains(id)) {
                items.add(item.clone());
                return true;
            }
            return false;
        });
        if (!items.isEmpty()) recovered.put(event.getEntity().getUniqueId(), items);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> items = recovered.remove(event.getPlayer().getUniqueId());
        if (items == null || items.isEmpty()) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0),
                    55, 0.8, 1, 0.8, 0.06);
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 0.7f);
            for (ItemStack item : items) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            player.sendMessage("§5A Mini Rift returned your Siftite equipment.");
        });
    }
}
