package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Replaces the vanilla Sniffer digging gift for Dark Sniffers with the reference-mod
 * Sculkflower Seeds loot. The event hook runs only when the vanilla Sniffer actually drops
 * its digging item, avoiding a timer-based approximation and preventing duplicate seeds.
 */
final class DarkSnifferDiggingGiftListener implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey creatureKey;

    DarkSnifferDiggingGiftListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDarkSnifferDrop(EntityDropItemEvent event) {
        if (event.getEntityType() != EntityType.SNIFFER || !(event.getEntity() instanceof Sniffer sniffer)) return;
        if (!"dark_sniffer".equals(sniffer.getPersistentDataContainer()
                .get(creatureKey, PersistentDataType.STRING))) return;

        Item dropped = event.getItemDrop();
        ItemStack stack = dropped.getItemStack();
        if (stack.getType() != Material.TORCHFLOWER_SEEDS) return;

        ItemStack gift = createGift();
        if (gift == null) {
            plugin.getLogger().warning("Dark Sniffer digging loot could not create Sculkflower Seeds; keeping vanilla loot.");
            return;
        }

        dropped.setItemStack(gift);
    }

    private ItemStack createGift() {
        if (plugin instanceof TheSiftFoundationPlugin foundation) {
            ItemStack providerItem = foundation.siftiteContent().item(
                    Material.TORCHFLOWER_SEEDS, "sculkflower_seeds", "Sculkflower Seeds");
            if (providerItem != null) return providerItem;
        }

        // Strict production mode must never silently substitute a vanilla item for missing provider content.
        if (plugin.getConfig().getBoolean("providers.strict", true)) return null;

        // Development-only fallback while testing without a content provider.
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
