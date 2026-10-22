package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

public final class SiftiteContent {
    private final JavaPlugin plugin;
    private final NamespacedKey idKey;

    public SiftiteContent(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.idKey = new NamespacedKey(plugin, "content");
    }

    public void registerRecipes() {
        Server server = plugin.getServer();
        addShaped("charoite", item(Material.AMETHYST_SHARD, "charoite", "Charoite"),
                " C ", "CAC", " C ", 'C', Material.AMETHYST_SHARD, 'A', Material.AMETHYST_BLOCK);
        addShapeless("siftite_nugget", item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget"),
                Material.IRON_NUGGET, Material.AMETHYST_SHARD);
        addShaped("siftite_ingot", item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot"),
                "NNN", "NNN", "NNN", 'N', exact(item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget")));
        registerSmithing(server, "sword", Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                "siftite_sword", "Siftite Sword");
        registerSmithing(server, "axe", Material.DIAMOND_AXE, Material.NETHERITE_AXE,
                "siftite_axe", "Siftite Axe");
        registerSmithing(server, "pickaxe", Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
                "siftite_pickaxe", "Siftite Pickaxe");
        registerSmithing(server, "shovel", Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
                "siftite_shovel", "Siftite Shovel");
        registerSmithing(server, "hoe", Material.DIAMOND_HOE, Material.NETHERITE_HOE,
                "siftite_hoe", "Siftite Hoe");
        registerSmithing(server, "helmet", Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
                "siftite_helmet", "Siftite Helmet");
        registerSmithing(server, "chestplate", Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
                "siftite_chestplate", "Siftite Chestplate");
        registerSmithing(server, "leggings", Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
                "siftite_leggings", "Siftite Leggings");
        registerSmithing(server, "boots", Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
                "siftite_boots", "Siftite Boots");
    }

    public ItemStack item(Material material, String id, String name) {
        ItemStack result = new ItemStack(material);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName("§d" + name);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (id.startsWith("siftite_") && !id.equals("siftite_upgrade_smithing_template")) {
            addAttributes(meta, id);
        }
        result.setItemMeta(meta);
        return result;
    }

    public boolean is(ItemStack stack, String id) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return id.equals(stack.getItemMeta().getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING));
    }

    public ItemStack forId(String id) {
        return switch (id) {
            case "charoite" -> item(Material.AMETHYST_SHARD, id, "Charoite");
            case "charoite_ore" -> item(Material.AMETHYST_BLOCK, id, "Charoite Ore");
            case "siftite_nugget" -> item(Material.IRON_NUGGET, id, "Siftite Nugget");
            case "siftite_ingot" -> item(Material.NETHERITE_INGOT, id, "Siftite Ingot");
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
    }

    private void registerSmithing(Server server, String key, Material base, Material outputBase,
                                   String id, String name) {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "siftite_" + key);
        server.removeRecipe(recipeKey);
        org.bukkit.inventory.SmithingTransformRecipe recipe = new org.bukkit.inventory.SmithingTransformRecipe(
                recipeKey, item(outputBase, id, name),
                new org.bukkit.inventory.RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                new org.bukkit.inventory.RecipeChoice.MaterialChoice(base),
                new org.bukkit.inventory.RecipeChoice.ExactChoice(item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")));
        server.addRecipe(recipe);
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

    private org.bukkit.inventory.RecipeChoice exact(ItemStack item) {
        return new org.bukkit.inventory.RecipeChoice.ExactChoice(item);
    }

    private void addAttributes(ItemMeta meta, String id) {
        Attribute attribute = id.contains("helmet") || id.contains("chestplate")
                || id.contains("leggings") || id.contains("boots")
                ? Attribute.ARMOR_TOUGHNESS : Attribute.ATTACK_DAMAGE;
        double amount = attribute == Attribute.ARMOR_TOUGHNESS
                ? plugin.getConfig().getDouble("siftite.armor-toughness", 3.0)
                : plugin.getConfig().getDouble("siftite.spear-damage", 9.0);
        meta.addAttributeModifier(attribute, new AttributeModifier(
                new NamespacedKey(plugin, id + "_attribute"), amount,
                AttributeModifier.Operation.ADD_NUMBER));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }
}
