package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

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
    private final Map<UUID, Long> hornCooldownUntil = new HashMap<>();
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
        hornCooldownUntil.clear();
    }

    public LivingEntity summonSinger(Location location) {
        LivingEntity singer = mobFactory.spawn(SINGER_ID, location, EntityType.ALLAY);
        if (singer == null) return null;
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

    /** Mod parity: wake the Singer with a Goat Horn inside an Ancient City (Deep Dark biome). */
    @EventHandler
    public void onGoatHorn(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("resonance.horn-wakes-singer", true)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOAT_HORN) return;
        Player player = event.getPlayer();
        Location location = player.getLocation();
        if (location.getBlock().getBiome() != Biome.DEEP_DARK) return;
        long now = System.currentTimeMillis();
        Long until = hornCooldownUntil.get(player.getUniqueId());
        if (until != null && now < until) return;
        double radius = Math.max(4.0, plugin.getConfig().getDouble("resonance.horn-singer-check-radius", 48.0));
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (isSinger(nearby)) return;
        }
        Vector direction = location.getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) direction = new Vector(1, 0, 0);
        Location spawn = location.clone().add(direction.normalize().multiply(4)).add(0, 1, 0);
        if (summonSinger(spawn) == null) {
            player.sendActionBar("\u00a7cThe horn echoes, but nothing answers. Run /sift doctor.");
            return;
        }
        long cooldownMillis = Math.max(0L, plugin.getConfig().getLong("resonance.horn-cooldown-seconds", 600L)) * 1000L;
        hornCooldownUntil.put(player.getUniqueId(), now + cooldownMillis);
        player.getWorld().playSound(spawn, Sound.ENTITY_WARDEN_LISTENING, 1.0f, 0.8f);
        player.sendActionBar("\u00a75The horn wakes a Singer...");
    }

    @EventHandler
    public void onHornPlayerQuit(PlayerQuitEvent event) {
        hornCooldownUntil.remove(event.getPlayer().getUniqueId());
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
                    for (Entity nearby : singer.getNearbyEntities(range, range, range)) {
                        if (nearby instanceof Player target) applySingerPulse(singer, target);
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

    @EventHandler
    public void onEntityAddedToWorld(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof LivingEntity living && isSinger(living)) ensureBar(living);
    }

    int trackedBossBarCount() {
        return bars.size();
    }

    private boolean protectedByCharoite(Player player) {
        if (!plugin.getConfig().getBoolean("resonance.singer.charoite-protection", true)) {
            return false;
        }
        var inventory = player.getInventory();
        return hasTaggedCharoite(inventory.getStorageContents())
                || hasTaggedCharoite(inventory.getArmorContents())
                || hasTaggedCharoite(new ItemStack[]{inventory.getItemInOffHand()});
    }

    private boolean hasTaggedCharoite(ItemStack[] items) {
        for (ItemStack stack : items) {
            if (stack != null && stack.hasItemMeta()
                    && "charoite".equals(stack.getItemMeta().getPersistentDataContainer()
                    .get(itemKey, PersistentDataType.STRING))) return true;
        }
        return false;
    }

    private void applySingerPulse(LivingEntity singer, Player player) {
        if (!plugin.getConfig().getBoolean("resonance.singer.attack-enabled", true)
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || protectedByCharoite(player)) return;

        double damage = Math.max(0.0, plugin.getConfig().getDouble("resonance.singer.damage", 1.0));
        if (damage > 0.0) player.damage(damage, singer);

        int durationSeconds = Math.max(0,
                plugin.getConfig().getInt("resonance.singer.slowness-seconds", 3));
        if (durationSeconds > 0) {
            int amplifier = Math.max(0,
                    plugin.getConfig().getInt("resonance.singer.slowness-amplifier", 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    durationSeconds * 20, amplifier, true, true, true));
        }
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
        SiftAdvancementGrant.grant(plugin, event.getEntity().getKiller(), "singer_silenced");
        if (plugin.getConfig().getBoolean("resonance.singer.drop-charoite", true)) {
            ItemStack drop = new SiftiteContent(plugin).item(org.bukkit.Material.AMETHYST_SHARD,
                    "charoite", "Charoite");
            if (drop != null) event.getDrops().add(drop);
        }
    }

    @EventHandler
    public void onSingerTarget(EntityTargetEvent event) {
        if (isSinger(event.getEntity())) event.setCancelled(true);
    }
}
