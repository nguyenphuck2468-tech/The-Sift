package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Keyed;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SiftiteContent implements Listener {
    private static final java.util.Map<String, Boolean> ITEM_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> MISSING_ITEM_WARNINGS = ConcurrentHashMap.newKeySet();
    private final JavaPlugin plugin;
    private final NamespacedKey idKey;
    private final ExternalItemBridge externalItems;

    public SiftiteContent(JavaPlugin plugin) {
        this(plugin, new ExternalItemBridge(plugin));
    }

    /** Shared wiring: reuse one ExternalItemBridge (and its provider caches) across registries. */
    SiftiteContent(JavaPlugin plugin, ExternalItemBridge externalItems) {
        this.plugin = Objects.requireNonNull(plugin);
        this.idKey = new NamespacedKey(plugin, "content");
        this.externalItems = Objects.requireNonNull(externalItems);
    }

    public void registerRecipes() {
        Server server = plugin.getServer();
        for (String key : java.util.List.of("charoite", "siftite_nugget", "siftite_ingot",
                "siftite_upgrade_smithing_template", "smelt_raw_sifter_meat", "smelt_blub_meat")) {
            server.removeRecipe(new NamespacedKey(plugin, key));
        }
        for (String key : java.util.List.of("siftite_sword", "siftite_axe", "siftite_pickaxe",
                "siftite_shovel", "siftite_hoe", "siftite_helmet", "siftite_chestplate",
                "siftite_leggings", "siftite_boots")) server.removeRecipe(new NamespacedKey(plugin, key));
        if (plugin.getConfig().getBoolean("providers.strict", true)
                && !new ProviderReadiness(plugin).itemsReady()) {
            return;
        }
        if (enabled("charoite_item", "charoite.enabled")) {
            addShaped("charoite", item(Material.LAPIS_LAZULI, "charoite", "Charoite"),
                    " C ", "CAC", " C ", 'C', Material.AMETHYST_SHARD, 'A', Material.AMETHYST_BLOCK);
        }
        // Reference-mod parity: Cooked Sifter Meat / Cooked Blub Meat come from smelting the
        // raw drops (see SiftEcologyManager#onEntityDeath), so they must be obtainable even
        // when FurnaceSmeltEvent is cancelled by other plugins.
        registerFurnish("smelt_raw_sifter_meat",
                item(Material.BEEF, "raw_sifter_meat", "Raw Sifter Meat"),
                item(Material.COOKED_BEEF, "cooked_sifter_meat", "Cooked Sifter Meat"));
        registerFurnish("smelt_blub_meat",
                item(Material.MUTTON, "blub_meat", "Blub Meat"),
                item(Material.COOKED_MUTTON, "cooked_blub_meat", "Cooked Blub Meat"));
        if (enabled("siftite_nugget", "siftite.enabled")) {
            addShapeless("siftite_nugget", item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget"),
                    Material.IRON_NUGGET, Material.AMETHYST_SHARD);
        }
        if (enabled("siftite_ingot", "siftite.enabled") && enabled("siftite_nugget", "siftite.enabled")) {
            addShaped("siftite_ingot", item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot"),
                    "NNN", "NNN", "NNN", 'N', exact(item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget")));
        }
        if (enabled("siftite_upgrade_smithing_template", "siftite.enabled")
                && enabled("siftite_ingot", "siftite.enabled")) addTemplateRecipe();
        if (!enabled("siftite_upgrade_smithing_template", "siftite.enabled")
                || !enabled("siftite_ingot", "siftite.enabled")) return;
        registerSmithingIfEnabled(server, "sword", Material.NETHERITE_SWORD, "siftite_sword", "Siftite Sword");
        registerSmithingIfEnabled(server, "axe", Material.NETHERITE_AXE, "siftite_axe", "Siftite Axe");
        registerSmithingIfEnabled(server, "pickaxe", Material.NETHERITE_PICKAXE, "siftite_pickaxe", "Siftite Pickaxe");
        registerSmithingIfEnabled(server, "shovel", Material.NETHERITE_SHOVEL, "siftite_shovel", "Siftite Shovel");
        registerSmithingIfEnabled(server, "hoe", Material.NETHERITE_HOE, "siftite_hoe", "Siftite Hoe");
        registerSmithingIfEnabled(server, "helmet", Material.NETHERITE_HELMET, "siftite_helmet", "Siftite Helmet");
        registerSmithingIfEnabled(server, "chestplate", Material.NETHERITE_CHESTPLATE, "siftite_chestplate", "Siftite Chestplate");
        registerSmithingIfEnabled(server, "leggings", Material.NETHERITE_LEGGINGS, "siftite_leggings", "Siftite Leggings");
        registerSmithingIfEnabled(server, "boots", Material.NETHERITE_BOOTS, "siftite_boots", "Siftite Boots");
    }

    private boolean enabled(String id, String parentPath) {
        String family = id.startsWith("charoite") ? "charoite" : "siftite";
        boolean sectionEnabled = plugin.getConfig().getBoolean(parentPath, true);
        return sectionEnabled && plugin.getConfig().getBoolean("content." + family, true)
                && plugin.getConfig().getBoolean("content." + id, true);
    }

    private void registerSmithingIfEnabled(Server server, String recipeId, Material base,
                                           String contentId, String name) {
        if (enabled(contentId, "siftite.enabled")) {
            registerSmithing(server, recipeId, base, base, contentId, name);
        } else {
            server.removeRecipe(new NamespacedKey(plugin, "siftite_" + recipeId));
        }
    }

    public ItemStack item(Material material, String id, String name) {
        ItemStack result = externalItems.create(id);
        boolean providerItem = result != null;
        if (result == null && plugin.getConfig().getBoolean("providers.strict", true)) {
            if (MISSING_ITEM_WARNINGS.add(id)) plugin.getLogger().warning(
                    "Strict provider mode refused vanilla fallback for item ID '" + id
                            + "'. Run /sift doctor and install a provider definition.");
            return null;
        }
        if (result == null) result = new ItemStack(material);
        ItemMeta meta = result.getItemMeta();
        if (!providerItem) meta.setDisplayName("§d" + name);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (!providerItem && hasItemModel(id)) meta.setItemModel(NamespacedKey.fromString("thesift:" + id));
        if (id.startsWith("siftite_") && !id.equals("siftite_upgrade_smithing_template")) {
            addAttributes(meta, id);
            applyDurabilityMultiplier(result, meta, id);
        }
        result.setItemMeta(meta);
        return result;
    }

    /**
     * Applies {@code siftite.durability-multiplier} from config.yml to freshly crafted Siftite
     * gear by raising the item's custom max-damage above its vanilla base (Paper Damageable
     * meta). This makes the config knob a real gameplay effect instead of a dead flag surfaced
     * only in {@code /sift doctor}. Damage stays clamped at 0; the extra headroom lives in
     * getMaxDamage(), which is how vanilla treats items that have bonus durability.
     */
    private void applyDurabilityMultiplier(ItemStack result, ItemMeta meta, String id) {
        double multiplier = plugin.getConfig().getDouble("siftite.durability-multiplier", 1.2);
        if (multiplier <= 1.0 || result.getType().getMaxDurability() <= 0) return;
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) return;
        int extra = (int) Math.round(result.getType().getMaxDurability() * (multiplier - 1.0));
        if (extra <= 0) return;
        // getMaxDamage() throws unless a custom ceiling was set before, so fall back to the
        // material's vanilla durability as the baseline.
        int base = damageable.hasMaxDamage() ? damageable.getMaxDamage() : result.getType().getMaxDurability();
        damageable.setMaxDamage(base + extra);
    }

    private boolean hasItemModel(String id) {
        return ITEM_MODEL_CACHE.computeIfAbsent(id, key -> {
            try (var modelFile = plugin.getResource("pack/assets/thesift/items/" + key + ".json")) {
                return modelFile != null;
            } catch (java.io.IOException exception) {
                plugin.getLogger().fine("Could not inspect item model for " + key + ": " + exception.getMessage());
                return false;
            }
        });
    }

    public boolean is(ItemStack stack, String id) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return id.equals(stack.getItemMeta().getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING));
    }

    /** The PDC key this registry stamps item IDs under; lets other managers read tags directly. */
    public NamespacedKey idKey() {
        return idKey;
    }

    public ItemStack forId(String id) {
        ItemStack foundationItem = switch (id) {
            case "charoite" -> item(Material.LAPIS_LAZULI, id, "Charoite");
            case "charoite_ore" -> item(Material.AMETHYST_BLOCK, id, "Charoite Ore");
            case "siftite_nugget" -> item(Material.IRON_NUGGET, id, "Siftite Nugget");
            case "siftite_ingot" -> item(Material.NETHERITE_INGOT, id, "Siftite Ingot");
            case "siftite_ore" -> item(Material.ANCIENT_DEBRIS, id, "Siftite Ore");
            case "siftite_upgrade_smithing_template" ->
                    item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, id, "Siftite Upgrade Template");
            case "siftite_sword" -> item(Material.NETHERITE_SWORD, id, "Siftite Sword");
            case "siftite_axe" -> item(Material.NETHERITE_AXE, id, "Siftite Axe");
            case "siftite_pickaxe" -> item(Material.NETHERITE_PICKAXE, id, "Siftite Pickaxe");
            case "siftite_shovel" -> item(Material.NETHERITE_SHOVEL, id, "Siftite Shovel");
            case "siftite_hoe" -> item(Material.NETHERITE_HOE, id, "Siftite Hoe");
            case "siftite_spear" -> item(Material.NETHERITE_SWORD, id, "Siftite Spear");
            case "siftite_helmet" -> item(Material.NETHERITE_HELMET, id, "Siftite Helmet");
            case "siftite_chestplate" -> item(Material.NETHERITE_CHESTPLATE, id, "Siftite Chestplate");
            case "siftite_leggings" -> item(Material.NETHERITE_LEGGINGS, id, "Siftite Leggings");
            case "siftite_boots" -> item(Material.NETHERITE_BOOTS, id, "Siftite Boots");
            default -> null;
        };
        if (foundationItem != null) return foundationItem;

        Material material = switch (id) {
            case "siftslate", "sonorous_deepslate" -> Material.DEEPSLATE;
            case "soul_block" -> Material.SOUL_SAND;
            case "sift_soil" -> Material.ROOTED_DIRT;
            case "sift_grass", "healthy_sculk" -> id.equals("healthy_sculk") ? Material.SCULK : Material.MOSS_BLOCK;
            case "sift_log", "overgrown_willow" -> Material.CHERRY_LOG;
            case "sift_leaves" -> Material.CHERRY_LEAVES;
            case "sift_planks" -> Material.CHERRY_PLANKS;
            case "siftstone" -> Material.TUFF;
            case "siftstone_bricks" -> Material.TUFF_BRICKS;
            case "chiseled_siftstone", "sift_altar" -> Material.CHISELED_DEEPSLATE;
            case "shard_ore", "charoite_ore" -> Material.AMETHYST_BLOCK;
            case "siftite_ore" -> Material.ANCIENT_DEBRIS;
            case "rift_gate" -> Material.CRYING_OBSIDIAN;
            case "sift_shard", "spire_fragment", "charoite" -> Material.AMETHYST_SHARD;
            case "sky_fragment" -> Material.PHANTOM_MEMBRANE;
            case "frost_fragment" -> Material.PRISMARINE_SHARD;
            case "sift_core", "heart_of_the_sift" -> Material.ECHO_SHARD;
            case "sift_catalyst" -> Material.BLAZE_POWDER;
            case "sift_crown" -> Material.GOLDEN_HELMET;
            case "raw_loomer" -> Material.RABBIT;
            case "cooked_loomer" -> Material.COOKED_RABBIT;
            case "bulb_glow" -> Material.GLOWSTONE_DUST;
            case "shard_sword", "prism_blade" -> Material.NETHERITE_SWORD;
            case "shard_pickaxe" -> Material.NETHERITE_PICKAXE;
            case "shard_axe" -> Material.NETHERITE_AXE;
            case "prism_helmet" -> Material.NETHERITE_HELMET;
            case "prism_chestplate" -> Material.NETHERITE_CHESTPLATE;
            case "prism_leggings" -> Material.NETHERITE_LEGGINGS;
            case "prism_boots" -> Material.NETHERITE_BOOTS;
            case "phase_boots" -> Material.DIAMOND_BOOTS;
            case "rift_key" -> Material.TRIAL_KEY;
            case "rift_disc" -> Material.MUSIC_DISC_5;
            case "bulb" -> Material.SLIME_BALL;
            case "wisp", "violet_wisp" -> Material.ALLAY_SPAWN_EGG;
            case "koi" -> Material.TROPICAL_FISH_SPAWN_EGG;
            case "hollow_bulb" -> Material.ZOMBIE_SPAWN_EGG;
            case "glowbell" -> Material.BAT_SPAWN_EGG;
            case "thornback" -> Material.TURTLE_SPAWN_EGG;
            case "glimmerwing" -> Material.BEE_SPAWN_EGG;
            case "the_sifter" -> Material.RAVAGER_SPAWN_EGG;
            case "frost_stalker" -> Material.STRAY_SPAWN_EGG;
            case "sunspore" -> Material.MOOSHROOM_SPAWN_EGG;
            case "drifter" -> Material.ENDERMAN_SPAWN_EGG;
            case "shardling" -> Material.ENDERMITE_SPAWN_EGG;
            case "prism_warden" -> Material.WARDEN_SPAWN_EGG;
            case "lumen_tulip" -> Material.TORCHFLOWER_SEEDS;
            case "prismite" -> Material.SILVERFISH_SPAWN_EGG;
            case "loomer" -> Material.RABBIT_SPAWN_EGG;
            case "raw_sifter_meat" -> Material.BEEF;
            case "cooked_sifter_meat" -> Material.COOKED_BEEF;
            case "blub_meat" -> Material.MUTTON;
            case "cooked_blub_meat" -> Material.COOKED_MUTTON;
            default -> null;
        };
        if (material == null) return null;
        String displayName = switch (id) {
            case "sift_grass" -> "Sift Grass";
            case "sift_log" -> "Sift Log";
            case "sift_leaves" -> "Sift Leaves";
            case "sift_planks" -> "Sift Planks";
            case "sift_core" -> "Heart of the Sift";
            case "prism_warden" -> "Prism Warden Spawn Egg";
            case "lumen_tulip" -> "Lumen Tulip Seeds";
            default -> title(id);
        };
        return item(material, id, displayName);
    }

    private String title(String id) {
        StringBuilder title = new StringBuilder();
        for (String word : id.split("_")) {
            if (!title.isEmpty()) title.append(' ');
            title.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return title.toString();
    }

    private void registerSmithing(Server server, String key, Material base, Material outputBase,
                                   String id, String name) {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "siftite_" + key);
        server.removeRecipe(recipeKey);
        org.bukkit.inventory.SmithingTransformRecipe recipe = new org.bukkit.inventory.SmithingTransformRecipe(
                recipeKey, item(outputBase, id, name),
                exact(item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                        "siftite_upgrade_smithing_template", "Siftite Upgrade Template")),
                new org.bukkit.inventory.RecipeChoice.MaterialChoice(base),
                org.bukkit.inventory.RecipeChoice.exactChoice(
                        item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")));
        server.addRecipe(recipe);
    }

    private void addTemplateRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "siftite_upgrade_smithing_template");
        plugin.getServer().removeRecipe(recipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey,
                item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                        "siftite_upgrade_smithing_template", "Siftite Upgrade Template"));
        recipe.addIngredient(new org.bukkit.inventory.RecipeChoice.MaterialChoice(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        recipe.addIngredient(exact(item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")));
        plugin.getServer().addRecipe(recipe);
    }

    /** Tagged Siftite materials must not be accepted by vanilla material-only recipes. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (isPluginRecipe(event.getRecipe())) {
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isSiftiteItem(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        var inventory = event.getInventory();
        var recipe = inventory.getRecipe();
        if (isPluginRecipe(recipe)) return;
        if (isSiftiteItem(inventory.getInputTemplate()) || isSiftiteItem(inventory.getInputEquipment())
                || isSiftiteItem(inventory.getInputMineral())) {
            event.setResult(null);
        }
    }

    private boolean isSiftiteItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id != null && id.startsWith("siftite_");
    }

    private boolean isPluginRecipe(org.bukkit.inventory.Recipe recipe) {
        return recipe instanceof Keyed keyed
                && keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT));
    }

    private void addShaped(String key, ItemStack result, String row1, String row2, String row3, Object... ingredients) {
        NamespacedKey recipeKey = new NamespacedKey(plugin, key);
        plugin.getServer().removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape(row1, row2, row3);
        for (int i = 0; i < ingredients.length; i += 2) {
            char symbol = (char) ingredients[i];
            Object value = ingredients[i + 1];
            if (value instanceof org.bukkit.inventory.RecipeChoice choice) recipe.setIngredient(symbol, choice);
            else recipe.setIngredient(symbol, (Material) value);
        }
        plugin.getServer().addRecipe(recipe);
    }

    private void addShapeless(String key, ItemStack result, Material... ingredients) {
        NamespacedKey recipeKey = new NamespacedKey(plugin, key);
        plugin.getServer().removeRecipe(recipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, result);
        for (Material ingredient : ingredients) recipe.addIngredient(ingredient);
        plugin.getServer().addRecipe(recipe);
    }

    /** Register a furnace smelting recipe from a tagged item to its cooked counterpart. */
    private void registerFurnish(String key, ItemStack input, ItemStack output) {
        if (input == null || output == null) return;
        NamespacedKey recipeKey = new NamespacedKey(plugin, key);
        plugin.getServer().removeRecipe(recipeKey);
        FurnaceRecipe recipe = new FurnaceRecipe(recipeKey, output,
                new RecipeChoice.ExactChoice(input), 0.35f, 10);
        plugin.getServer().addRecipe(recipe);
    }

    private org.bukkit.inventory.RecipeChoice exact(ItemStack item) {
        return org.bukkit.inventory.RecipeChoice.exactChoice(item);
    }

    private void addAttributes(ItemMeta meta, String id) {
        if (id.endsWith("_helmet") || id.endsWith("_chestplate")
                || id.endsWith("_leggings") || id.endsWith("_boots")) {
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_knockback_resistance"),
                    plugin.getConfig().getDouble("siftite.knockback-resistance", 0.1),
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR));
        } else if (id.endsWith("_sword") || id.endsWith("_axe") || id.endsWith("_spear")) {
            double targetDamage = id.endsWith("_spear")
                    ? plugin.getConfig().getDouble("siftite.spear-damage", 9.0)
                    : 8.0 + plugin.getConfig().getDouble("siftite.weapon-damage-bonus", 1.0);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_damage"),
                    Math.max(0.0, targetDamage - 8.0),
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND));
        }
        if (id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel") || id.endsWith("_hoe")) {
            meta.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_mining_speed"),
                    plugin.getConfig().getDouble("siftite.mining-speed-bonus", 0.2),
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.HAND));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }
}
