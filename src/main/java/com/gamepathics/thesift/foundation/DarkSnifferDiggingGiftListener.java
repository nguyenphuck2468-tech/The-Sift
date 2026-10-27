package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Replaces the vanilla Sniffer seed drop with the reference mod's Sculkflower Seeds
 * for tagged Dark Sniffers. Intercepting the actual drop avoids guessing whether a
 * DIGGING animation completed successfully and prevents duplicate rewards.
 */
final class DarkSnifferDiggingGiftListener implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey creatureKey;
    private final NamespacedKey snifferCorruptionStartKey;

    DarkSnifferDiggingGiftListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
        this.snifferCorruptionStartKey = new NamespacedKey(plugin, "sniffer_corruption_start");
    }

    /** Dark Sniffers cannot produce offspring, even if love mode was applied before conversion. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreedDarkSniffer(EntityBreedEvent event) {
        if (isDarkSniffer(event.getMother()) || isDarkSniffer(event.getFather())) event.setCancelled(true);
    }

    /** Dark Sniffers are not food-driven/breedable in the reference mod. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFeedDarkSniffer(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isDarkSniffer(entity)) return;
        ItemStack held = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (held.getType() == Material.TORCHFLOWER_SEEDS) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSnifferDrop(EntityDropItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Sniffer)) return;

        Material droppedType = event.getItemDrop().getItemStack().getType();
        if (droppedType != Material.TORCHFLOWER_SEEDS && droppedType != Material.PITCHER_POD) return;

        var pdc = entity.getPersistentDataContainer();
        if (!"dark_sniffer".equals(pdc.get(creatureKey, PersistentDataType.STRING))) {
            // The reference mod's infected-Sniffer digging loot table is empty. Keep the
            // vanilla Sniffer from leaking seeds while its irreversible corruption is active.
            if (pdc.has(snifferCorruptionStartKey, PersistentDataType.LONG)) event.setCancelled(true);
            return;
        }

        // Cancel the vanilla item before attempting resolution, so a strict install with
        // a missing provider definition never leaks the wrong vanilla seed into gameplay.
        event.setCancelled(true);
        ItemStack gift = createGift();
        if (gift == null) {
            plugin.getLogger().warning(
                    "Dark Sniffer dug successfully, but provider item 'sculkflower_seeds' is unresolved; run /sift doctor.");
            return;
        }

        Location location = event.getItemDrop().getLocation().clone();
        Item dropped = location.getWorld().dropItem(location, gift);
        dropped.setPickupDelay(10);
    }

    private boolean isDarkSniffer(Entity entity) {
        return entity instanceof Sniffer && "dark_sniffer".equals(
                entity.getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING));
    }

    private ItemStack createGift() {
        if (plugin instanceof TheSiftFoundationPlugin foundation) {
            ItemStack providerItem = foundation.siftiteContent().item(
                    Material.TORCHFLOWER_SEEDS, "sculkflower_seeds", "Sculkflower Seeds");
            if (providerItem != null) return providerItem;
        }

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
