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

    SculkflowerManager(JavaPlugin plugin, SiftiteContent content) {
        this.plugin = plugin;
        this.blocks = new ExternalBlockBridge(plugin);
        this.content = content;
        this.contentKey = new NamespacedKey(plugin, "content");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickGrowth, 40L, GROWTH_INTERVAL_TICKS);
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
            event.setDropItems(false);
            // The mature plant is the final crop state in the reference mod;
            // breaking it drops the flower itself, not an extra seed.
            drop(event.getBlock(), flower());
            return;
        }

        event.setDropItems(false);
        drop(event.getBlock(), seed());
    }

    private void tickGrowth() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        for (org.bukkit.Chunk chunk : sift.getLoadedChunks()) {
            // Sample columns, not random Y coordinates: choosing a uniform Y across a
            // modern world's full height almost never lands on a crop block.
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = ThreadLocalRandom.current().nextInt(16);
                int z = ThreadLocalRandom.current().nextInt(16);
                Block crop = null;
                int age = -1;
                for (int y = sift.getMaxHeight() - 1; y >= sift.getMinHeight(); y--) {
                    Block candidate = chunk.getBlock(x, y, z);
                    int candidateAge = cropAge(candidate);
                    if (candidateAge >= 0) {
                        crop = candidate;
                        age = candidateAge;
                        break;
                    }
                }
                if (crop == null || ThreadLocalRandom.current().nextInt(3) != 0) continue;
                grow(crop, age + 1);
            }
        }
    }

    private boolean grow(Block block, int nextAge) {
        String id = nextAge >= 2 ? FLOWER_ID : CROP_STAGE1;
        return blocks.place(id, block.getLocation());
    }

    private int cropAge(Block block) {
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
        if (stack != null || plugin.getConfig().getBoolean("providers.strict", true)) return stack;
        return new ItemStack(Material.TORCHFLOWER_SEEDS);
    }

    private ItemStack flower() {
        ItemStack stack = content.item(Material.POPPY, FLOWER_ID, "Sculkflower");
        if (stack != null || plugin.getConfig().getBoolean("providers.strict", true)) return stack;
        return new ItemStack(Material.POPPY);
    }

    private void drop(Block block, ItemStack stack) {
        if (stack != null) block.getWorld().dropItemNaturally(block.getLocation(), stack);
    }
}
