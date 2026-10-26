package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ResonanceManager implements Listener {
    private static final String SINGER_ID = "singer";
    private final JavaPlugin plugin;
    private final NamespacedKey entityKey;
    private SiftMobFactory mobFactory;
    private SiftiteContent content;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> hornCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> barterUntil = new HashMap<>();
    /**
     * Singer soul event (mod parity): the reference mod's Singer is invulnerable outside its
     * soul event. Here a player holding a Soul Block at main hand "awakens" the Singer for
     * resonance.singer.soul-event-seconds, during which it can be damaged and killed.
     */
    private final Map<UUID, Long> soulEventUntil = new HashMap<>();
    private long tick;
    private int taskId = -1;

    public ResonanceManager(JavaPlugin plugin) {
        this(plugin, new SiftiteContent(plugin), new SiftMobFactory(plugin));
    }

    /** Shared wiring: reuse the plugin-wide content registry and mob factory instead of new instances. */
    ResonanceManager(JavaPlugin plugin, SiftiteContent content, SiftMobFactory mobFactory) {
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "resonance_entity");
        this.content = content;
        this.mobFactory = mobFactory;
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
        barterUntil.clear();
        soulEventUntil.clear();
    }

    /** True while the Singer's soul event window is open (it can be damaged during this time). */
    boolean inSoulEvent(UUID singerId) {
        Long until = soulEventUntil.get(singerId);
        return until != null && until > System.currentTimeMillis();
    }

    /**
     * Mod parity: the Singer is invulnerable outside its soul event. Holding a Soul Block at
     * main hand and right-clicking the Singer opens a resonance.singer.soul-event-seconds
     * window during which damage is allowed; without one, the hit is cancelled with feedback.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSingerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity singer) || !isSinger(singer)) return;
        if (!plugin.getConfig().getBoolean("resonance.singer.invulnerable", true)) return;
        if (inSoulEvent(singer.getUniqueId())) return;
        // Defensive re-check: the Bukkit API marks EntityDamageByEntityEvent "cancellable by
        // contract" but some implementations don't honour it, so enforce invulnerability via
        // zero damage as well as setCancelled.
        event.setCancelled(true);
        event.setDamage(0.0);
        // Feedback is best-effort cosmetic: some server implementations throw for particles or
        // action bars in special contexts; the cancellation above must never depend on them.
        try {
            singer.getWorld().spawnParticle(org.bukkit.Particle.SOUL,
                    singer.getLocation().add(0, 1.0, 0), 6, 0.3, 0.3, 0.3, 0.0);
        } catch (Throwable ignored) {
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker != null) {
            try {
                attacker.sendActionBar("\u00a75The Singer cannot be harmed outside its soul event"
                        + " \u2014 offer it a Soul Block.");
            } catch (Throwable ignored) {
            }
        }
    }

    public LivingEntity summonSinger(Location location) {
        LivingEntity singer = mobFactory.spawn(SINGER_ID, location, EntityType.ALLAY);
        if (singer == null) return null;
        singer.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, SINGER_ID);
        singer.setCustomName("§5§lSinger");
        singer.setCustomNameVisible(true);
        singer.setPersistent(true);
        singer.setInvulnerable(plugin.getConfig().getBoolean("resonance.singer.invulnerable", true));
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
        if (!plugin.getConfig().getBoolean("resonance.horn-wakes-singer", false)) return;
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

    /** Mod parity: a Singer trades a Soul Block for a reward from the mod's weighted table. */
    @EventHandler
    public void onBarter(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isSinger(event.getRightClicked())) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR || !content.is(held, "soul_block")) return;
        event.setCancelled(true);
        Entity singer = event.getRightClicked();
        long now = System.currentTimeMillis();
        Long until = barterUntil.get(singer.getUniqueId());
        if (until != null && now < until) return;
        barterUntil.put(singer.getUniqueId(), now + 3000L);
        // Soul event trigger (mod parity): offering a Soul Block also awakens the Singer for a
        // limited window during which its invulnerability is lifted. Controlled by
        // resonance.singer.soul-event-seconds (0 disables the mechanic entirely).
        int soulEventSeconds = Math.max(0,
                plugin.getConfig().getInt("resonance.singer.soul-event-seconds", 60));
        if (soulEventSeconds > 0) {
            soulEventUntil.put(singer.getUniqueId(), now + soulEventSeconds * 1000L);
            player.sendActionBar("\u00a75The Singer accepts the Soul Block \u2014 it can be harmed for "
                    + soulEventSeconds + "s.");
        }
        if (player.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
        singer.getWorld().playSound(singer.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!singer.isValid()) return;
            SingerBarter.Reward reward = SingerBarter.pick(java.util.concurrent.ThreadLocalRandom.current()
                    .nextInt(SingerBarter.TOTAL_ROLL));
            Material material = reward.material();
            if (reward.randomWool()) {
                var wools = Tag.WOOL.getValues().toArray(new Material[0]);
                material = wools[java.util.concurrent.ThreadLocalRandom.current().nextInt(wools.length)];
            }
            int amount = reward.min() + java.util.concurrent.ThreadLocalRandom.current()
                    .nextInt(reward.max() - reward.min() + 1);
            Vector direction = player.getLocation().toVector().subtract(singer.getLocation().toVector()).setY(0);
            if (direction.lengthSquared() < 0.001) direction = new Vector(1, 0, 0);
            direction.normalize();
            final Vector pushDirection = direction;
            Location drop = singer.getLocation().add(pushDirection.clone().multiply(0.6)).add(0, 1.2, 0);
            singer.getWorld().dropItem(drop, new ItemStack(material, amount), item ->
                    item.setVelocity(pushDirection.clone().multiply(0.3).setY(0.2)));
            SiftAdvancementGrant.grant(plugin, player, "pitch_perfect");
        }, 40L);
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
        // Chunk-based scan (same pattern as EchoGolemManager/RiftManager): iterate each world's
        // entities once instead of re-scanning per player, which duplicated work and could miss
        // Singers in chunks without a nearby player.
        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) continue;
            for (Player centerPlayer : players) {
                for (Entity entity : world.getNearbyEntities(centerPlayer.getLocation(), range, range, range)) {
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
        // Read the tag through the shared content registry so both Singer and Echo Golem
        // charoite checks use one code path (and honour provider-created items).
        var inventory = player.getInventory();
        return hasTaggedCharoite(inventory.getStorageContents())
                || hasTaggedCharoite(inventory.getArmorContents())
                || hasTaggedCharoite(new ItemStack[]{inventory.getItemInOffHand()});
    }

    private boolean hasTaggedCharoite(ItemStack[] items) {
        for (ItemStack stack : items) {
            if (content.is(stack, "charoite")) return true;
        }
        return false;
    }

    private void applySingerPulse(LivingEntity singer, Player player) {
        if (!plugin.getConfig().getBoolean("resonance.singer.attack-enabled", false)
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
        Player killer = event.getEntity().getKiller();
        // Parity: the reference mod's Singer is invulnerable outside its soul event. The plugin
        // lifts that invulnerability during a soul-event window (see onBarter/onSingerDamaged),
        // so singer_silenced is granted when the killer defeated it inside such a window, or
        // outright when a server opts out of invulnerability (resonance.singer.invulnerable: false).
        boolean soulEventKill = inSoulEvent(event.getEntity().getUniqueId());
        if (!plugin.getConfig().getBoolean("resonance.singer.invulnerable", true) || soulEventKill) {
            SiftAdvancementGrant.grant(plugin, killer, "singer_silenced");
        }
        soulEventUntil.remove(event.getEntity().getUniqueId());
        if (plugin.getConfig().getBoolean("resonance.singer.drop-charoite", true)) {
            ItemStack drop = content.forId("charoite");
            if (drop != null) event.getDrops().add(drop);
        }
    }

    @EventHandler
    public void onSingerTarget(EntityTargetEvent event) {
        if (isSinger(event.getEntity())) event.setCancelled(true);
    }
}
