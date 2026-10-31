package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Grants the bundled advancement tree, which mirrors the reference mod's 22 advancements. Entries that use
 * vanilla triggers (Ancient City visit, biome visits) are handled by the datapack itself; the rest are
 * awarded from here or from the managers that own the event (Rift, Singer, Charoite enchanting, portal).
 *
 * <p>All eight reference-mod triggers outside the vanilla datapack are wired: planting
 * Sculkflower seeds on Sculk, igniting wood inside the dimension, a tamed Blub killing a
 * Dark Snifter (owner), Blub taming, the Echo Golem returning the Soul Block, the Ichor
 * Snowball Warden feint (false_alarm), mining Charoite/Siftite ore, and filling the Ichor
 * bucket. The Echo Golem carrying Soul Blocks to Singers (like_father_and_son) is awarded
 * by EchoGolemManager's delivery pulse.
 */
public final class SiftAdvancementManager implements Listener {
    private static final String SIFT_WORLD = "thesift:the_sift";
    private final JavaPlugin plugin;
    private final NamespacedKey contentKey;
    private final NamespacedKey firstEntryKey;

    public SiftAdvancementManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.contentKey = new NamespacedKey(plugin, "content");
        this.firstEntryKey = new NamespacedKey(plugin, "sift_first_entry");
    }

    @EventHandler
    public void onEnterSift(PlayerChangedWorldEvent event) {
        if (!isSift(event.getPlayer().getWorld())) return;
        grant(event.getPlayer(), "root", "story/brave_the_unknown");
        // Reference-mod 1.1.x: an ambient sting the first time a player enters the dimension.
        Player player = event.getPlayer();
        Long seen = player.getPersistentDataContainer().get(firstEntryKey, PersistentDataType.LONG);
        if (seen == null) {
            player.getPersistentDataContainer().set(firstEntryKey, PersistentDataType.LONG,
                    System.currentTimeMillis());
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.7f);
            Messages.send(player, "world.first-entry", "The Sift hums around you for the first time.");
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String id = contentId(event.getItem().getItemStack());
        if ("charoite".equals(id)) grant(player, "kinda_purpleish");
        if ("siftite_nugget".equals(id)) grant(player, "sifted_nugget");
        // Reference-mod parity (smells_creepy): obtain Sculkflower seeds.
        if ("sculkflower_seeds".equals(id)) grant(player, "smells_creepy");
        // The mod's inventory_changed covers every way of receiving the armour, so check the
        // full set on pickup too, not only craft/smith.
        if (id != null && (id.equals("siftite_helmet") || id.equals("siftite_chestplate")
                || id.equals("siftite_leggings") || id.equals("siftite_boots"))) {
            checkSiftiteSetNextTick(player);
        }
    }

    /**
     * Reference-mod parity (spicewood): ignite any wood block while inside the Sift dimension
     * with flint and steel or a fire charge. Wood detection follows the mod's block-tag list.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgniteWood(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getItem();
        if (held == null || (held.getType() != Material.FLINT_AND_STEEL
                && held.getType() != Material.FIRE_CHARGE)) return;
        if (event.getClickedBlock() == null || !isSift(event.getPlayer().getWorld())) return;
        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked.getType().isAir()) return;
        boolean wood = Tag.LOGS.isTagged(clicked.getType()) || Tag.PLANKS.isTagged(clicked.getType())
                || Tag.WOODEN_STAIRS.isTagged(clicked.getType()) || Tag.WOODEN_SLABS.isTagged(clicked.getType())
                || Tag.WOODEN_FENCES.isTagged(clicked.getType()) || Tag.WOODEN_DOORS.isTagged(clicked.getType())
                || Tag.WOODEN_TRAPDOORS.isTagged(clicked.getType()) || Tag.WOODEN_BUTTONS.isTagged(clicked.getType())
                || Tag.WOODEN_PRESSURE_PLATES.isTagged(clicked.getType());
        if (wood) grant(event.getPlayer(), "spicewood");
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (isSiftiteGear(contentId(event.getRecipe().getResult()))
                && event.getWhoClicked() instanceof Player player) checkSiftiteSetNextTick(player);
        // Reference-mod parity (smells_creepy): crafting Sculkflower seeds also counts.
        if ("sculkflower_seeds".equals(contentId(event.getRecipe().getResult()))
                && event.getWhoClicked() instanceof Player player) grant(player, "smells_creepy");
    }

    @EventHandler
    public void onSmith(SmithItemEvent event) {
        if (isSiftiteGear(contentId(event.getInventory().getResult()))
                && event.getWhoClicked() instanceof Player player) checkSiftiteSetNextTick(player);
    }

    /** Mod advancement "Keep Inventory": die while carrying Siftite equipment. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            String id = contentId(drop);
            if (id != null && id.startsWith("siftite_")
                    && !"siftite_nugget".equals(id) && !"siftite_ingot".equals(id)) {
                grant(event.getEntity(), "keep_inventory");
                return;
            }
        }
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

    private void checkSiftiteSetNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean helmet = false, chest = false, legs = false, boots = false;
            var inventory = player.getInventory();
            java.util.List<ItemStack> all = new java.util.ArrayList<>(java.util.Arrays.asList(inventory.getContents()));
            for (ItemStack piece : all) {
                String id = contentId(piece);
                if ("siftite_helmet".equals(id)) helmet = true;
                else if ("siftite_chestplate".equals(id)) chest = true;
                else if ("siftite_leggings".equals(id)) legs = true;
                else if ("siftite_boots".equals(id)) boots = true;
            }
            if (helmet && chest && legs && boots) grant(player, "cover_me_in_siftite");
        });
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if ("ichor_bottle".equals(contentId(event.getItem()))) grant(event.getPlayer(), "blood_of_the_gods");
    }

    /** IchorListener cancels vanilla consumption and handles bottle effects from this interaction. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIchorBottleDrink(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                || !"ichor_bottle".equals(contentId(event.getItem()))) return;
        grant(event.getPlayer(), "blood_of_the_gods");
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
