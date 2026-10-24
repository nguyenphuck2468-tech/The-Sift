package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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

public final class EchoGolemManager implements Listener {
    private static final String ECHO_GOLEM_ID = "echo_golem";
    private final JavaPlugin plugin;
    private final NamespacedKey entityKey;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private long tick;

    public EchoGolemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "echo_golem_entity");
    }

    public void start() {
        long interval = 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, interval, interval);
    }

    public void shutdown() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        bars.clear();
    }

    public IronGolem summonEchoGolem(Location location) {
        IronGolem golem = (IronGolem) location.getWorld().spawnEntity(location, EntityType.IRON_GOLEM);
        golem.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, ECHO_GOLEM_ID);
        golem.setCustomName("§b§lEcho Golem");
        golem.setCustomNameVisible(true);
        golem.setPersistent(true);
        golem.setCanPickupItems(false);
        golem.getAttribute(Attribute.MAX_HEALTH).setBaseValue(
                plugin.getConfig().getDouble("echo-golem.max-health", 80.0));
        golem.setHealth(golem.getAttribute(Attribute.MAX_HEALTH).getValue());
        golem.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(
                plugin.getConfig().getDouble("echo-golem.attack-damage", 12.0));
        golem.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
        golem.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);

        BossBar bar = Bukkit.createBossBar("§bEcho Golem", BarColor.BLUE, BarStyle.SEGMENTED_20);
        bars.put(golem.getUniqueId(), bar);
        
        // Resonance particles on spawn
        location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
        
        return golem;
    }

    private void pulse() {
        if (!plugin.getConfig().getBoolean("echo-golem.enabled", true)
                || !plugin.getConfig().getBoolean("content.echo_golem", true)) {
            return;
        }
        tick++;
        int period = Math.max(1, plugin.getConfig().getInt("echo-golem.resonance-period", 8));
        if (tick % period != 0) {
            updateBars();
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(IronGolem.class)) {
                if (!isEchoGolem(entity)) {
                    continue;
                }
                Location center = entity.getLocation();
                double range = plugin.getConfig().getDouble("echo-golem.resonance-range", 20.0);
                
                // Resonance pulse effect
                world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.5, 0), 1);
                world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.8f);
                
                // Damage and slow players without Charoite
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(center) > range * range
                            || protectedByCharoite(player)) {
                        continue;
                    }
                    player.damage(plugin.getConfig().getDouble("echo-golem.resonance-damage", 6.0), entity);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            plugin.getConfig().getInt("echo-golem.slowness-seconds", 4) * 20,
                            plugin.getConfig().getInt("echo-golem.slowness-amplifier", 1)));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                            plugin.getConfig().getInt("echo-golem.mining-fatigue-seconds", 6) * 20,
                            plugin.getConfig().getInt("echo-golem.mining-fatigue-amplifier", 2)));
                }
                
                // Heal nearby Echo Golems
                for (Entity nearby : world.getNearbyEntities(center, range, range, range)) {
                    if (nearby instanceof IronGolem other && isEchoGolem(other) && other != entity) {
                        other.heal(plugin.getConfig().getDouble("echo-golem.heal-amount", 4.0));
                        other.getWorld().spawnParticle(Particle.HEART, other.getLocation().add(0, 1.5, 0), 3);
                    }
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
                if (player.getLocation().distanceSquared(entity.getLocation()) <= 40 * 40) {
                    bar.addPlayer(player);
                }
            }
            double max = living.getAttribute(Attribute.MAX_HEALTH).getValue();
            bar.setProgress(Math.max(0, Math.min(1, living.getHealth() / max)));
            return false;
        });
    }

    private boolean protectedByCharoite(Player player) {
        if (!plugin.getConfig().getBoolean("echo-golem.charoite-protection", true)) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.hasItemMeta()
                    && "charoite".equals(stack.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "content"), PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    private boolean isEchoGolem(Entity entity) {
        return ECHO_GOLEM_ID.equals(entity.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING));
    }

    @EventHandler
    public void onEchoGolemDeath(EntityDeathEvent event) {
        if (!isEchoGolem(event.getEntity())) {
            return;
        }
        BossBar bar = bars.remove(event.getEntity().getUniqueId());
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        if (plugin.getConfig().getBoolean("echo-golem.drop-siftite", true)) {
            // Drop Siftite Ingot
            SiftiteContent content = new SiftiteContent(plugin);
            event.getDrops().add(content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot"));
        }
    }

    @EventHandler
    public void onEchoGolemTarget(EntityTargetEvent event) {
        if (isEchoGolem(event.getEntity()) && event.getTarget() != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }
}
