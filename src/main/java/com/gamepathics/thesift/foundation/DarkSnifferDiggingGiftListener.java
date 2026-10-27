package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Sniffer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Gives Dark Sniffers their reference-mod digging gift without changing vanilla Sniffer loot.
 * The Paper port uses a vanilla Sniffer as the entity stand-in, so the gift is detected from
 * its persisted The Sift creature tag and the DIGGING -> non-DIGGING state transition.
 */
final class DarkSnifferDiggingGiftListener {
    private static final String DIMENSION = "thesift:the_sift";
    private static final long MINIMUM_DIG_MILLIS = 3_000L;

    private final JavaPlugin plugin;
    private final NamespacedKey creatureKey;
    private final NamespacedKey diggingStartedKey;
    private final NamespacedKey diggingGiftedKey;
    private final BukkitTask task;

    DarkSnifferDiggingGiftListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
        this.diggingStartedKey = new NamespacedKey(plugin, "dark_sniffer_dig_started");
        this.diggingGiftedKey = new NamespacedKey(plugin, "dark_sniffer_dig_gifted");
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        long now = System.currentTimeMillis();

        for (Entity entity : sift.getEntities()) {
            if (entity.getType() != EntityType.SNIFFER || !(entity instanceof Sniffer sniffer)) continue;
            var pdc = entity.getPersistentDataContainer();
            if (!"dark_sniffer".equals(pdc.get(creatureKey, PersistentDataType.STRING))) continue;

            if (sniffer.getState() == Sniffer.State.DIGGING) {
                if (!pdc.has(diggingStartedKey, PersistentDataType.LONG)) {
                    pdc.set(diggingStartedKey, PersistentDataType.LONG, now);
                    pdc.remove(diggingGiftedKey);
                }
                continue;
            }

            Long started = pdc.get(diggingStartedKey, PersistentDataType.LONG);
            if (started == null) continue;
            if (pdc.has(diggingGiftedKey, PersistentDataType.BYTE)) {
                pdc.remove(diggingStartedKey);
                continue;
            }
            if (now - started < MINIMUM_DIG_MILLIS) continue;

            ItemStack gift = createGift();
            if (gift == null) {
                plugin.getLogger().warning("Dark Sniffer finished digging but Sculkflower Seeds could not be created.");
                pdc.remove(diggingStartedKey);
                continue;
            }

            Location dropAt = sniffer.getLocation().clone().add(0, 0.35, 0);
            Item dropped = sift.dropItem(dropAt, gift);
            dropped.setPickupDelay(10);
            pdc.set(diggingGiftedKey, PersistentDataType.BYTE, (byte) 1);
            pdc.remove(diggingStartedKey);
        }
    }

    private ItemStack createGift() {
        if (plugin instanceof TheSiftFoundationPlugin foundation) {
            ItemStack providerItem = foundation.siftiteContent().item(
                    Material.TORCHFLOWER_SEEDS, "sculkflower_seeds", "Sculkflower Seeds");
            if (providerItem != null) return providerItem;
        }

        // Development fallback: keep the gameplay functional even before a provider definition
        // for sculkflower_seeds exists. The stack is still tagged as The Sift content and uses
        // the bundled custom item model; provider-first production installs can replace it later.
        ItemStack fallback = new ItemStack(Material.TORCHFLOWER_SEEDS);
        ItemMeta meta = fallback.getItemMeta();
        meta.setDisplayName("§5Sculkflower Seeds");
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "content"), PersistentDataType.STRING, "sculkflower_seeds");
        meta.setItemModel(NamespacedKey.fromString("thesift:sculkflower_seeds"));
        fallback.setItemMeta(meta);
        return fallback;
    }
}
