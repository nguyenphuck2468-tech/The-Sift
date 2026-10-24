package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Persists identity for Soul Block substitutes without NMS or tile entities. */
public final class SoulBlockListener implements Listener {
    private static final String ID = "soul_block";
    private final JavaPlugin plugin;
    private final NamespacedKey contentKey;
    private final NamespacedKey positionsKey;
    private final ExternalBlockBridge blockBridge;

    public SoulBlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.contentKey = new NamespacedKey(plugin, "content");
        this.positionsKey = new NamespacedKey(plugin, "soul_block_positions");
        this.blockBridge = new ExternalBlockBridge(plugin);
    }

    static void markGenerated(JavaPlugin plugin, Block block) {
        new SoulBlockListener(plugin).mark(block);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("content.soul_block", true)) return;
        ItemStack stack = event.getItemInHand();
        if (stack == null || !stack.hasItemMeta()
                || !ID.equals(stack.getItemMeta().getPersistentDataContainer()
                .get(contentKey, PersistentDataType.STRING))) return;
        mark(event.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("content.soul_block", true)) return;
        Block block = event.getBlock();
        if (!isMarked(block) || block.getType() != Material.SOUL_SAND
                && !blockBridge.isBlock(ID, block)) return;
        remove(block);
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(),
                new SiftiteContent(plugin).forId(ID));
    }

    private boolean isMarked(Block block) {
        String value = block.getChunk().getPersistentDataContainer().get(positionsKey, PersistentDataType.STRING);
        return value != null && Arrays.asList(value.split(";")).contains(key(block));
    }

    private void mark(Block block) {
        var pdc = block.getChunk().getPersistentDataContainer();
        String value = pdc.get(positionsKey, PersistentDataType.STRING);
        String key = key(block);
        if (value == null || value.isBlank()) pdc.set(positionsKey, PersistentDataType.STRING, key);
        else if (!Arrays.asList(value.split(";")).contains(key)) {
            pdc.set(positionsKey, PersistentDataType.STRING, value + ";" + key);
        }
    }

    private void remove(Block block) {
        var pdc = block.getChunk().getPersistentDataContainer();
        String value = pdc.get(positionsKey, PersistentDataType.STRING);
        if (value == null) return;
        String remaining = Arrays.stream(value.split(";")).filter(part -> !part.equals(key(block)))
                .collect(Collectors.joining(";"));
        if (remaining.isBlank()) pdc.remove(positionsKey);
        else pdc.set(positionsKey, PersistentDataType.STRING, remaining);
    }

    private String key(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
