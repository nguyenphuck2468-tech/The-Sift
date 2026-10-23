package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RiftManager implements Listener {
    public enum RiftState {
        DORMANT,
        OPENING,
        ACTIVE,
        CLOSING
    }

    private static final String RIFT_KEY_ID = "rift_key";
    private static final String RIFT_BLOCK_ID = "rift_block";

    private final JavaPlugin plugin;
    private final NamespacedKey riftKey;
    private final NamespacedKey blockKey;
    private final Map<UUID, RiftInstance> rifts = new HashMap<>();

    public RiftManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.riftKey = new NamespacedKey(plugin, "rift_key");
        this.blockKey = new NamespacedKey(plugin, "rift_block");
    }

    public void start() {
        // Periodic rift state updates
        new BukkitRunnable() {
            @Override
            public void run() {
                updateRifts();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    public void shutdown() {
        for (RiftInstance rift : rifts.values()) {
            rift.forceClose();
        }
        rifts.clear();
    }

    public ItemStack createRiftKey() {
        ItemStack key = new ItemStack(Material.TRIAL_KEY);
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName("§5§lRift Key");
        meta.setCustomModelData(1);
        meta.getPersistentDataContainer().set(riftKey, PersistentDataType.STRING, RIFT_KEY_ID);
        key.setItemMeta(meta);
        return key;
    }

    public boolean isRiftKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return RIFT_KEY_ID.equals(item.getItemMeta().getPersistentDataContainer()
                .get(riftKey, PersistentDataType.STRING));
    }

    public void openRift(Location location, Player opener) {
        if (!plugin.getConfig().getBoolean("rift.enabled", true)
                || !plugin.getConfig().getBoolean("content.rift", true)) {
            return;
        }
        UUID id = UUID.randomUUID();
        RiftInstance rift = new RiftInstance(id, location, opener);
        rifts.put(id, rift);
        rift.beginOpening();
    }

    private void updateRifts() {
        if (!plugin.getConfig().getBoolean("rift.enabled", true)) return;

        rifts.entrySet().removeIf(entry -> {
            RiftInstance rift = entry.getValue();
            rift.tick();
            if (rift.state == RiftState.DORMANT) {
                return true; // Remove dormant rifts
            }
            return false;
        });
    }

    @EventHandler
    public void onRiftKeyUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isRiftKey(item)) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEDROCK) return; // Rift opens on bedrock

        event.setCancelled(true);
        event.getPlayer().getInventory().removeItem(item);
        openRift(block.getLocation().add(0, 1, 0), event.getPlayer());
    }

    @EventHandler
    public void onRiftBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!block.hasMetadata("rift_block")) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cThe rift resists your attempt to break it.");
    }

    public class RiftInstance {
        private final UUID id;
        private final Location center;
        private final Player opener;
        private RiftState state = RiftState.DORMANT;
        private int stateTimer = 0;
        private int phase = 0;

        public RiftInstance(UUID id, Location center, Player opener) {
            this.id = id;
            this.center = center;
            this.opener = opener;
        }

        public void beginOpening() {
            state = RiftState.OPENING;
            stateTimer = 0;
            phase = 0;
            center.getWorld().playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.5f);
            center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
            opener.sendMessage("§5The rift begins to tear open...");
            
            // Schedule opening progression
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (state != RiftState.OPENING) return;
                    stateTimer++;
                    if (stateTimer >= 100) { // 5 seconds
                        becomeActive();
                    } else if (stateTimer % 20 == 0) {
                        // Growing particles
                        center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 1, 0), 
                            20 + stateTimer / 5, 1, 1, 1, 0.05);
                        center.getWorld().playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 0.8f + stateTimer * 0.01f);
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        private void becomeActive() {
            state = RiftState.ACTIVE;
            stateTimer = 0;
            phase = 1;
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.7f);
            center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 5);
            center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(0, 1, 0), 100, 3, 3, 3, 0.2);
            opener.sendMessage("§5§lTHE RIFT HAS OPENED! §r§5Something stirs within...");

            // Spawn initial mobs
            spawnRiftMobs(3);

            // Active phase timer
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (state != RiftState.ACTIVE) return;
                    stateTimer++;
                    if (stateTimer >= 600) { // 30 seconds active
                        beginClosing();
                    } else if (stateTimer % 60 == 0) {
                        // Periodic mob spawns
                        spawnRiftMobs(1);
                        // Resonance pulse
                        pulseResonance();
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }

        private void pulseResonance() {
            center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.6f);
            center.getWorld().spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 1);
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) <= 400) { // 20 blocks
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                }
            }
        }

        private void spawnRiftMobs(int count) {
            for (int i = 0; i < count; i++) {
                Location spawnLoc = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-5, 5),
                    0,
                    ThreadLocalRandom.current().nextDouble(-5, 5)
                );
                spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0, 1, 0);
                
                // Spawn Enderman-like or custom mob
                Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ENDERMAN);
                entity.getPersistentDataContainer().set(blockKey, PersistentDataType.STRING, RIFT_BLOCK_ID);
                entity.setCustomName("§5Rift Walker");
                entity.setCustomNameVisible(true);
            }
        }

        private void beginClosing() {
            state = RiftState.CLOSING;
            stateTimer = 0;
            center.getWorld().playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.8f);
            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 1, 0), 100, 3, 3, 3, 0.2);
            opener.sendMessage("§5The rift begins to seal...");

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (state != RiftState.CLOSING) return;
                    stateTimer++;
                    if (stateTimer >= 100) { // 5 seconds
                        close();
                    } else if (stateTimer % 20 == 0) {
                        center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0, 1, 0), 
                            20, 2, 2, 2, 0.05);
                        center.getWorld().playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.2f - stateTimer * 0.01f);
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        private void close() {
            state = RiftState.DORMANT;
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 0.5f);
            center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 3);
            center.getWorld().spawnParticle(Particle.SMOKE, center.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
            opener.sendMessage("§5The rift has sealed. §7Rewards await the brave...");

            // Drop rewards
            dropRewards();
        }

        private void dropRewards() {
            // Drop Siftite, Charoite, etc.
            for (int i = 0; i < 3; i++) {
                Location dropLoc = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-2, 2),
                    1,
                    ThreadLocalRandom.current().nextDouble(-2, 2)
                );
                SiftiteContent content = new SiftiteContent(plugin);
                dropLoc.getWorld().dropItemNaturally(dropLoc, 
                    content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot"));
            }
            // Rift Key fragment chance
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                center.getWorld().dropItemNaturally(center.clone().add(0, 1, 0), createRiftKey());
            }
        }

        public void forceClose() {
            if (state != RiftState.DORMANT) {
                close();
            }
        }

        public void tick() {
            // Passive ambient effects during active phase
            if (state == RiftState.ACTIVE && ThreadLocalRandom.current().nextDouble() < 0.1) {
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 1, 0), 5, 1, 1, 1, 0.02);
            }
        }
    }
}