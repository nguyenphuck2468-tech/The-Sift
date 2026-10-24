package com.gamepathics.thesift.foundation;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Lapis-compatible Charoite enchanting approximation. */
public final class CharoiteEnchantingListener implements Listener {
    private final NamespacedKey contentKey;
    private final NamespacedKey charoiteEnchantedKey;

    public CharoiteEnchantingListener(JavaPlugin plugin) {
        contentKey = new NamespacedKey(plugin, "content");
        charoiteEnchantedKey = new NamespacedKey(plugin, "charoite_enchanted");
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (!isCharoite(event.getView().getTopInventory() instanceof org.bukkit.inventory.EnchantingInventory inv
                ? inv.getSecondary() : null)) return;
        for (EnchantmentOffer offer : event.getOffers()) {
            if (offer == null || offer.getEnchantment() == null) continue;
            if (offer.getEnchantment().getMaxLevel() > 1) {
                offer.setEnchantmentLevel(offer.getEnchantmentLevel() + 1);
            }
            offer.setCost(offer.getCost() + 20);
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (!(event.getInventory() instanceof org.bukkit.inventory.EnchantingInventory inventory)
                || !isCharoite(inventory.getSecondary())) return;
        event.getEnchantsToAdd().replaceAll((enchantment, level) ->
                enchantment.getMaxLevel() > 1 ? level + 1 : level);
        ItemStack item = event.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(charoiteEnchantedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        event.setItem(item);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) return;
        if (anvil.getFirstItem() != null && anvil.getSecondItem() != null
                && (isCharoiteEnchanted(anvil.getFirstItem()) || isCharoiteEnchanted(anvil.getSecondItem()))) {
            event.setResult(null);
        }
    }

    private boolean isCharoite(ItemStack item) {
        return item != null && item.hasItemMeta()
                && "charoite".equals(item.getItemMeta().getPersistentDataContainer()
                        .get(contentKey, PersistentDataType.STRING));
    }

    private boolean isCharoiteEnchanted(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(charoiteEnchantedKey, PersistentDataType.BYTE);
    }
}
