package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Grants the bundled advancement tree through Bukkit's public advancement API. */
public final class SiftAdvancementManager implements Listener {
    private static final String SIFT_WORLD = "thesift:the_sift";
    private final JavaPlugin plugin;
    private final NamespacedKey contentKey;
    private final NamespacedKey creatureKey;

    public SiftAdvancementManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.contentKey = new NamespacedKey(plugin, "content");
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
    }

    @EventHandler
    public void onEnterSift(PlayerChangedWorldEvent event) {
        if (isSift(event.getPlayer().getWorld())) grant(event.getPlayer(), "root");
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String id = contentId(event.getItem().getItemStack());
        if ("charoite".equals(id) || "siftite_nugget".equals(id)) grant(player, "shard");
        if ("rift_key".equals(id)) grant(player, "rift_key");
        if ("rift_disc".equals(id)) grant(player, "rift_melody");
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        String id = contentId(event.getRecipe().getResult());
        if (isSiftiteGear(id)
                && event.getWhoClicked() instanceof Player player) grant(player, "shard_gear");
    }

    private boolean isSiftiteGear(String id) {
        if (id == null) return false;
        return switch (id) {
            case "siftite_sword", "siftite_axe", "siftite_pickaxe", "siftite_shovel", "siftite_hoe",
                    "siftite_spear", "siftite_helmet", "siftite_chestplate", "siftite_leggings",
                    "siftite_boots" -> true;
            default -> false;
        };
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if ("ichor_bottle".equals(contentId(event.getItem()))) grant(event.getPlayer(), "storm");
    }

    /** IchorListener cancels vanilla consumption and handles bottle effects from this interaction. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onIchorBottleDrink(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || !"ichor_bottle".equals(contentId(event.getItem()))) return;
        grant(event.getPlayer(), "storm");
    }

    @EventHandler
    public void onCreatureDeath(EntityDeathEvent event) {
        var dead = event.getEntity();
        String id = dead.getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING);
        Player killer = dead.getKiller();
        if (killer == null || id == null) return;
        switch (id) {
            case "prism_warden" -> grant(killer, "warden", "prismatic");
            // The Sifter and its crimson Spire Tyrant variant share the kill grants: defeating
            // either counts as "sifter"; the tyrant advancement additionally unlocks for the
            // Spire Tyrant only.
            case "the_sifter", "sifter" -> grant(killer, "sifter");
            case "dark_sniffer" -> grant(killer, "sniffer_hunter");
            // Fan-made content, NOT mod parity: the reference mod (Arta48/Mielons-The-Sift)
            // has no "Spire Tyrant" creature, no Crimson Keep, and no "tyrant" advancement.
            // The variant ships disabled by default (content.spire_tyrant); killing one still
            // grants sifter + tyrant so the bundled fan-made advancement stays obtainable
            // when admins opt in.
            case "spire_tyrant" -> grant(killer, "sifter", "tyrant");
            default -> { }
        }
    }

    private String contentId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(contentKey, PersistentDataType.STRING);
    }

    private boolean isSift(World world) {
        return world.getKey().toString().equals(SIFT_WORLD);
    }

    private void grant(Player player, String... ids) {
        if (player == null) return;
        for (String id : ids) {
            Advancement advancement = Bukkit.getAdvancement(NamespacedKey.fromString("thesift:" + id));
            if (advancement == null) {
                plugin.getLogger().fine("Advancement thesift:" + id + " is not loaded.");
                continue;
            }
            var progress = player.getAdvancementProgress(advancement);
            List<String> remaining = progress.getRemainingCriteria().stream().toList();
            for (String criterion : remaining) progress.awardCriteria(criterion);
        }
    }
}
