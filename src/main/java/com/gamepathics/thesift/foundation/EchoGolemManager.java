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
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
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
    private SiftiteContent content;
    private final NamespacedKey entityKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey cargoKey;
    private SiftMobFactory mobFactory;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private long tick;

    /**
     * Shared content registry: prefers the plugin-wide instance when The Sift is running as a
     * live plugin (one ExternalItemBridge/cache); falls back to a local one in unit tests where
     * MockBukkit does not register this class as a plugin.
     */
    private SiftiteContent sharedContent() {
        if (content == null) {
            content = plugin instanceof TheSiftFoundationPlugin foundation
                    ? foundation.siftiteContent() : new SiftiteContent(plugin);
        }
        return content;
    }

    /** Shared mob factory, resolved the same way as {@link #sharedContent()}. */
    private SiftMobFactory sharedMobFactory() {
        if (mobFactory == null) {
            mobFactory = plugin instanceof TheSiftFoundationPlugin foundation
                    ? foundation.mobFactory() : new SiftMobFactory(plugin);
        }
        return mobFactory;
    }

    public EchoGolemManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.entityKey = new NamespacedKey(plugin, "echo_golem_entity");
        this.ownerKey = new NamespacedKey(plugin, "echo_golem_owner");
        this.cargoKey = new NamespacedKey(plugin, "echo_golem_soul_block");
    }

    EchoGolemManager(JavaPlugin plugin, SiftiteContent content) {
        this(plugin);
        this.content = content;
    }

    EchoGolemManager(JavaPlugin plugin, SiftiteContent content, SiftMobFactory mobFactory) {
        this(plugin, content);
        this.mobFactory = mobFactory;
    }

    SiftiteContent content() {
        return sharedContent();
    }

    SiftMobFactory mobFactory() {
        return sharedMobFactory();
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
        LivingEntity golem = sharedMobFactory().spawn(ECHO_GOLEM_ID, location, EntityType.IRON_GOLEM);
        if (golem == null) return null;
        golem.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, ECHO_GOLEM_ID);
        golem.setCustomName("§b§lEcho Golem");
        golem.setCustomNameVisible(true);
        golem.setPersistent(true);
        // Reference-mod parity: the Echo Golem is a neutral guardian. It never
        // attacks players and only bonds with one that offers it an Echo Shard.
        if (golem instanceof Mob mob) {
            mob.setCanPickupItems(false);
            mob.setTarget(null);
        }
        var maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(plugin.getConfig().getDouble("echo-golem.max-health", 34.0));
            golem.setHealth(maxHealth.getValue());
        }
        // Reference-mod statline (EchoGolemEntity.createMobAttributes): speed 0.23 and
        // knockback resistance 0.25; the plugin previously ran at 0.28 / 1.0.
        var movementSpeed = golem.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(plugin.getConfig().getDouble("echo-golem.movement-speed", 0.23));
        }
        var knockback = golem.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(plugin.getConfig().getDouble("echo-golem.knockback-resist", 0.25));
        }

        ensureBar(golem);
        
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
        // Fan-made resonance heal pulse. The reference mod's Echo Golem has no healing
        // behaviour at all (its only stat is MAX_HEALTH 34), so this mechanic is gated off
        // by default; admins can opt in with echo-golem.heal-enabled: true. Charoite kinship
        // (protectedByCharoite) only widens the radius when the pulse is enabled — it never
        // makes the heal world-wide on its own.
        if (!plugin.getConfig().getBoolean("echo-golem.heal-enabled", false)) {
            updateBars();
            return;
        }
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        double range = Math.max(1, plugin.getConfig().getDouble("echo-golem.resonance-range", 20.0));
        // Scan each world once (chunk-based) instead of per-player entity queries: cheaper and
        // avoids duplicate/missed pulses when several players stand near the same golem.
        for (World world : Bukkit.getWorlds()) {
            for (Player centerPlayer : world.getPlayers()) {
                for (Entity entity : world.getNearbyEntities(centerPlayer.getLocation(), range, range, range)) {
                    if (!(entity instanceof LivingEntity golem) || !isEchoGolem(entity)
                            || !seen.add(entity.getUniqueId())) continue;
                    Location center = entity.getLocation();
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.5, 0), 1);
                    world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.3f, 0.8f);
                    Player owner = owner(entity);
                    if (owner != null && owner.getWorld().equals(world)) {
                        double effectiveRange = protectedByCharoite(owner) ? range * 2 : range;
                        if (owner.getLocation().distanceSquared(center) <= effectiveRange * effectiveRange) {
                            double heal = plugin.getConfig().getDouble("echo-golem.heal-amount", 4.0);
                            Location ownerLocation = owner.getLocation();
                            owner.setHealth(Math.min(owner.getHealth() + heal,
                                    owner.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                                            : owner.getAttribute(Attribute.MAX_HEALTH).getValue()));
                            world.spawnParticle(Particle.HEART, ownerLocation.add(0, 1.2, 0), 2);
                        }
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

    private void ensureBar(LivingEntity golem) {
        bars.computeIfAbsent(golem.getUniqueId(), ignored ->
                Bukkit.createBossBar("§bEcho Golem", BarColor.BLUE, BarStyle.SEGMENTED_20));
    }

    @EventHandler
    public void onEntityAddedToWorld(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof LivingEntity living && isEchoGolem(living)) ensureBar(living);
    }

    int trackedBossBarCount() {
        return bars.size();
    }

    /**
     * Reference-mod parity: a player carrying Charoite is treated as kin by the Echo Golem.
     * Currently the golem never targets players at all (see {@link #onEchoGolemTarget}), so this
     * helper only gates the resonance heal pulse; keep it in sync with ResonanceManager's
     * Singer charoite check.
     */
    boolean protectedByCharoite(Player player) {
        if (!plugin.getConfig().getBoolean("echo-golem.charoite-protection", true)) {
            return false;
        }
        var inventory = player.getInventory();
        return hasTaggedCharoite(inventory.getStorageContents())
                || hasTaggedCharoite(inventory.getArmorContents())
                || hasTaggedCharoite(new ItemStack[]{inventory.getItemInOffHand()});
    }

    private boolean hasTaggedCharoite(ItemStack[] items) {
        for (ItemStack stack : items) {
            if (sharedContent().is(stack, "charoite")) return true;
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
            ItemStack drop = sharedContent().item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
            if (drop != null) event.getDrops().add(drop);
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
            Messages.send(player, "echo-golem.bonded", "The Echo Golem recognizes you as its companion.");
            event.setCancelled(true);
            return;
        }
        if (owner == null || !owner.equals(player.getUniqueId().toString())) return;
        boolean holdingSoulBlock = sharedContent().is(hand, "soul_block");
        boolean carrying = pdc.has(cargoKey, PersistentDataType.BYTE);
        if (holdingSoulBlock && !carrying) {
            consumeOne(player);
            pdc.set(cargoKey, PersistentDataType.BYTE, (byte) 1);
            Messages.send(player, "echo-golem.takes-soul", "The Echo Golem takes the Soul Block.");
            event.setCancelled(true);
        } else if (carrying && hand.getType().isAir()) {
            ItemStack soul = sharedContent().forId("soul_block");
            if (soul == null) {
                Messages.send(player, "echo-golem.no-provider",
                        "Soul Block item is unavailable from the configured provider. Run /sift doctor.");
                event.setCancelled(true);
                return;
            }
            pdc.remove(cargoKey);
            player.getInventory().addItem(soul).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            Messages.send(player, "echo-golem.returns-soul", "The Echo Golem returns its Soul Block to you.");
            // Reference-mod parity (friend_of_the_sift): the bonded golem handing the Soul
            // Block back to its player.
            SiftAdvancementGrant.grant(plugin, player, "friend_of_the_sift");
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
