package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
    private final ExternalItemBridge externalItems;
    private final SiftiteContent content;

    public SoulBlockListener(JavaPlugin plugin) {
        this(plugin, new SiftiteContent(plugin));
    }

    SoulBlockListener(JavaPlugin plugin, SiftiteContent content) {
        this.plugin = plugin;
        this.content = content;
        this.contentKey = new NamespacedKey(plugin, "content");
        this.positionsKey = new NamespacedKey(plugin, "soul_block_positions");
        this.blockBridge = new ExternalBlockBridge(plugin);
        this.externalItems = new ExternalItemBridge(plugin);
    }

    static void markGenerated(JavaPlugin plugin, Block block) {
        NamespacedKey positionsKey = new NamespacedKey(plugin, "soul_block_positions");
        mark(positionsKey, block);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        remove(event.getBlockPlaced());
        if (!plugin.getConfig().getBoolean("content.soul_block", true)) return;
        ItemStack stack = event.getItemInHand();
        boolean tagged = stack != null && stack.hasItemMeta() && ID.equals(stack.getItemMeta()
                .getPersistentDataContainer().get(contentKey, PersistentDataType.STRING));
        if (!tagged && !externalItems.isItem(ID, stack)) return;
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
                content.forId(ID));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().forEach(this::remove); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().forEach(this::remove); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent event) { remove(event.getBlock()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) { remove(event.getBlock()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) { event.getBlocks().forEach(this::remove); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) { event.getBlocks().forEach(this::remove); }

    private boolean isMarked(Block block) {
        String value = block.getChunk().getPersistentDataContainer().get(positionsKey, PersistentDataType.STRING);
        return value != null && Arrays.asList(value.split(";")).contains(key(block));
    }

    private void mark(Block block) {
        mark(positionsKey, block);
    }

    private static void mark(NamespacedKey positionsKey, Block block) {
        var pdc = block.getChunk().getPersistentDataContainer();
        String value = pdc.get(positionsKey, PersistentDataType.STRING);
        String key = key(block.getX(), block.getY(), block.getZ());
        if (value == null || value.isBlank()) pdc.set(positionsKey, PersistentDataType.STRING, key);
        else if (!Arrays.asList(value.split(";")).contains(key)) pdc.set(positionsKey, PersistentDataType.STRING, value + ";" + key);
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
        return key(block.getX(), block.getY(), block.getZ());
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
