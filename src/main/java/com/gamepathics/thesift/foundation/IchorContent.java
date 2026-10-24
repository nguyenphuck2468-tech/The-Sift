package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

public final class IchorContent {
    public static final String ICHOR_BOTTLE_ID = "ichor_bottle";
    public static final String ICHOR_GLASS_ID = "ichor_glass";
    public static final String ICHOR_GLASS_PANE_ID = "ichor_glass_pane";
    public static final String ICHOR_SNOW_ID = "ichor_snow";
    public static final String ICHOR_SNOW_BLOCK_ID = "ichor_snow_block";

    private final JavaPlugin plugin;
    private final ExternalItemBridge externalItems;
    private final NamespacedKey contentKey;
    private final NamespacedKey bottleRecipeKey;
    private final NamespacedKey paneRecipeKey;

    public IchorContent(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.externalItems = new ExternalItemBridge(plugin);
        this.contentKey = new NamespacedKey(plugin, "content");
        this.bottleRecipeKey = new NamespacedKey(plugin, "ichor_glass");
        this.paneRecipeKey = new NamespacedKey(plugin, "ichor_glass_pane");
    }

    public void registerRecipes() {
        Server server = plugin.getServer();
        server.removeRecipe(bottleRecipeKey);
        server.removeRecipe(paneRecipeKey);

        Material glass = configuredMaterial("ichor.glass-material", Material.GLASS);
        Material pane = configuredMaterial("ichor.glass-pane-material", Material.GLASS_PANE);

        ShapedRecipe glassRecipe = new ShapedRecipe(bottleRecipeKey, create(glass, ICHOR_GLASS_ID, "Ichor Glass"));
        glassRecipe.shape("IGI", "GIG", "IGI");
        glassRecipe.setIngredient('I', Material.GLASS_BOTTLE);
        glassRecipe.setIngredient('G', Material.GLASS);
        server.addRecipe(glassRecipe);

        ShapedRecipe paneRecipe = new ShapedRecipe(paneRecipeKey,
                create(pane, ICHOR_GLASS_PANE_ID, "Ichor Glass Pane"));
        paneRecipe.shape("GGG", "GGG");
        paneRecipe.setIngredient('G', new RecipeChoice.ExactChoice(ichorGlass()));
        server.addRecipe(paneRecipe);
    }

    public ItemStack ichorBottle() {
        Material material = configuredMaterial("ichor.bottle-material", Material.GLASS_BOTTLE);
        return create(material, ICHOR_BOTTLE_ID, "Ichor Bottle");
    }

    public ItemStack ichorGlass() {
        return create(configuredMaterial("ichor.glass-material", Material.GLASS),
                ICHOR_GLASS_ID, "Ichor Glass");
    }

    public ItemStack ichorSnow() {
        return create(configuredMaterial("ichor.snow-material", Material.SNOW),
                ICHOR_SNOW_ID, "Ichor Snow");
    }

    public ItemStack ichorSnowBlock() {
        return create(configuredMaterial("ichor.snow-block-material", Material.SNOW_BLOCK),
                ICHOR_SNOW_BLOCK_ID, "Ichor Snow Block");
    }

    public ItemStack create(Material material, String id, String name) {
        ItemStack item = externalItems.create(id);
        boolean providerItem = item != null;
        if (item == null) item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (!providerItem) meta.setDisplayName("§d" + name);
        meta.getPersistentDataContainer().set(contentKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public boolean is(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(contentKey, PersistentDataType.STRING);
        return id.equals(value);
    }

    public boolean isIchorEnabled() {
        return plugin.getConfig().getBoolean("ichor.enabled", true);
    }

    private Material configuredMaterial(String path, Material fallback) {
        String name = plugin.getConfig().getString(path, fallback.name());
        Material material = Registry.MATERIAL.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return material == null ? fallback : material;
    }
}
