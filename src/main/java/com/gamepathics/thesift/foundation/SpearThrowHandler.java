package com.gamepathics.thesift.foundation;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.LongSupplier;

/**
 * Owns the Siftite Spear throw lifecycle as a transactional operation, trident-style.
 *
 * <p>The projectile spawns FIRST; only after a successful spawn does the held item leave
 * the hand. The thrown spear itself IS the item: it lands or hits exactly once and drops
 * for pickup, so a throw never multiplies the item (a previous design kept a damaged copy
 * in the hand while the projectile dropped another one — a dupe). Creative players keep
 * their stack and their throws never drop a copy. If the spawner returns null or throws,
 * inventory and cooldown stay untouched. Cooldown uses an injectable clock.
 */
final class SpearThrowHandler {
    private final NamespacedKey spearTagKey;
    private final NamespacedKey thrownTagKey;
    private final NamespacedKey droppedKey;
    private final NamespacedKey lastThrowKey;
    private final JavaPlugin plugin;
    private LongSupplier clock = System::currentTimeMillis;

    interface ProjectileSpawner {
        Snowball spawn(Location eye, ItemStack item, Player shooter, double speed);
    }

    private ProjectileSpawner spawner;

    SpearThrowHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.spearTagKey = new NamespacedKey(plugin, "content");
        this.thrownTagKey = new NamespacedKey(plugin, "sift_creature");
        this.droppedKey = new NamespacedKey(plugin, "spear_copy_dropped");
        this.lastThrowKey = new NamespacedKey(plugin, "spear_last_throw");
        this.spawner = this::defaultSpawn;
    }

    void setSpawner(ProjectileSpawner spawner) { this.spawner = spawner; }
    void setClock(LongSupplier clock) { this.clock = clock; }

    private Snowball defaultSpawn(Location eye, ItemStack item, Player shooter, double speed) {
        return eye.getWorld().spawn(eye, Snowball.class, spawned -> {
            spawned.setItem(item);
            spawned.setShooter(shooter);
            spawned.setVelocity(eye.getDirection().normalize().multiply(speed));
            spawned.getPersistentDataContainer().set(thrownTagKey, PersistentDataType.STRING,
                    "thrown_siftite_spear");
        });
    }

    boolean isSpear(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && "siftite_spear".equals(item.getItemMeta().getPersistentDataContainer()
                        .get(spearTagKey, PersistentDataType.STRING));
    }

    Snowball throwSpear(JavaPlugin plugin, Player player, ItemStack held, EquipmentSlot hand) {
        long now = clock.getAsLong();
        Long last = player.getPersistentDataContainer().get(lastThrowKey, PersistentDataType.LONG);
        if (last != null && now - last < 500L) return null;
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        Location eye = player.getEyeLocation();
        double reach = Math.max(1.0, plugin.getConfig().getDouble("siftite.spear-reach", 4.5));
        double speed = Math.max(0.8, Math.min(2.0, reach / 2.5));
        Snowball projectile;
        try {
            projectile = spawner.spawn(eye, held.clone(), player, speed);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Spear projectile spawn failed: " + exception.getMessage());
            return null;
        }
        if (projectile == null) return null;

        if (creative) {
            // The stack stays in hand, so the throw must not yield a pickup-able copy.
            projectile.getPersistentDataContainer().set(droppedKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            // The held item IS the projectile now — clear the hand only after a successful
            // spawn, otherwise a failed throw would delete the spear.
            player.getInventory().setItem(hand, null);
        }
        player.getPersistentDataContainer().set(lastThrowKey, PersistentDataType.LONG, now);
        return projectile;
    }

    boolean dropThrownCopy(JavaPlugin plugin, Snowball projectile) {
        var pdc = projectile.getPersistentDataContainer();
        if (pdc.getOrDefault(droppedKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1) return false;
        pdc.set(droppedKey, PersistentDataType.BYTE, (byte) 1);
        ItemStack copy = projectile.getItem();
        if (copy == null || copy.getType().isAir()) return false;
        projectile.getWorld().dropItem(projectile.getLocation(), copy).setPickupDelay(20);
        return true;
    }

    boolean damageTarget(JavaPlugin plugin, LivingEntity target, Player shooter) {
        target.damage(plugin.getConfig().getDouble("siftite.spear-damage", 6.0), shooter);
        return true;
    }
}
