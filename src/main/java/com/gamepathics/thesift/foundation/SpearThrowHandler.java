package com.gamepathics.thesift.foundation;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns the Siftite Spear throw lifecycle so the durability economy is testable:
 *
 * <ul>
 *   <li>throwing costs exactly one durability point from the held spear (creative players keep
 *       their stack);</li>
 *   <li>the projectile carries the ALREADY-DAMAGED copy - cloning before the damage turned every
 *       throw into a free full-durability spear when the copy landed;</li>
 *   <li>the copy drops exactly once, whether the spear strikes an entity, a block, or simply
 *       despawns (a PDC flag guards against the double drop);</li>
 *   <li>a spear that breaks on throw drops nothing.</li>
 * </ul>
 */
final class SpearThrowHandler {
    private final NamespacedKey spearTagKey;
    private final NamespacedKey thrownTagKey;
    private final NamespacedKey droppedKey;
    private final NamespacedKey lastThrowKey;

    SpearThrowHandler(JavaPlugin plugin) {
        this.spearTagKey = new NamespacedKey(plugin, "content");
        this.thrownTagKey = new NamespacedKey(plugin, "sift_creature");
        this.droppedKey = new NamespacedKey(plugin, "spear_copy_dropped");
        this.lastThrowKey = new NamespacedKey(plugin, "spear_last_throw");
    }

    /** True when the held stack is a tagged Siftite Spear. */
    boolean isSpear(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && "siftite_spear".equals(item.getItemMeta().getPersistentDataContainer()
                        .get(spearTagKey, PersistentDataType.STRING));
    }

    /** True when this player may throw again (simple 500 ms double-fire guard). */
    boolean offCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = player.getPersistentDataContainer().get(lastThrowKey, PersistentDataType.LONG);
        if (last != null && now - last < 500L) return false;
        player.getPersistentDataContainer().set(lastThrowKey, PersistentDataType.LONG, now);
        return true;
    }

    /**
     * Spawns the thrown spear and applies the throw cost. Returns the projectile or null when the
     * throw must not happen.
     */
    Snowball throwSpear(JavaPlugin plugin, Player player, ItemStack held, EquipmentSlot hand) {
        if (!offCooldown(player)) return null;
        Location eye = player.getEyeLocation();
        // The projectile carries the durability-damaged copy of the held spear.
        ItemStack copy = held.clone();
        ItemStack damaged = copy.damage(1, player);
        boolean broke = damaged == null || damaged.getType().isAir();
        Snowball projectile = eye.getWorld().spawn(eye, Snowball.class, spawned -> {
            spawned.setItem(copy);
            spawned.setShooter(player);
            spawned.setVelocity(eye.getDirection().normalize().multiply(1.8));
            spawned.getPersistentDataContainer().set(thrownTagKey, PersistentDataType.STRING,
                    "thrown_siftite_spear");
            if (broke) spawned.getPersistentDataContainer().set(droppedKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (broke) {
            player.getInventory().setItem(hand, null);
        } else {
            player.getInventory().setItem(hand, damaged);
        }
        return projectile;
    }

    /**
     * Drops the thrown copy exactly once at the projectile's position. Returns true when a drop
     * happened. A spear that broke on throw never drops.
     */
    boolean dropThrownCopy(JavaPlugin plugin, Snowball projectile) {
        var pdc = projectile.getPersistentDataContainer();
        if (pdc.getOrDefault(droppedKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1) return false;
        pdc.set(droppedKey, PersistentDataType.BYTE, (byte) 1);
        ItemStack copy = projectile.getItem();
        if (copy == null || copy.getType().isAir()) return false;
        projectile.getWorld().dropItem(projectile.getLocation(), copy).setPickupDelay(20);
        return true;
    }

    /** Applies the impact damage and returns true when damage was dealt. */
    boolean damageTarget(JavaPlugin plugin, LivingEntity target, Player shooter) {
        target.damage(plugin.getConfig().getDouble("siftite.spear-damage", 6.0), shooter);
        return true;
    }
}
