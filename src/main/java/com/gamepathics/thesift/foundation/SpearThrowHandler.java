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

import java.util.function.BiFunction;

/**
 * Owns the Siftite Spear throw lifecycle so the durability economy is testable.
 *
 * <p>The projectile spawning is abstracted behind {@link ProjectileSpawner} so tests can
 * verify durability, creative mode and drop logic without needing real entity spawning.
 */
final class SpearThrowHandler {
    private final NamespacedKey spearTagKey;
    private final NamespacedKey thrownTagKey;
    private final NamespacedKey droppedKey;
    private final NamespacedKey lastThrowKey;
    private final JavaPlugin plugin;

    /** Creates the projectile entity; injectable for testing. */
    interface ProjectileSpawner {
        Snowball spawn(Location eye, ItemStack item, Player shooter, double speed, boolean broke);
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

    void setSpawner(ProjectileSpawner spawner) {
        this.spawner = spawner;
    }

    private Snowball defaultSpawn(Location eye, ItemStack item, Player shooter, double speed, boolean broke) {
        return eye.getWorld().spawn(eye, Snowball.class, spawned -> {
            spawned.setItem(item);
            spawned.setShooter(shooter);
            spawned.setVelocity(eye.getDirection().normalize().multiply(speed));
            spawned.getPersistentDataContainer().set(thrownTagKey, PersistentDataType.STRING,
                    "thrown_siftite_spear");
            if (broke) spawned.getPersistentDataContainer().set(droppedKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    boolean isSpear(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && "siftite_spear".equals(item.getItemMeta().getPersistentDataContainer()
                        .get(spearTagKey, PersistentDataType.STRING));
    }

    boolean offCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = player.getPersistentDataContainer().get(lastThrowKey, PersistentDataType.LONG);
        if (last != null && now - last < 500L) return false;
        player.getPersistentDataContainer().set(lastThrowKey, PersistentDataType.LONG, now);
        return true;
    }

    /**
     * Computes the throw cost: returns the damaged item for the hand (null if broke) and
     * the item for the projectile. Static and testable without entities.
     */
    static ItemStack[] computeThrowCost(ItemStack held, boolean creative) {
        if (creative) return new ItemStack[]{held.clone(), held.clone()};
        ItemStack handItem = held.clone();
        var meta = handItem.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int newDamage = damageable.getDamage() + 1;
            if (newDamage >= held.getType().getMaxDurability()) {
                return new ItemStack[]{null, null};
            }
            damageable.setDamage(newDamage);
            handItem.setItemMeta(meta);
        }
        ItemStack projectileItem = handItem.clone();
        return new ItemStack[]{handItem, projectileItem};
    }

    Snowball throwSpear(JavaPlugin plugin, Player player, ItemStack held, EquipmentSlot hand) {
        if (!offCooldown(player)) return null;
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ItemStack[] cost = computeThrowCost(held, creative);
        ItemStack handItem = cost[0];
        ItemStack projectileItem = cost[1];
        boolean broke = handItem == null;

        if (!creative) {
            player.getInventory().setItem(hand, handItem);
        }

        Location eye = player.getEyeLocation();
        double reach = Math.max(1.0, plugin.getConfig().getDouble("siftite.spear-reach", 4.5));
        double speed = Math.max(0.8, Math.min(2.0, reach / 2.5));
        return spawner.spawn(eye, projectileItem, player, speed, broke);
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
