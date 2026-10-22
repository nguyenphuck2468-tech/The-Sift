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
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ResonanceManager implements Listener {
    private static final String SINGER_ID = "singer";
    private final JavaPlugin plugin;
    private final NamespacedKey entityKey;
    private final NamespacedKey itemKey;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private long tick;

    public ResonanceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "resonance_entity");
        this.itemKey = new NamespacedKey(plugin, "content");
    }

    public void start() {
        long interval = 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, interval, interval);
    }

    public Allay summonSinger(Location location) {
        Allay singer = (Allay) location.getWorld().spawnEntity(location, EntityType.ALLAY);
        singer.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, SINGER_ID);
        singer.setCustomName("§5§lSinger");
        singer.setCustomNameVisible(true);
        singer.setPersistent(true);
        singer.setCanPickupItems(false);
        singer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(
                plugin.getConfig().getDouble("resonance.singer.max-health", 40.0));
        singer.setHealth(singer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
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
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Allay.class)) {
                if (!isSinger(entity)) {
                    continue;
                }
                Location center = entity.getLocation();
                double range = plugin.getConfig().getDouble("resonance.singer.range", 16.0);
                world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 0.8, 0), 1);
                world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.45f, 1.6f);
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(center) > range * range
                            || protectedByCharoite(player)) {
                        continue;
                    }
                    player.damage(plugin.getConfig().getDouble("resonance.singer.damage", 4.0), entity);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            plugin.getConfig().getInt("resonance.singer.slowness-seconds", 3) * 20,
                            plugin.getConfig().getInt("resonance.singer.slowness-amplifier", 0)));
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
            bar.removeAll();
            for (Player player : entity.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(entity.getLocation()) <= 32 * 32) {
                    bar.addPlayer(player);
                }
            }
            double max = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            bar.setProgress(Math.max(0, Math.min(1, living.getHealth() / max)));
            return false;
        });
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
        if (isSinger(event.getEntity()) && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }
}
