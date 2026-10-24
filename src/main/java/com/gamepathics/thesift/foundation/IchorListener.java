package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class IchorListener implements Listener {
    private final JavaPlugin plugin;
    private final IchorContent content;
    private final IchorState state;

    public IchorListener(JavaPlugin plugin, IchorContent content, IchorState state) {
        this.plugin = Objects.requireNonNull(plugin);
        this.content = Objects.requireNonNull(content);
        this.state = Objects.requireNonNull(state);
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onCauldronInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !content.isContentEnabled(IchorContent.ICHOR_BOTTLE_ID)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != configuredCauldron()) {
            return;
        }
        ItemStack held = event.getItem();
        int cost = Math.max(1, plugin.getConfig().getInt("ichor.cauldron.bottle-cost", 1));
        if (held == null || held.getType() != Material.GLASS_BOTTLE
                || state.level(block.getLocation()) < cost) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        int nextLevel = state.level(block.getLocation()) - cost;
        state.setLevel(block.getLocation(), nextLevel);
        consumeOne(inventory);
        giveOrDrop(player, content.ichorBottle());
        if (plugin.getConfig().getBoolean("ichor.cauldron.sounds", true)) {
            player.playSound(player.getLocation(), "block.brewing_stand.brew", 0.8f, 1.2f);
        }
        if (plugin.getConfig().getBoolean("ichor.cauldron.particles", true)) {
            block.getWorld().spawnParticle(org.bukkit.Particle.DRIPPING_OBSIDIAN_TEAR,
                    block.getLocation().add(0.5, 1.0, 0.5), 8, 0.25, 0.2, 0.25, 0.02);
        }
        player.sendMessage("§dThe cauldron fills the bottle with resonant Ichor. §7("
                + nextLevel + "/" + plugin.getConfig().getInt("ichor.cauldron.max-level", 3) + ")");
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onIchorBottleRefill(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !content.isContentEnabled(IchorContent.ICHOR_BOTTLE_ID)
                || !plugin.getConfig().getBoolean("ichor.cauldron.refill-from-source", true)) {
            return;
        }
        Block block = event.getClickedBlock();
        ItemStack held = event.getItem();
        int maxLevel = Math.max(1, plugin.getConfig().getInt("ichor.cauldron.max-level", 3));
        if (block == null || held == null || !content.is(held, IchorContent.ICHOR_BOTTLE_ID)
                || block.getType() != configuredCauldron() || state.level(block.getLocation()) >= maxLevel) {
            return;
        }
        event.setCancelled(true);
        // One bottle contributes one charge; a single bottle cannot refill a
        // three-charge cauldron and then be withdrawn as three bottles.
        state.setLevel(block.getLocation(), Math.min(
                maxLevel,
                state.level(block.getLocation()) + 1));
        consumeOne(event.getPlayer().getInventory());
        event.getPlayer().sendMessage("§dThe cauldron resonates with Ichor.");
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onDrinkIchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !content.isIchorEnabled()
                || event.getAction() != Action.RIGHT_CLICK_AIR
                || !content.isContentEnabled(IchorContent.ICHOR_BOTTLE_ID)
                || !content.is(event.getItem(), IchorContent.ICHOR_BOTTLE_ID)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        consumeOne(player.getInventory());
        giveOrDrop(player, new ItemStack(Material.GLASS_BOTTLE));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, true, true));
        player.playSound(player.getLocation(), "minecraft:item.honey_bottle.drink", 0.8f, 1.25f);
        player.getWorld().spawnParticle(org.bukkit.Particle.DRIPPING_DRIPSTONE_WATER,
                player.getLocation().add(0, 1, 0), 8, 0.25, 0.35, 0.25, 0.01);
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String id = content.isContentEnabled("ichor_glass") && content.is(item, IchorContent.ICHOR_GLASS_ID) ? IchorContent.ICHOR_GLASS_ID
                : content.isContentEnabled("ichor_glass_pane") && content.is(item, IchorContent.ICHOR_GLASS_PANE_ID) ? IchorContent.ICHOR_GLASS_PANE_ID
                : content.is(item, IchorContent.ICHOR_SNOW_ID) ? IchorContent.ICHOR_SNOW_ID
                : content.is(item, IchorContent.ICHOR_SNOW_BLOCK_ID) ? IchorContent.ICHOR_SNOW_BLOCK_ID : null;
        if (id != null && (id.equals(IchorContent.ICHOR_GLASS_ID) || id.equals(IchorContent.ICHOR_GLASS_PANE_ID)
                ? true : content.isContentEnabled(id))) {
            state.setBlock(event.getBlockPlaced().getLocation(), id);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        String id = state.block(event.getBlock().getLocation());
        if (id == null || !content.isContentEnabled(id)) {
            return;
        }
        event.setDropItems(false);
        ItemStack drop = switch (id) {
            case IchorContent.ICHOR_GLASS_ID -> content.ichorGlass();
            case IchorContent.ICHOR_GLASS_PANE_ID -> content.create(Material.GLASS_PANE,
                    IchorContent.ICHOR_GLASS_PANE_ID, "Ichor Glass Pane");
            case IchorContent.ICHOR_SNOW_ID -> content.ichorSnow();
            case IchorContent.ICHOR_SNOW_BLOCK_ID -> content.ichorSnowBlock();
            default -> null;
        };
        state.removeBlock(event.getBlock().getLocation());
        if (drop != null) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
        }
    }

    private Material configuredCauldron() {
        String configured = plugin.getConfig().getString("ichor.cauldron-material", "WATER_CAULDRON");
        Material material = Material.matchMaterial(configured);
        return material == null ? Material.WATER_CAULDRON : material;
    }

    private void consumeOne(PlayerInventory inventory) {
        ItemStack held = inventory.getItemInMainHand();
        if (held.getAmount() <= 1) {
            inventory.setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
