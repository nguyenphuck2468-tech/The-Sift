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
        // NOT owned by this plugin, and its ingredient ExactChoice carries the tagged
        // siftite_ingot. The guard must not blank out this recipe's result: because the
        // tagged item satisfies the recipe's own declared choice, the per-slot check in
        // onPrepareCraft leaves the matrix untouched (verified here through MockBukkit's
        // CraftInventoryView-based crafting inventory, which runs PrepareItemCraftEvent).
        NamespacedKey foreignKey = new NamespacedKey("thesift_datapack_sim", "siftite_ingot_from_nuggets");
        ItemStack output = content.item(Material.IRON_INGOT, "sim_output", "Simulated Output");
        ShapelessRecipe foreignRecipe = new ShapelessRecipe(foreignKey, output);
        ItemStack taggedIngot = content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
        foreignRecipe.addIngredient(new RecipeChoice.ExactChoice(taggedIngot));
        server.addRecipe(foreignRecipe);

        NamespacedKey idKey = new NamespacedKey(plugin, "content");
        assertFalse(SiftiteContent.recipeIntendsTaggedItems(foreignRecipe, idKey),
                "recipes with deliberate non-material choices are left to Bukkit matching, not the leak heuristic");

        org.bukkit.inventory.CraftingInventory grid =
                (org.bukkit.inventory.CraftingInventory) server.createInventory(
                        null, org.bukkit.inventory.InventoryType.WORKBENCH, "sim");
        grid.setMatrix(new ItemStack[]{taggedIngot});
        server.getScheduler().performTicks(1);
        assertNotNull(grid.getResult(), "exact-choice datapack recipe must produce a result");
        assertEquals("sim_output", grid.getResult().getItemMeta().getPersistentDataContainer()
                .get(idKey, org.bukkit.persistence.PersistentDataType.STRING),
                "tagged siftite ingot satisfying the recipe's ExactChoice must craft, not be blocked");

        // A plain vanilla material-only recipe must still be flagged by the leak heuristic:
        // its MaterialChoice compares bare material type, so a siftite-tagged stand-in of
        // the same material slips in unless the per-slot guard blocks it at craft time.
        ShapelessRecipe vanillaClone = new ShapelessRecipe(
                new NamespacedKey("other_plugin", "plain"), output);
        vanillaClone.addIngredient(Material.NETHERITE_INGOT);
        assertTrue(SiftiteContent.recipeIntendsTaggedItems(vanillaClone, idKey),
                "material-only recipes that would accept a tagged stand-in stay flagged for the guard");

        // End-to-end P0 regression: the tagged ingot in the vanilla-shaped NETHERITE_INGOT
        // craft gets blanked by the guard, so no netherite ingot may ever come out.
        org.bukkit.inventory.CraftingInventory leaky =
                (org.bukkit.inventory.CraftingInventory) server.createInventory(
                        null, org.bukkit.inventory.InventoryType.WORKBENCH, "leak");
        leaky.setMatrix(new ItemStack[]{
                taggedIngot, taggedIngot, taggedIngot,
                taggedIngot, taggedIngot, taggedIngot,
                taggedIngot, taggedIngot, taggedIngot});
        server.getScheduler().performTicks(1);
        assertNull(leaky.getResult(),
                "a siftite-tagged item must never satisfy a vanilla material-only recipe");
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
        mixed.shape("NI"); // N = intended exact siftite nugget, I = amethyst shard slot
        ItemStack taggedNugget = content.item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget");
        mixed.setIngredient('N', new RecipeChoice.ExactChoice(taggedNugget));
        mixed.setIngredient('I', Material.AMETHYST_SHARD);
        server.addRecipe(mixed);

        NamespacedKey idKey = new NamespacedKey(plugin, "content");
        // Mixed choices are hand-authored too — no wholesale whitelist via the leak heuristic.
        assertFalse(SiftiteContent.recipeIntendsTaggedItems(mixed, idKey));

        // Per-slot semantics: the nugget choice accepts the tagged nugget, but NO declared
        // choice accepts a siftite_ingot — so an ingot placed in the material slot stays guarded.
        boolean anyChoiceAcceptsIngot = SiftiteContent.recipeChoices(mixed).stream()
                .anyMatch(choice -> choice.test(
                        content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")));
        assertFalse(anyChoiceAcceptsIngot,
                "a mixed recipe must not implicitly accept unrelated siftite items");
        boolean anyChoiceAcceptsNugget = SiftiteContent.recipeChoices(mixed).stream()
                .anyMatch(choice -> choice.test(taggedNugget));
        assertTrue(anyChoiceAcceptsNugget);

        // End-to-end: correct ingredients craft fine...
        org.bukkit.inventory.CraftingInventory good =
                (org.bukkit.inventory.CraftingInventory) server.createInventory(
                        null, org.bukkit.inventory.InventoryType.WORKBENCH, "mixed-good");
        good.setMatrix(new ItemStack[]{taggedNugget, new ItemStack(Material.AMETHYST_SHARD)});
        server.getScheduler().performTicks(1);
        assertNotNull(good.getResult(), "intended exact + material combination must craft");

        // ...but swapping the nugget for a DIFFERENT siftite item in the exact slot gets blocked.
        org.bukkit.inventory.CraftingInventory bad =
                (org.bukkit.inventory.CraftingInventory) server.createInventory(
                        null, org.bukkit.inventory.InventoryType.WORKBENCH, "mixed-bad");
        bad.setMatrix(new ItemStack[]{
                content.item(Material.IRON_NUGGET, "siftite_scrap", "Siftite Scrap"),
                new ItemStack(Material.AMETHYST_SHARD)});
        server.getScheduler().performTicks(1);
        assertNull(bad.getResult(),
                "a tagged item that matches no declared choice must not craft the foreign recipe");
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
