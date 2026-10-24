package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public final class SiftiteListener implements Listener {
    private final SiftiteContent content;
    private final JavaPlugin plugin;
    private final NamespacedKey oreMarker;
    private final NamespacedKey siftiteOreMarker;
    private final ExternalBlockBridge blockBridge;

    public SiftiteListener(JavaPlugin plugin, SiftiteContent content) {
        this.plugin = plugin;
        this.content = content;
        this.oreMarker = new NamespacedKey(plugin, "charoite_ore_positions");
        this.siftiteOreMarker = new NamespacedKey(plugin, "siftite_ore_positions");
        this.blockBridge = new ExternalBlockBridge(plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onOreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material siftiteOreMaterial = Material.matchMaterial(plugin.getConfig().getString("siftite.ore-material", "ANCIENT_DEBRIS"));
        if (plugin.getConfig().getBoolean("content.siftite", true)
                && plugin.getConfig().getBoolean("content.siftite_ore", true)
                && plugin.getConfig().getBoolean("content.siftite_nugget", true)
                && isMarked(block, siftiteOreMarker) && (block.getType() == siftiteOreMaterial
                || blockBridge.isBlock("siftite_ore", block))) {
            Player player = event.getPlayer();
            String tool = player.getInventory().getItemInMainHand().getType().name();
            if (!(tool.startsWith("DIAMOND_") || tool.startsWith("NETHERITE_")) || !tool.endsWith("_PICKAXE")) return;
            event.setDropItems(false);
            removeMarked(block, siftiteOreMarker);
            int amount = ThreadLocalRandom.current().nextInt(1, 3);
            ItemStack drop = content.item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget");
            drop.setAmount(amount);
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
            return;
        }
        if (!plugin.getConfig().getBoolean("content.charoite", true)
                || !plugin.getConfig().getBoolean("content.charoite_ore", true)
                || !plugin.getConfig().getBoolean("content.charoite_item", true)) return;
        String configuredWorld = plugin.getConfig().getString("charoite.world", "thesift:the_sift");
        if (!block.getWorld().getKey().toString().equalsIgnoreCase(configuredWorld)) return;
        Material oreMaterial = Material.matchMaterial(plugin.getConfig().getString("charoite.ore-material", "AMETHYST_BLOCK"));
        if (!isMarked(block, oreMarker)
                || (oreMaterial == null || block.getType() != oreMaterial)
                && !blockBridge.isBlock("charoite_ore", block)) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!tool.getType().name().endsWith("_PICKAXE") || tool.getType().name().startsWith("WOODEN_")
                || tool.getType().name().startsWith("GOLDEN_") || tool.getType().name().startsWith("STONE_")) return;
        event.setDropItems(false);
        removeMarked(block, oreMarker);
        int min = Math.max(0, plugin.getConfig().getInt("charoite.drop-min", 1));
        int max = Math.max(min, plugin.getConfig().getInt("charoite.drop-max", 2));
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        if (amount == 0) return;
        ItemStack drop = content.item(Material.LAPIS_LAZULI, "charoite", "Charoite");
        drop.setAmount(amount);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
    }

    static void markGeneratedOre(JavaPlugin plugin, Block block) {
        mark(plugin, block, new NamespacedKey(plugin, "charoite_ore_positions"));
    }

    static void markGeneratedSiftiteOre(JavaPlugin plugin, Block block) {
        mark(plugin, block, new NamespacedKey(plugin, "siftite_ore_positions"));
    }

    private static void mark(JavaPlugin plugin, Block block, NamespacedKey marker) {
        var pdc = block.getChunk().getPersistentDataContainer();
        String existing = pdc.get(marker, PersistentDataType.STRING);
        String key = positionKey(block);
        if (existing == null || existing.isBlank()) pdc.set(marker, PersistentDataType.STRING, key);
        else if (!java.util.Arrays.asList(existing.split(";")).contains(key)) {
            pdc.set(marker, PersistentDataType.STRING, existing + ";" + key);
        }
    }

    private static String positionKey(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isMarked(Block block, NamespacedKey key) {
        String positions = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return positions != null && java.util.Arrays.asList(positions.split(";")).contains(positionKey(block));
    }

    private void removeMarked(Block block, NamespacedKey key) {
        var pdc = block.getChunk().getPersistentDataContainer();
        String positions = pdc.get(key, PersistentDataType.STRING);
        if (positions == null) return;
        String updated = java.util.Arrays.stream(positions.split(";"))
                .filter(value -> !value.equals(positionKey(block))).collect(java.util.stream.Collectors.joining(";"));
        if (updated.isBlank()) pdc.remove(key);
        else pdc.set(key, PersistentDataType.STRING, updated);
    }
}
