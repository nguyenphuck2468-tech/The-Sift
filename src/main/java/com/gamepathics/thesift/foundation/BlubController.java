package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Blub behaviour extracted from SiftEcologyManager (Phase 4 step 2): Soul-Block attraction for
 * wild Blubs, the 1-in-3 tame roll, the stay/follow toggle, follow/teleport-lead and owner
 * defence. Key names are unchanged (blub_owner, blub_stay) so existing world data keeps working.
 */
final class BlubController implements Listener {
    private static final String DIMENSION = "thesift:the_sift";
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final SiftiteContent content;
    private final SiftEcologyManager ecology;
    private final NamespacedKey creatureKey;
    private final NamespacedKey blubOwnerKey;
    private final NamespacedKey blubStayKey;

    BlubController(org.bukkit.plugin.java.JavaPlugin plugin, SiftiteContent content, SiftEcologyManager ecology) {
        this.plugin = plugin;
        this.content = content;
        this.ecology = ecology;
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
        this.blubOwnerKey = new NamespacedKey(plugin, "blub_owner");
        this.blubStayKey = new NamespacedKey(plugin, "blub_stay");
    }

    /** Called from the 5 s ecology task. */
    void tick() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        for (Entity entity : ecology.indexedMobs(sift, "blub")) {
            if (!(entity instanceof Mob blub)) continue;
            var pdc = blub.getPersistentDataContainer();
            if (!"blub".equals(pdc.get(creatureKey, PersistentDataType.STRING))) continue;
            String ownerId = pdc.get(blubOwnerKey, PersistentDataType.STRING);
            if (pdc.has(blubStayKey, PersistentDataType.BYTE)) continue; // staying put
            if (ownerId == null) {
                // Reference-mod parity (Blub): wild Blubs are drawn to a nearby player holding
                // a Soul Block - the same item that tames them.
                if (blub.getTarget() == null) {
                    for (Entity holder : blub.getNearbyEntities(10, 6, 10)) {
                        if (!(holder instanceof Player nearby)
                                || nearby.getGameMode() == GameMode.SPECTATOR) continue;
                        if (content.is(nearby.getInventory().getItemInMainHand(), "soul_block")
                                || content.is(nearby.getInventory().getItemInOffHand(), "soul_block")) {
                            blub.getPathfinder().moveTo(nearby.getLocation());
                            break;
                        }
                    }
                }
                continue;
            }
            Player owner;
            try {
                owner = Bukkit.getPlayer(UUID.fromString(ownerId));
            } catch (IllegalArgumentException ignored) {
                return;
            }
            if (owner == null || !owner.isOnline() || owner.getWorld() != sift) continue;
            // Defend: target the most recent hostile attacker of the owner within range,
            // otherwise follow (teleport-lead like the mod's seated/following behaviour).
            boolean defending = false;
            if (blub.getTarget() == null) {
                for (Entity holder : sift.getNearbyEntities(owner.getLocation(), 8, 4, 8)) {
                    if (!(holder instanceof Mob hostile)) continue;
                    if (hostile.getTarget() == owner && hostile.getLocation()
                            .distanceSquared(blub.getLocation()) < 64) {
                        blub.setTarget(hostile);
                        defending = true;
                        break;
                    }
                }
            } else if (blub.getTarget() instanceof Mob hostile && hostile.getTarget() == owner) {
                defending = true;
            }
            double distance = blub.getLocation().distanceSquared(owner.getLocation());
            if (!defending && distance > 25) {
                if (distance > 900) {
                    Location anchor = owner.getLocation().add(0, 0.2, 0);
                    if (blub.teleport(anchor)) {
                        sift.spawnParticle(Particle.POOF, anchor, 4, 0.2, 0.2, 0.2, 0);
                    }
                } else {
                    blub.getPathfinder().moveTo(owner.getLocation());
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlubInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity target = event.getRightClicked();
        if (!"blub".equals(target.getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING))) {
            return;
        }
        Player player = event.getPlayer();
        var pdc = target.getPersistentDataContainer();
        String ownerId = pdc.get(blubOwnerKey, PersistentDataType.STRING);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (ownerId == null && content.is(hand, "soul_block")) {
            if (!plugin.getConfig().getBoolean("content.blub", true)) return;
            // Reference-mod parity: the Soul Block is always consumed and tames the Blub on
            // a 1-in-3 roll (mobs.blub.tame-chance), not on every attempt.
            consumeOne(player, hand);
            event.setCancelled(true);
            if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("mobs.blub.tame-chance", 1.0 / 3.0)) {
                Messages.send(player, "blub.failed", "The Blub sniffs the Soul Block and wanders off. Try again.");
                return;
            }
            pdc.set(blubOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            if (target instanceof org.bukkit.entity.LivingEntity living) living.setRemoveWhenFarAway(false);
            Messages.send(player, "blub.tamed", "The Blub accepts your Soul Block and follows you.");
            SiftAdvancementGrant.grant(plugin, player, "blub");
        } else if (ownerId != null && ownerId.equals(player.getUniqueId().toString())) {
            event.setCancelled(true);
            // Reference-mod parity (Blub): right-clicking your Blub with an empty hand
            // toggles stay/follow (a Rabbit cannot play the mod's sit animation, so staying
            // freezes the mob in place with its AI paused).
            if (hand.getType().isAir()) {
                boolean staying = pdc.has(blubStayKey, PersistentDataType.BYTE);
                if (staying) {
                    pdc.remove(blubStayKey);
                    if (target instanceof Mob mob) mob.setAI(true);
                    Messages.send(player, "blub.follow", "The Blub trots after you again.");
                } else {
                    pdc.set(blubStayKey, PersistentDataType.BYTE, (byte) 1);
                    if (target instanceof Mob mob) {
                        mob.setAI(false);
                        mob.getPathfinder().stopPathfinding();
                    }
                    Messages.send(player, "blub.stay", "The Blub settles down and stays.");
                }
                return;
            }
            // Already yours: a small heart response, mirroring the mod's bond feedback.
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 0.6, 0), 3);
        }
    }

    private void consumeOne(Player player, ItemStack hand) {
        // Vanilla behaviour: creative players keep their stack.
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }
}
