package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/** Paper-side gameplay bridge for Mielon's three-stage Sculkflower crop. */
final class SculkflowerManager implements Listener {
    private static final String DIMENSION = "thesift:the_sift";
    private static final String SEED_ID = "sculkflower_seeds";
    private static final String CROP_STAGE0 = "sculkflower_crop_stage0";
    private static final String CROP_STAGE1 = "sculkflower_crop_stage1";
    private static final String FLOWER_ID = "sculkflower";
    private static final long GROWTH_INTERVAL_TICKS = 68L;

    private final JavaPlugin plugin;
    private final ExternalBlockBridge blocks;
    private final SiftiteContent content;
    private final NamespacedKey contentKey;
    private final NamespacedKey cropsKey;

    SculkflowerManager(JavaPlugin plugin, SiftiteContent content) {
        this.plugin = plugin;
        this.blocks = new ExternalBlockBridge(plugin);
        this.content = content;
        this.contentKey = new NamespacedKey(plugin, "content");
        this.cropsKey = new NamespacedKey(plugin, "sculkflower_crops");
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tickGrowth(), 40L, GROWTH_INTERVAL_TICKS);
    }

    /** Register the reference Sculkflower -> Light Blue Dye recipe with provider-backed item matching. */
    void registerRecipes() {
        NamespacedKey key = recipeKey();
        plugin.getServer().removeRecipe(key);
        ItemStack input = flower();
        if (input == null) return;
        ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(Material.LIGHT_BLUE_DYE));
        recipe.addIngredient(RecipeChoice.exactChoice(input));
        plugin.getServer().addRecipe(recipe);
    }

    /** Remove the recipe owned by this manager when the plugin is disabled. */
    void removeRecipes() {
        plugin.getServer().removeRecipe(recipeKey());
    }

    private NamespacedKey recipeKey() {
        return new NamespacedKey(plugin, "light_blue_dye_from_sculkflower");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isSift(player.getWorld())) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isSculkflowerSeeds(held)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || !canPlantOn(clicked.getType())) return;
        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.getType().isAir()) return;
        if (!blocks.place(CROP_STAGE0, target.getLocation())) return;

        registerCrop(target);
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
        // Reference-mod parity (planting_the_dark): the advancement fires specifically when
        // Sculkflower seeds go onto Sculk, not onto the plugin's farmland extension.
        if (clicked.getType() == Material.SCULK) {
            SiftAdvancementGrant.grant(plugin, player, "planting_the_dark");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBoneMeal(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isSift(player.getWorld())) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.BONE_MEAL) return;

        Block block = event.getClickedBlock();
        int age = cropAge(block);
        if (age < 0) return;
        if (!grow(block, age + 1)) return;

        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isSift(event.getBlock().getWorld())) return;
        int age = cropAge(event.getBlock());
        if (age < 0) {
            if (!blocks.isBlock(FLOWER_ID, event.getBlock())) return;
            unregisterCrop(event.getBlock());
            event.setDropItems(false);
            // The mature plant is the final crop state in the reference mod;
            // breaking it drops the flower itself, not an extra seed.
            drop(event.getBlock(), flower());
            return;
        }

        unregisterCrop(event.getBlock());
        event.setDropItems(false);
        drop(event.getBlock(), seed());
    }

    /**
     * Grows registered crops. Review P2: the old sampling swept every loaded chunk's full
     * world height eight times per tick to stumble over crop blocks. Crops are now recorded
     * in their chunk's PDC when planted, so the tick reads one PDC array per chunk and only
     * visits blocks that are actually crops; stale entries (crop removed by explosion or a
     * piston) are culled when sampled.
     */
    private void tickGrowth() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        tickGrowth(sift);
    }

    /** Package-private overload so tests can drive a MockBukkit world (dimension keys are unmockable). */
    void tickGrowth(World sift) {
        for (org.bukkit.Chunk chunk : sift.getLoadedChunks()) {
            long[] crops = chunk.getPersistentDataContainer().get(cropsKey, PersistentDataType.LONG_ARRAY);
            if (crops == null || crops.length == 0) continue;
            for (int attempt = 0; attempt < 8; attempt++) {
                long packed = crops[ThreadLocalRandom.current().nextInt(crops.length)];
                Block crop = chunk.getBlock((int) ((packed >> 4) & 0xF), (int) (packed >> 8),
                        (int) (packed & 0xF));
                int age = cropAge(crop);
                if (age < 0) {
                    // The block is gone or no longer a crop: drop the stale entry.
                    unregisterCrop(crop);
                    continue;
                }
                if (ThreadLocalRandom.current().nextInt(3) != 0) continue;
                grow(crop, age + 1);
            }
        }
    }

    boolean grow(Block block, int nextAge) {
        String id = nextAge >= 2 ? FLOWER_ID : CROP_STAGE1;
        boolean placed = blocks.place(id, block.getLocation());
        if (placed && nextAge >= 2) {
            // The mature flower no longer grows: remove it from the crop registry.
            unregisterCrop(block);
        }
        return placed;
    }

    /** Chunk-local crop registry entry: (y &lt;&lt; 8) | (localX &lt;&lt; 4) | localZ. */
    private static long packCrop(Block block) {
        return ((long) block.getY() << 8) | ((block.getX() & 0xF) << 4) | (block.getZ() & 0xF);
    }

    void registerCrop(Block block) {
        org.bukkit.Chunk chunk = block.getChunk();
        long packed = packCrop(block);
        long[] crops = chunk.getPersistentDataContainer().get(cropsKey, PersistentDataType.LONG_ARRAY);
        if (crops != null) {
            for (long existing : crops) if (existing == packed) return;
        }
        long[] next = crops == null ? new long[]{packed}
                : java.util.Arrays.copyOf(crops, crops.length + 1);
        next[next.length - 1] = packed;
        chunk.getPersistentDataContainer().set(cropsKey, PersistentDataType.LONG_ARRAY, next);
    }

    void unregisterCrop(Block block) {
        org.bukkit.Chunk chunk = block.getChunk();
        long[] crops = chunk.getPersistentDataContainer().get(cropsKey, PersistentDataType.LONG_ARRAY);
        if (crops == null) return;
        long packed = packCrop(block);
        int index = -1;
        for (int i = 0; i < crops.length; i++) if (crops[i] == packed) { index = i; break; }
        if (index < 0) return;
        long[] next = new long[crops.length - 1];
        System.arraycopy(crops, 0, next, 0, index);
        System.arraycopy(crops, index + 1, next, index, crops.length - index - 1);
        if (next.length == 0) chunk.getPersistentDataContainer().remove(cropsKey);
        else chunk.getPersistentDataContainer().set(cropsKey, PersistentDataType.LONG_ARRAY, next);
    }

    int cropAge(Block block) {
        if (block == null) return -1;
        if (blocks.isBlock(CROP_STAGE0, block)) return 0;
        if (blocks.isBlock(CROP_STAGE1, block)) return 1;
        return -1;
    }

    private boolean canPlantOn(Material material) {
        return material == Material.FARMLAND || material == Material.SCULK;
    }

    private boolean isSift(World world) {
        return world != null && world.getKey().toString().equals(DIMENSION);
    }

    private boolean isSculkflowerSeeds(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        String id = stack.getItemMeta().getPersistentDataContainer().get(contentKey, PersistentDataType.STRING);
        return SEED_ID.equals(id);
    }

    private ItemStack seed() {
        ItemStack stack = content.item(Material.TORCHFLOWER_SEEDS, SEED_ID, "Sculkflower Seeds");
        if (stack != null || plugin.getConfig().getBoolean("providers.strict", false)) return stack;
        return new ItemStack(Material.TORCHFLOWER_SEEDS);
    }

    private ItemStack flower() {
        ItemStack stack = content.item(Material.CACTUS_FLOWER, FLOWER_ID, "Sculkflower");
        if (stack != null || plugin.getConfig().getBoolean("providers.strict", false)) return stack;
        return new ItemStack(Material.CACTUS_FLOWER);
    }

    private void drop(Block block, ItemStack stack) {
        if (stack != null) block.getWorld().dropItemNaturally(block.getLocation(), stack);
    }
}
