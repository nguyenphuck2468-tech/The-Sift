package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class SiftiteRecipeTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private SiftiteContent content;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("providers.strict", false);
        content = new SiftiteContent(plugin);
        content.registerRecipes();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void templateRecipeRequiresTaggedSiftiteIngot() {
        Recipe registered = server.getRecipe(
                new NamespacedKey(plugin, "siftite_upgrade_smithing_template"));
        assertInstanceOf(ShapelessRecipe.class, registered);

        boolean acceptsTaggedIngot = false;
        boolean acceptsPlainNetheriteIngot = false;
        ItemStack taggedIngot = content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
        ItemStack plainIngot = new ItemStack(Material.NETHERITE_INGOT);
        for (RecipeChoice choice : ((ShapelessRecipe) registered).getChoiceList()) {
            acceptsTaggedIngot |= choice.test(taggedIngot);
            acceptsPlainNetheriteIngot |= choice.test(plainIngot);
        }

        assertTrue(acceptsTaggedIngot);
        assertFalse(acceptsPlainNetheriteIngot);
    }

    @Test
    void strictModeRefusesVanillaTaggedItemWhenProviderIdIsMissing() {
        plugin.getConfig().set("providers.strict", true);
        plugin.getConfig().set("integrations.items.provider", "missing-provider");
        assertNull(content.item(Material.AMETHYST_SHARD, "charoite", "Charoite"));
    }

    @Test
    void datapackStyleExactChoiceRecipePassesTheCraftGuard() {
        // Simulates a recipe loaded from the bundled progression datapack: it is Keyed but
        // NOT owned by this plugin's namespace, and its ingredient ExactChoice carries the
        // tagged siftite_ingot. Without the intent check in onPrepareCraft, the guard would
        // blank out this recipe's result because the matrix contains a siftite_-tagged item.
        NamespacedKey foreignKey = new NamespacedKey("thesift_datapack_sim", "siftite_ingot_from_nuggets");
        ItemStack output = content.item(Material.IRON_INGOT, "sim_output", "Simulated Output");
        ShapelessRecipe foreignRecipe = new ShapelessRecipe(foreignKey, output);
        foreignRecipe.addIngredient(new RecipeChoice.ExactChoice(
                content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")));
        server.addRecipe(foreignRecipe);

        SiftiteContent.SiftiteGuardProbe probe =
                new SiftiteContent.SiftiteGuardProbe(new NamespacedKey(plugin, "content"));
        assertTrue(probe.recipeIntendsTaggedItems(foreignRecipe),
                "datapack recipes whose exact-choice ingredients carry our tags must be whitelisted");
        // A plain vanilla material-only recipe must still be guarded.
        ShapelessRecipe vanillaClone = new ShapelessRecipe(
                new NamespacedKey("other_plugin", "plain"), output);
        vanillaClone.addIngredient(Material.NETHERITE_INGOT);
        assertFalse(probe.recipeIntendsTaggedItems(vanillaClone),
                "material-only recipes stay guarded so tagged items cannot leak into vanilla crafts");
    }
}
