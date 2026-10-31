package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class IchorContent {
    private static final Set<String> MISSING_ITEM_WARNINGS = ConcurrentHashMap.newKeySet();
    public static final String ICHOR_BOTTLE_ID = "ichor_bottle";
    public static final String ICHOR_GLASS_ID = "ichor_glass";
    public static final String ICHOR_GLASS_PANE_ID = "ichor_glass_pane";
    public static final String ICHOR_SNOW_ID = "ichor_snow";
    public static final String ICHOR_SNOW_BLOCK_ID = "ichor_snow_block";
    public static final String ICHOR_BUCKET_ID = "ichor_bucket";
    public static final String ICHOR_SHARD_ID = "ichor_shard";
    public static final String ICHOR_SNOWBALL_ID = "ichor_snowball";

    private final JavaPlugin plugin;
    private final ExternalItemBridge externalItems;
    private final NamespacedKey contentKey;
    private final NamespacedKey bottleRecipeKey;
    private final NamespacedKey paneRecipeKey;
    private final NamespacedKey bucketRecipeKey;
    private final NamespacedKey shardRecipeKey;
    private final NamespacedKey snowballRecipeKey;

    public IchorContent(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.externalItems = new ExternalItemBridge(plugin);
        this.contentKey = new NamespacedKey(plugin, "content");
        this.bottleRecipeKey = new NamespacedKey(plugin, "ichor_glass");
        this.paneRecipeKey = new NamespacedKey(plugin, "ichor_glass_pane");
        this.bucketRecipeKey = new NamespacedKey(plugin, "ichor_bucket");
        this.shardRecipeKey = new NamespacedKey(plugin, "ichor_shard");
        this.snowballRecipeKey = new NamespacedKey(plugin, "ichor_snowball");
    }

    /** Unregisters the Ichor recipes owned by this plugin (used from onDisable). */
    public void removeRecipes() {
        plugin.getServer().removeRecipe(bottleRecipeKey);
        plugin.getServer().removeRecipe(paneRecipeKey);
        plugin.getServer().removeRecipe(bucketRecipeKey);
        plugin.getServer().removeRecipe(shardRecipeKey);
        plugin.getServer().removeRecipe(snowballRecipeKey);
    }

    public void registerRecipes() {
        Server server = plugin.getServer();
        server.removeRecipe(bottleRecipeKey);
        server.removeRecipe(paneRecipeKey);
        server.removeRecipe(bucketRecipeKey);
        server.removeRecipe(shardRecipeKey);
        server.removeRecipe(snowballRecipeKey);
        if (!isIchorEnabled()) return;
        if (!plugin.getConfig().getBoolean("recipes.enabled", true)) return;
        if (plugin.getConfig().getBoolean("providers.strict", true)
                && !new ProviderReadiness(plugin).itemsReady()) return;

        Material glass = configuredMaterial("ichor.glass-material", Material.GLASS);
        Material pane = configuredMaterial("ichor.glass-pane-material", Material.GLASS_PANE);

        if (isContentEnabled("ichor_glass")) {
            ShapedRecipe glassRecipe = new ShapedRecipe(bottleRecipeKey, create(glass, ICHOR_GLASS_ID, "Ichor Glass"));
            glassRecipe.shape("IGI", "GIG", "IGI");
            glassRecipe.setIngredient('I', Material.GLASS_BOTTLE);
            glassRecipe.setIngredient('G', Material.GLASS);
            server.addRecipe(glassRecipe);
        }

        if (isContentEnabled("ichor_glass_pane") && isContentEnabled("ichor_glass")) {
            ShapedRecipe paneRecipe = new ShapedRecipe(paneRecipeKey,
                    create(pane, ICHOR_GLASS_PANE_ID, "Ichor Glass Pane"));
            paneRecipe.shape("GGG", "GGG");
            paneRecipe.setIngredient('G', RecipeChoice.exactChoice(ichorGlass()));
            server.addRecipe(paneRecipe);
        }

        if (isContentEnabled("ichor_bucket")) {
            ShapelessRecipe bucketRecipe = new ShapelessRecipe(bucketRecipeKey,
                    ichorBucket());
            bucketRecipe.addIngredient(Material.BUCKET);
            bucketRecipe.addIngredient(RecipeChoice.exactChoice(ichorBottle()));
            bucketRecipe.addIngredient(RecipeChoice.exactChoice(ichorBottle()));
            server.addRecipe(bucketRecipe);
        }

        if (isContentEnabled("ichor_shard") && isContentEnabled("ichor_snowball")) {
            ShapelessRecipe shardRecipe = new ShapelessRecipe(shardRecipeKey, ichorShard());
            shardRecipe.addIngredient(RecipeChoice.exactChoice(ichorSnowball()));
            shardRecipe.addIngredient(RecipeChoice.exactChoice(ichorSnowball()));
            shardRecipe.addIngredient(RecipeChoice.exactChoice(ichorSnowball()));
            shardRecipe.addIngredient(RecipeChoice.exactChoice(ichorSnowball()));
            server.addRecipe(shardRecipe);
        }

        if (isContentEnabled("ichor_snowball") && isContentEnabled("ichor_bottle")) {
            ShapelessRecipe snowballRecipe = new ShapelessRecipe(snowballRecipeKey, ichorSnowball(2));
            snowballRecipe.addIngredient(Material.SNOWBALL);
            snowballRecipe.addIngredient(RecipeChoice.exactChoice(ichorBottle()));
            server.addRecipe(snowballRecipe);
        }
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

    /** An iron bucket filled with resonant Ichor; right-clicking water places the Ichor spring. */
    public ItemStack ichorBucket() {
        return create(configuredMaterial("ichor.bucket-material", Material.WATER_BUCKET),
                ICHOR_BUCKET_ID, "Ichor Bucket");
    }

    /** Solidified Ichor: four Ichor Snowballs pressed into one shard. */
    public ItemStack ichorShard() {
        return create(configuredMaterial("ichor.shard-material", Material.PRISMARINE_SHARD),
                ICHOR_SHARD_ID, "Ichor Shard");
    }

    public ItemStack ichorSnowball() {
        return ichorSnowball(1);
    }

    public ItemStack ichorSnowball(int amount) {
        ItemStack snowball = create(Material.SNOWBALL, ICHOR_SNOWBALL_ID, "Ichor Snowball");
        if (snowball != null && amount > 1) snowball.setAmount(amount);
        return snowball;
    }

    public ItemStack create(Material material, String id, String name) {
        ItemStack item = externalItems.create(id);
        boolean providerItem = item != null;
        if (item == null && plugin.getConfig().getBoolean("providers.strict", true)) {
            if (MISSING_ITEM_WARNINGS.add(id)) plugin.getLogger().warning(
                    "Strict provider mode refused vanilla fallback for item ID '" + id
                            + "'. Run /sift doctor and install a provider definition.");
            return null;
        }
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
        return plugin.getConfig().getBoolean("ichor.enabled", true)
                && plugin.getConfig().getBoolean("content.ichor", true);
    }

    public boolean isContentEnabled(String id) {
        return isIchorEnabled() && plugin.getConfig().getBoolean("content." + id, true);
    }

    private Material configuredMaterial(String path, Material fallback) {
        String name = plugin.getConfig().getString(path, fallback.name());
        Material material = Registry.MATERIAL.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        return material == null ? fallback : material;
    }
}
