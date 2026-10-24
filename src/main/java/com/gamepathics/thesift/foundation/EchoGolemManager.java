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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
    private final NamespacedKey ownerKey;
    private final NamespacedKey contentKey;
    private final NamespacedKey cargoKey;
    private final SiftMobFactory mobFactory;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private long tick;

    public EchoGolemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "echo_golem_entity");
        this.ownerKey = new NamespacedKey(plugin, "echo_golem_owner");
        this.contentKey = new NamespacedKey(plugin, "content");
        this.cargoKey = new NamespacedKey(plugin, "echo_golem_soul_block");
        this.mobFactory = new SiftMobFactory(plugin);
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

    public LivingEntity summonEchoGolem(Location location) {
        LivingEntity golem = mobFactory.spawn(ECHO_GOLEM_ID, location, EntityType.IRON_GOLEM);
        golem.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, ECHO_GOLEM_ID);
        golem.setCustomName("§b§lEcho Golem");
        golem.setCustomNameVisible(true);
        golem.setPersistent(true);
        if (golem instanceof Mob mob) mob.setCanPickupItems(false);
        var maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(plugin.getConfig().getDouble("echo-golem.max-health", 80.0));
            golem.setHealth(maxHealth.getValue());
        }
        var attackDamage = golem.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) attackDamage.setBaseValue(
                plugin.getConfig().getDouble("echo-golem.attack-damage", 12.0));
        var movementSpeed = golem.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null) movementSpeed.setBaseValue(0.28);
        var knockback = golem.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(1.0);

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
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        double range = Math.max(1, plugin.getConfig().getDouble("echo-golem.resonance-range", 20.0));
        for (World world : Bukkit.getWorlds()) {
            for (Player nearbyPlayer : world.getPlayers()) {
                for (Entity entity : nearbyPlayer.getNearbyEntities(range, range, range)) {
                    if (!(entity instanceof LivingEntity golem) || !isEchoGolem(entity)
                            || !seen.add(entity.getUniqueId())) continue;
                    Location center = entity.getLocation();
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.5, 0), 1);
                    world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.3f, 0.8f);
                    Player owner = owner(entity);
                    if (owner != null && owner.getWorld().equals(world)
                            && owner.getLocation().distanceSquared(center) <= range * range) {
                        double heal = plugin.getConfig().getDouble("echo-golem.heal-amount", 4.0);
                        owner.setHealth(Math.min(owner.getHealth() + heal,
                                owner.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                                        : owner.getAttribute(Attribute.MAX_HEALTH).getValue()));
                        world.spawnParticle(Particle.HEART, owner.getLocation().add(0, 1.2, 0), 2);
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
            var maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealth == null ? living.getHealth() : maxHealth.getValue();
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
        if (isEchoGolem(event.getEntity()) && event.getTarget() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEchoGolemInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || !isEchoGolem(event.getRightClicked())) return;
        Player player = event.getPlayer();
        Entity golem = event.getRightClicked();
        var pdc = golem.getPersistentDataContainer();
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.ECHO_SHARD && (owner == null || owner.equals(player.getUniqueId().toString()))) {
            if (hand.getAmount() == 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            else hand.setAmount(hand.getAmount() - 1);
            pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            player.sendMessage("§bThe Echo Golem recognizes you as its companion.");
            event.setCancelled(true);
            return;
        }
        if (owner == null || !owner.equals(player.getUniqueId().toString())) return;
        String heldId = hand.hasItemMeta() ? hand.getItemMeta().getPersistentDataContainer()
                .get(contentKey, PersistentDataType.STRING) : null;
        boolean carrying = pdc.has(cargoKey, PersistentDataType.BYTE);
        if ("soul_block".equals(heldId) && !carrying) {
            consumeOne(player);
            pdc.set(cargoKey, PersistentDataType.BYTE, (byte) 1);
            player.sendMessage("§bThe Echo Golem takes the Soul Block.");
            event.setCancelled(true);
        } else if (carrying && hand.getType().isAir()) {
            pdc.remove(cargoKey);
            ItemStack soul = new SiftiteContent(plugin).forId("soul_block");
            player.getInventory().addItem(soul).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.sendMessage("§bThe Echo Golem returns its Soul Block to you.");
            event.setCancelled(true);
        }
    }

    private Player owner(Entity golem) {
        String value = golem.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void consumeOne(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else hand.setAmount(hand.getAmount() - 1);
    }
}
