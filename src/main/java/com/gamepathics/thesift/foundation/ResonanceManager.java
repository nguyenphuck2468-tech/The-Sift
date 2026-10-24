package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ResonanceManager implements Listener {
    private static final String SINGER_ID = "singer";
    private final JavaPlugin plugin;
    private final NamespacedKey entityKey;
    private final NamespacedKey itemKey;
    private final SiftMobFactory mobFactory;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private long tick;
    private int taskId = -1;

    public ResonanceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "resonance_entity");
        this.itemKey = new NamespacedKey(plugin, "content");
        this.mobFactory = new SiftMobFactory(plugin);
    }

    public void start() {
        long interval = 20L;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, interval, interval).getTaskId();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (BossBar bar : bars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        bars.clear();
    }

    public LivingEntity summonSinger(Location location) {
        LivingEntity singer = mobFactory.spawn(SINGER_ID, location, EntityType.ALLAY);
        singer.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, SINGER_ID);
        singer.setCustomName("§5§lSinger");
        singer.setCustomNameVisible(true);
        singer.setPersistent(true);
        if (singer instanceof Mob mob) mob.setCanPickupItems(false);
        var maxHealth = singer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(plugin.getConfig().getDouble("resonance.singer.max-health", 40.0));
            singer.setHealth(maxHealth.getValue());
        }
        BossBar bar = Bukkit.createBossBar("§5Singer Resonance", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bars.put(singer.getUniqueId(), bar);
        return singer;
    }

    private void pulse() {
        if (!plugin.getConfig().getBoolean("resonance.enabled", true)
                || !plugin.getConfig().getBoolean("content.singer", true)) {
            return;
        }
        tick++;
        int period = Math.max(1, plugin.getConfig().getInt("resonance.singer.pulse-seconds", 5));
        if (tick % period != 0) {
            updateBars();
            return;
        }
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        double range = Math.max(1, plugin.getConfig().getDouble("resonance.singer.range", 16.0));
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                for (Entity entity : player.getNearbyEntities(range, range, range)) {
                    if (!(entity instanceof LivingEntity singer) || !isSinger(entity) || !seen.add(entity.getUniqueId())) continue;
                    ensureBar(singer);
                    Location center = singer.getLocation();
                    world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 0.8, 0), 1);
                    world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.45f, 1.6f);
                }
            }
        }
        updateBars();
    }

    private void updateBars() {
        bars.entrySet().removeIf(entry -> {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                entry.getValue().removeAll();
                entry.getValue().setVisible(false);
                return true;
            }
            BossBar bar = entry.getValue();
            java.util.Set<Player> desired = new java.util.HashSet<>();
            for (Player player : entity.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(entity.getLocation()) <= 32 * 32) {
                    desired.add(player);
                }
            }
            for (Player player : new java.util.HashSet<>(bar.getPlayers())) {
                if (!desired.contains(player)) bar.removePlayer(player);
            }
            for (Player player : desired) if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
            var maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = maxHealth == null ? living.getHealth() : maxHealth.getValue();
            bar.setProgress(Math.max(0, Math.min(1, living.getHealth() / max)));
            return false;
        });
    }

    private void ensureBar(LivingEntity singer) {
        bars.computeIfAbsent(singer.getUniqueId(), ignored ->
                Bukkit.createBossBar("§5Singer Resonance", BarColor.PURPLE, BarStyle.SEGMENTED_10));
    }

    private boolean protectedByCharoite(Player player) {
        if (!plugin.getConfig().getBoolean("resonance.singer.charoite-protection", true)) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.hasItemMeta()
                    && "charoite".equals(stack.getItemMeta().getPersistentDataContainer()
                    .get(itemKey, PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    private boolean isSinger(Entity entity) {
        return SINGER_ID.equals(entity.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING));
    }

    @EventHandler
    public void onSingerDeath(EntityDeathEvent event) {
        if (!isSinger(event.getEntity())) {
            return;
        }
        BossBar bar = bars.remove(event.getEntity().getUniqueId());
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        if (plugin.getConfig().getBoolean("resonance.singer.drop-charoite", true)) {
            event.getDrops().add(new SiftiteContent(plugin).item(org.bukkit.Material.AMETHYST_SHARD,
                    "charoite", "Charoite"));
        }
    }

    @EventHandler
    public void onSingerTarget(EntityTargetEvent event) {
        if (isSinger(event.getEntity())) event.setCancelled(true);
    }
}
