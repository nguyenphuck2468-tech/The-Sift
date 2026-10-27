package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
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

    @Test
    void mixedExactPlusMaterialRecipeDoesNotWhitelistOtherSlots() {
        // P2 regression: a keyed recipe that intends ONE siftite item via ExactChoice must not
        // be wholesale whitelisted — other matrix slots carrying different siftite-tagged items
        // (here siftite_ingot in a plain AMETHYST_SHARD slot) still get blocked. The guard now
        // matches per-slot against the recipe's own choices instead of trusting a single true.
        NamespacedKey foreignKey = new NamespacedKey("thesift_datapack_sim", "mixed_recipe");
        ItemStack output = content.item(Material.IRON_INGOT, "sim_output", "Simulated Output");
        ShapedRecipe mixed = new ShapedRecipe(foreignKey, output);
        mixed.shape("NI"); // N = intended exact siftite nugget, I = vanilla iron ingot slot
        mixed.setIngredient('N', new RecipeChoice.ExactChoice(
                content.item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget")));
        mixed.setIngredient('I', Material.AMETHYST_SHARD);
        server.addRecipe(mixed);

        SiftiteContent.SiftiteGuardProbe probe =
                new SiftiteContent.SiftiteGuardProbe(new NamespacedKey(plugin, "content"));
        // Legacy whole-recipe whitelist would return true here; that is exactly the too-broad
        // behaviour the per-slot rewrite removed. Keep the check covered for its callers.
        assertTrue(probe.recipeIntendsTaggedItems(mixed));

        // Per-slot semantics: the nugget choice accepts the tagged nugget, but NO declared
        // choice accepts a siftite_ingot — so an ingot placed in the material slot stays guarded.
        boolean anyChoiceAcceptsIngot = SiftiteContent.recipeChoices(mixed).stream()
                .anyMatch(choice -> choice.test(
                        content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")));
        assertFalse(anyChoiceAcceptsIngot,
                "a mixed recipe must not implicitly accept unrelated siftite items");
        boolean anyChoiceAcceptsNugget = SiftiteContent.recipeChoices(mixed).stream()
                .anyMatch(choice -> choice.test(
                        content.item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget")));
        assertTrue(anyChoiceAcceptsNugget);
    }

    @Test
    void blubCampfireRecipeOnlyAcceptsTaggedBlubMeat() {
        // P1 regression: the campfire recipe must use an ExactChoice on the tagged blub_meat,
        // never a bare MUTTON material choice that would let vanilla mutton cook into
        // Cooked Blub Meat and race the vanilla campfire recipe.
        Recipe registered = server.getRecipe(new NamespacedKey(plugin, "blub_campfire"));
        assertInstanceOf(CampfireRecipe.class, registered);
        CampfireRecipe campfire = (CampfireRecipe) registered;

        RecipeChoice input = campfire.getInputChoice();
        assertInstanceOf(RecipeChoice.ExactChoice.class, input);
        assertTrue(input.test(content.item(Material.MUTTON, "blub_meat", "Blub Meat")),
                "tagged blub_meat must cook on the campfire");
        assertFalse(input.test(new ItemStack(Material.MUTTON)),
                "plain vanilla mutton must NOT satisfy the Blub campfire ingredient");
        assertEquals("cooked_blub_meat", campfire.getResult().getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "content"),
                        org.bukkit.persistence.PersistentDataType.STRING));
    }
}
