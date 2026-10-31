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
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SiftiteContent implements Listener {
    private static final java.util.Map<String, Boolean> ITEM_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> MISSING_ITEM_WARNINGS = ConcurrentHashMap.newKeySet();
    private final JavaPlugin plugin;
    private final NamespacedKey idKey;
    private final ExternalItemBridge externalItems;
    private final java.util.Set<NamespacedKey> ownedRecipeKeys = ConcurrentHashMap.newKeySet();

    public SiftiteContent(JavaPlugin plugin) {
        this(plugin, new ExternalItemBridge(plugin));
    }

    /** Shared wiring: reuse one ExternalItemBridge (and its provider caches) across registries. */
    SiftiteContent(JavaPlugin plugin, ExternalItemBridge externalItems) {
        this.plugin = Objects.requireNonNull(plugin);
        this.idKey = new NamespacedKey(plugin, "content");
        this.externalItems = Objects.requireNonNull(externalItems);
    }

    /** Unregisters exactly the recipes registered by this plugin instance. */
    public void removeRecipes() {
        Server server = plugin.getServer();
        // Do not sweep the entire namespace: datapacks or other integrations may legitimately
        // register recipes under thesift. Track the exact keys this instance added instead.
        for (NamespacedKey key : ownedRecipeKeys) {
            server.removeRecipe(key);
        }
        ownedRecipeKeys.clear();
    }

    public void registerRecipes() {
        Server server = plugin.getServer();
        for (String key : java.util.List.of("charoite", "siftite_nugget", "siftite_ingot",
                "siftite_upgrade_smithing_template", "smelt_raw_sifter_meat", "smelt_blub_meat",
                "blub_campfire")) {
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
        // Campfire parity for the Blub food chain. Registered in Java (not the bundled
        // datapack) with an ExactChoice so ONLY the tagged blub_meat cooks here — a
        // material-only datapack recipe on minecraft:mutton would let plain vanilla mutton
        // produce Cooked Blub Meat and race the vanilla campfire recipe.
        registerCampfire("blub_campfire",
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
        // Reference-mod recipes: an ingot breaks back down into nine nuggets, and the
        // spear is a nugget tip over an ingot shaft on a stick.
        if (plugin.getConfig().getBoolean("recipes.enabled", true)) {
            ItemStack ingot = item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
            ItemStack nugget = item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget");
            if (ingot != null && nugget != null) {
                NamespacedKey nuggetsKey = new NamespacedKey(plugin, "siftite_nugget_from_ingot");
                plugin.getServer().removeRecipe(nuggetsKey);
                ItemStack nuggetsOut = nugget.clone();
                nuggetsOut.setAmount(9);
                ShapelessRecipe nuggets = new ShapelessRecipe(nuggetsKey, nuggetsOut);
                nuggets.addIngredient(exact(ingot));
                plugin.getServer().addRecipe(nuggets);
                ownedRecipeKeys.add(nuggetsKey);

                if (plugin.getConfig().getBoolean("content.siftite_spear", true)) {
                    ItemStack spear = item(Material.NETHERITE_SWORD, "siftite_spear", "Siftite Spear");
                    if (spear != null) {
                        NamespacedKey spearKey = new NamespacedKey(plugin, "siftite_spear_craft");
                        plugin.getServer().removeRecipe(spearKey);
                        ShapedRecipe spearRecipe = new ShapedRecipe(spearKey, spear);
                        spearRecipe.shape("N", "I", "S");
                        spearRecipe.setIngredient('N', exact(nugget));
                        spearRecipe.setIngredient('I', exact(ingot));
                        spearRecipe.setIngredient('S', Material.STICK);
                        plugin.getServer().addRecipe(spearRecipe);
                        ownedRecipeKeys.add(spearKey);
                    }
                }
            }
        }
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

    /** The content id stamped on this stack's PDC, or null when the stack is not Sift content. */
    public String idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
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
            case "spire_tyrant" -> Material.RAVAGER_SPAWN_EGG; // crimson Sifter-variant boss
            case "frost_stalker" -> Material.STRAY_SPAWN_EGG;
            case "sunspore" -> Material.MOOSHROOM_SPAWN_EGG;
            case "drifter" -> Material.ENDERMAN_SPAWN_EGG;
            case "shardling" -> Material.ENDERMITE_SPAWN_EGG;
            case "prism_warden" -> Material.WARDEN_SPAWN_EGG;
            case "lumen_tulip", "sculkflower_seeds" -> Material.TORCHFLOWER_SEEDS;
            case "sculkflower" -> Material.POPPY;
            case "prismite" -> Material.SILVERFISH_SPAWN_EGG;
            case "loomer" -> Material.RABBIT_SPAWN_EGG;
            // Remaining spec mobs get vanilla stand-in spawn eggs so every id in
            // integrations/content-spec.json resolves through forId().
            case "echo_golem" -> Material.IRON_GOLEM_SPAWN_EGG;
            case "singer" -> Material.ALLAY_SPAWN_EGG;
            case "dark_sniffer" -> Material.SNIFFER_SPAWN_EGG;
            case "rift_walker" -> Material.ENDERMAN_SPAWN_EGG;
            case "sift_wastes_stalker" -> Material.WITCH_SPAWN_EGG;
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
        ownedRecipeKeys.add(recipeKey);
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
        ownedRecipeKeys.add(recipeKey);
    }

    /** Tagged Siftite materials must not be accepted by recipes that don't declare them. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        blockTaggedItemsOutsideExactChoices(event.getRecipe(), event.getInventory().getMatrix(),
                () -> event.getInventory().setResult(null));
    }

    /**
     * Core per-slot guard, extracted as a pure function so tests can assert the exact
     * decision without depending on MockBukkit's crafting pipeline.
     *
     * Semantics (review P0/P2): a tagged siftite item may only pass through a FOREIGN
     * recipe when some ExactChoice declared BY THAT RECIPE matches it — same material
     * AND carrying the plugin's content tag. MaterialChoice/TagChoice compare bare
     * material/type only, so a siftite_ingot dropped into a vanilla NETHERITE_INGOT
     * craft satisfies NO declared exact choice and the result gets blanked: no leak
     * vector. (The old `choice.test(ingredient)` form was unsound: MaterialChoice.test()
     * returns true for any stack of the listed material, tagged or not.)
     *
     * Recipes owned by this plugin are whitelisted wholesale — they were authored with
     * exact choices around their own tagged items. The guard NEVER writes nulls back
     * into the matrix: PrepareItemCraftEvent fires for every candidate recipe matching
     * the grid, and clearing slots would physically delete the player's ingredients
     * whenever an unrelated foreign recipe is evaluated (review P1). Only the RESULT
     * is blanked, which prevents the undesired craft from producing anything while
     * keeping items intact.
     */
    static boolean blockTaggedItemsOutsideExactChoices(org.bukkit.inventory.Recipe recipe,
                                                       ItemStack[] matrix, Runnable blockAction) {
        if (matrix == null) return false;
        // Only keyed crafting recipes can be ownership-checked. An unkeyed recipe has no
        // namespace to compare against, so it is conservatively treated as foreign and the
        // guard still applies — never silently skip because isOwnRecipe() returned false.
        if (recipe instanceof Keyed && isOwnRecipe(recipe)) return false;
        List<ItemStack> exactOptions = exactChoiceOptions(recipe);
        boolean blocked = false;
        for (ItemStack ingredient : matrix) {
            if (!isSiftiteItemStatic(ingredient)) continue;
            if (exactOptions.stream().anyMatch(option -> option.isSimilar(ingredient))) continue;
            blocked = true;
        }
        if (blocked) blockAction.run();
        return blocked;
    }

    /** Every concrete ItemStack an ExactChoice of this recipe would accept. */
    static List<ItemStack> exactChoiceOptions(org.bukkit.inventory.Recipe recipe) {
        List<ItemStack> options = new ArrayList<>();
        for (RecipeChoice choice : recipeChoices(recipe)) {
            if (choice instanceof RecipeChoice.ExactChoice exact) {
                options.addAll(exact.getChoices());
            } else if (choice instanceof RecipeChoice.PredicateChoice predicate) {
                // A PredicateChoice with a base stack documents intent for a specific item;
                // include it so predicate-based datapack-style recipes whitelist correctly.
                ItemStack base = predicate.getItemStack();
                if (base != null) options.add(base);
            }
        }
        return options;
    }

    /** Every RecipeChoice a keyed recipe declares, using the Paper 26.3 recipe APIs. */
    static List<RecipeChoice> recipeChoices(org.bukkit.inventory.Recipe recipe) {
        List<RecipeChoice> choices = new ArrayList<>();
        try {
            if (recipe instanceof ShapedRecipe shaped) {
                for (Map.Entry<Character, RecipeChoice> entry : shaped.getChoiceMap().entrySet()) {
                    choices.add(entry.getValue());
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                choices.addAll(shapeless.getChoiceList());
            } else if (recipe instanceof CookingRecipe<?> cooking) {
                // Covers FurnaceRecipe, CampfireRecipe, SmokingRecipe and BlastFurnaceRecipe:
                // these expose their input through CookingRecipe#getInputChoice(), not a
                // getIngredient() accessor (which does not exist on the Paper API).
                RecipeChoice input = cooking.getInputChoice();
                if (input != null) choices.add(input);
            }
        } catch (RuntimeException exception) {
            return List.of();
        }
        return choices;
    }

    /**
     * Leak heuristic (review P2): true when a recipe declares ONLY bare MaterialChoices
     * while still accepting some siftite-tagged stand-in item — i.e. a vanilla/material-only
     * recipe that siftite items could sneak into (plain NETHERITE_INGOT crafts, other
     * plugins' recipes on shared materials). Recipes that declare any non-material choice
     * (ExactChoice, PredicateChoice, TagChoice) were authored around specific items and rely
     * on exact matching instead. This predicate documents/pins the leak surface; the actual
     * enforcement is the per-slot ownership-aware guard in onPrepareCraft, which blanks any
     * tagged slot that no declared ExactChoice of a foreign recipe matches exactly.
     */
    static boolean recipeIntendsTaggedItems(org.bukkit.inventory.Recipe recipe, NamespacedKey idKey) {
        List<RecipeChoice> choices = recipeChoices(recipe);
        if (choices.isEmpty()) return false;
        for (RecipeChoice choice : choices) {
            if (choice == null) continue;
            // Any non-material choice means the recipe matches specific items exactly —
            // it is not a material-only leak vector.
            if (!(choice instanceof RecipeChoice.MaterialChoice)) return false;
        }
        // Material-only recipe: would ANY of its choices swallow a siftite-tagged item?
        for (RecipeChoice choice : choices) {
            if (choice != null && choice.test(taggedStandIn(choice, idKey))) return true;
        }
        return false;
    }

    /**
     * Builds a siftite-tagged stand-in for one declared choice: same material the choice
     * accepts (first candidate of a MaterialChoice), carrying the {@code siftite_probe}
     * content id, so we can ask the choice directly whether it would let a tagged item
     * through. Falls back to AIR (test returns false) when the choice names no material.
     */
    private static ItemStack taggedStandIn(RecipeChoice choice, NamespacedKey idKey) {
        Material base = Material.AIR;
        if (choice instanceof RecipeChoice.MaterialChoice material) {
            Collection<? extends Material> options = material.getChoices();
            if (options != null && !options.isEmpty()) base = options.iterator().next();
        }
        ItemStack probe = new ItemStack(base);
        ItemMeta meta = probe.getItemMeta();
        if (base.isItem() && meta != null) {
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, "siftite_probe");
            probe.setItemMeta(meta);
        }
        return probe;
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

    /** Static twin of {@link #isSiftiteItem} for the pure guard function (same PDC contract). */
    private static boolean isSiftiteItemStatic(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        try {
            String id = item.getItemMeta().getPersistentDataContainer()
                    .get(SIFTITE_ID_KEY, PersistentDataType.STRING);
            return id != null && id.startsWith("siftite_");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean isPluginRecipe(org.bukkit.inventory.Recipe recipe) {
        // Same ownership semantics as the craft guard: NamespacedKey namespaces are always
        // lowercase, so compare against PLUGIN_NAMESPACE (plugin.getName() lower-cased).
        return isOwnRecipe(recipe);
    }

    /**
     * Namespace under which this plugin registers every recipe. NamespacedKey enforces
     * lowercase namespaces, so ownership comparisons MUST lower-case the plugin display
     * name ("TheSift" -> "thesift"). The previous un-lowercased comparison could never
     * match, which made isOwnRecipe always false: the ghost-recipe sweep removed nothing
     * and every plugin recipe was wrongly treated as foreign (review finding on 1172ce4).
     */
    static final String PLUGIN_NAMESPACE = "thesift";
    /** Canonical content-tag key shared by instance and static guard paths. */
    static final NamespacedKey SIFTITE_ID_KEY = safeKey(PLUGIN_NAMESPACE, "content");

    private static NamespacedKey safeKey(String namespace, String key) {
        try {
            return new NamespacedKey(namespace, key);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * True only for recipes registered by THIS plugin instance. Ownership is proven via the
     * recipe's own NamespacedKey: every addShaped/addShapeless/registerFurnish/registerCampfire
     * call builds its key with {@code new NamespacedKey(plugin, ...)} — plugin.getName()
     * lower-cased equals {@link #PLUGIN_NAMESPACE}.
     */
    static boolean isOwnRecipe(org.bukkit.inventory.Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) return false;
        try {
            return keyed.getKey().getNamespace().equals(PLUGIN_NAMESPACE);
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
        ownedRecipeKeys.add(recipeKey);
    }

    private void addShapeless(String key, ItemStack result, Material... ingredients) {
        NamespacedKey recipeKey = new NamespacedKey(plugin, key);
        plugin.getServer().removeRecipe(recipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, result);
        for (Material ingredient : ingredients) recipe.addIngredient(ingredient);
        plugin.getServer().addRecipe(recipe);
        ownedRecipeKeys.add(recipeKey);
    }

    /** Register a furnace smelting recipe from a tagged item to its cooked counterpart. */
    private void registerFurnish(String key, ItemStack input, ItemStack output) {
        // Config kill-switch: with recipes.enabled=false the food chain still works through the
        // FurnaceSmeltEvent fallback listener in SiftEcologyManager instead of a real recipe.
        if (!plugin.getConfig().getBoolean("recipes.enabled", true)) return;
        if (input == null || output == null) return;
        NamespacedKey recipeKey = new NamespacedKey(plugin, key);
        plugin.getServer().removeRecipe(recipeKey);
        FurnaceRecipe recipe = new FurnaceRecipe(recipeKey, output,
                RecipeChoice.exactChoice(input), 0.35f, 10);
        plugin.getServer().addRecipe(recipe);
        ownedRecipeKeys.add(recipeKey);
    }

    /** Campfire twin of {@link #registerFurnish}: ExactChoice input so vanilla mutton cannot cook here. */
    private void registerCampfire(String key, ItemStack input, ItemStack output) {
        if (!plugin.getConfig().getBoolean("recipes.enabled", true)) return;
        if (input == null || output == null) return;
        NamespacedKey recipeKey = new NamespacedKey(plugin, key);
        plugin.getServer().removeRecipe(recipeKey);
        org.bukkit.inventory.CampfireRecipe recipe = new org.bukkit.inventory.CampfireRecipe(
                recipeKey, output, RecipeChoice.exactChoice(input), 0.35f, 600);
        plugin.getServer().addRecipe(recipe);
        ownedRecipeKeys.add(recipeKey);
    }

    private org.bukkit.inventory.RecipeChoice exact(ItemStack item) {
        return org.bukkit.inventory.RecipeChoice.exactChoice(item);
    }

    private void addAttributes(ItemMeta meta, String id) {
        if (id.endsWith("_helmet") || id.endsWith("_chestplate")
                || id.endsWith("_leggings") || id.endsWith("_boots")) {
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_knockback_resistance"),
                    plugin.getConfig().getDouble("siftite.knockback-resistance", 0.15),
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
