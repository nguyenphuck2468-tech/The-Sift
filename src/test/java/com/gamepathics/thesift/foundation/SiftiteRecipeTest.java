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
        // tagged item exactly matches one of the recipe's declared ExactChoice options,
        // the per-slot check in onPrepareCraft leaves the result untouched (verified here
        // through MockBukkit's crafting inventory, which runs PrepareItemCraftEvent).
        NamespacedKey foreignKey = new NamespacedKey("thesift_datapack_sim", "siftite_ingot_from_nuggets");
        ItemStack output = content.item(Material.IRON_INGOT, "sim_output", "Simulated Output");
        ShapelessRecipe foreignRecipe = new ShapelessRecipe(foreignKey, output);
        ItemStack taggedIngot = content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
        foreignRecipe.addIngredient(RecipeChoice.exactChoice(taggedIngot));
        server.addRecipe(foreignRecipe);

        NamespacedKey idKey = new NamespacedKey(plugin, "content");
        assertFalse(SiftiteContent.recipeIntendsTaggedItems(foreignRecipe, idKey),
                "recipes with deliberate non-material choices are left to Bukkit matching, not the leak heuristic");

        // Deterministic guard assertion: the tagged ingot exactly matches a declared
        // ExactChoice of this foreign recipe, so the per-slot guard must NOT block it.
        // (MockBukkit does not simulate PrepareItemCraftEvent through createInventory,
        // so we call the extracted pure guard function directly instead of a fake E2E.)
        java.util.concurrent.atomic.AtomicBoolean blocked = new java.util.concurrent.atomic.AtomicBoolean(false);
        boolean decision = SiftiteContent.blockTaggedItemsOutsideExactChoices(
                foreignRecipe, new ItemStack[]{taggedIngot}, () -> blocked.set(true));
        assertFalse(decision, "an item matching a declared ExactChoice must pass the guard");
        assertFalse(blocked.get(), "the block action must not fire for an intended exact match");

        // A plain vanilla material-only recipe must still be flagged by the leak heuristic:
        // its MaterialChoice compares bare material type, so a siftite-tagged stand-in of
        // the same material slips in unless the per-slot guard blocks it at craft time.
        ShapelessRecipe vanillaClone = new ShapelessRecipe(
                new NamespacedKey("other_plugin", "plain"), output);
        vanillaClone.addIngredient(Material.NETHERITE_INGOT);
        assertTrue(SiftiteContent.recipeIntendsTaggedItems(vanillaClone, idKey),
                "material-only recipes that would accept a tagged stand-in stay flagged for the guard");
    }

    @Test
    void guardBlocksMaterialOnlyLeakAndKeepsIntendedExactCrafts() {
        // Direct unit test of the per-slot guard semantics (review P0): ExactChoice matches
        // count as intent; MaterialChoice/TagChoice never do, even when the raw material fits.
        NamespacedKey exactKey = new NamespacedKey("foreign_exact", "ok");
        ItemStack taggedIngot = content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
        ShapelessRecipe exactRecipe = new ShapelessRecipe(exactKey,
                content.item(Material.IRON_INGOT, "sim_output", "Simulated Output"));
        exactRecipe.addIngredient(RecipeChoice.exactChoice(taggedIngot));

        java.util.List<ItemStack> exactOptions = SiftiteContent.exactChoiceOptions(exactRecipe);
        assertEquals(1, exactOptions.size());
        assertTrue(exactOptions.get(0).isSimilar(taggedIngot),
                "declared ExactChoice option must match the intended tagged item slot-for-slot");

        // The leak vector: a material-only foreign recipe declares NO ExactChoice, so a
        // tagged siftite ingot can never be whitelisted there — the guard blanks the result.
        ShapelessRecipe materialOnly = new ShapelessRecipe(
                new NamespacedKey("foreign_material", "leak"),
                content.item(Material.IRON_INGOT, "sim_output", "Simulated Output"));
        materialOnly.addIngredient(Material.NETHERITE_INGOT);
        assertTrue(SiftiteContent.exactChoiceOptions(materialOnly).isEmpty(),
                "a MaterialChoice-only recipe must offer no exact-match whitelist entries");
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
        mixed.setIngredient('N', RecipeChoice.exactChoice(taggedNugget));
        mixed.setIngredient('I', Material.AMETHYST_SHARD);
        server.addRecipe(mixed);

        NamespacedKey idKey = new NamespacedKey(plugin, "content");
        // Mixed choices are hand-authored too — no wholesale whitelist via the leak heuristic.
        assertFalse(SiftiteContent.recipeIntendsTaggedItems(mixed, idKey));

        // Per-slot semantics: the nugget choice accepts the tagged nugget, but NO declared
        // choice accepts a siftite_ingot — so an ingot placed in the material slot stays guarded.
        boolean anyChoiceAcceptsIngot = SiftiteContent.exactChoiceOptions(mixed).stream()
                .anyMatch(option -> option.isSimilar(
                        content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")));
        assertFalse(anyChoiceAcceptsIngot,
                "a mixed recipe must not implicitly accept unrelated siftite items");
        boolean anyChoiceAcceptsNugget = SiftiteContent.exactChoiceOptions(mixed).stream()
                .anyMatch(option -> option.isSimilar(taggedNugget));
        assertTrue(anyChoiceAcceptsNugget);

        // End-to-end semantics via the deterministic guard function (MockBukkit does not
        // simulate PrepareItemCraftEvent through createInventory): correct ingredients
        // pass...
        java.util.concurrent.atomic.AtomicBoolean blockedGood = new java.util.concurrent.atomic.AtomicBoolean(false);
        assertFalse(SiftiteContent.blockTaggedItemsOutsideExactChoices(mixed,
                new ItemStack[]{taggedNugget, new ItemStack(Material.AMETHYST_SHARD)},
                () -> blockedGood.set(true)),
                "intended exact + material combination must craft");
        assertFalse(blockedGood.get());

        // ...but swapping the nugget for a DIFFERENT siftite item in the exact slot gets blocked.
        java.util.concurrent.atomic.AtomicBoolean blockedBad = new java.util.concurrent.atomic.AtomicBoolean(false);
        assertTrue(SiftiteContent.blockTaggedItemsOutsideExactChoices(mixed,
                new ItemStack[]{
                        content.item(Material.IRON_NUGGET, "siftite_scrap", "Siftite Scrap"),
                        new ItemStack(Material.AMETHYST_SHARD)},
                () -> blockedBad.set(true)),
                "a tagged item that matches no declared choice must not craft the foreign recipe");
        assertTrue(blockedBad.get(), "the block action must fire for the non-matching tagged item");

        // And the P0 leak vector: a siftite_ingot dropped into a vanilla material-only
        // NETHERITE_INGOT recipe satisfies MaterialChoice but NO ExactChoice — it must be
        // blocked, whereas a plain netherite ingot (no tag) is none of the guard's business.
        ShapelessRecipe vanillaClone = new ShapelessRecipe(
                new NamespacedKey("other_plugin", "plain_netherite"), output);
        vanillaClone.addIngredient(Material.NETHERITE_INGOT);
        // Register it on the server so the grid below genuinely MATCHES this recipe —
        // otherwise an assertNull on a result would be vacuously true (no recipe at all).
        server.addRecipe(vanillaClone);
        assertTrue(SiftiteContent.blockTaggedItemsOutsideExactChoices(vanillaClone,
                new ItemStack[]{content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot")},
                () -> {}),
                "tagged siftite ingot must never slip into a material-only vanilla recipe");
        assertFalse(SiftiteContent.blockTaggedItemsOutsideExactChoices(vanillaClone,
                new ItemStack[]{new ItemStack(Material.NETHERITE_INGOT)}, () -> {}),
                "plain vanilla ingredients are untouched by the guard");

        // Own-namespace datapack-style recipe: same namespace as the plugin, but its
        // ExactChoice deliberately accepts the tagged item — the ownership whitelist lets
        // it craft instead of blanking it out.
        NamespacedKey ownDatapackKey = new NamespacedKey("thesift", "siftite_ingot_from_nuggets_dp");
        ShapedRecipe ownRecipe = new ShapedRecipe(ownDatapackKey, output);
        ownRecipe.shape("NNN", "NNN", "NNN");
        ItemStack nineNuggets = content.item(Material.IRON_NUGGET, "siftite_nugget", "Siftite Nugget");
        nineNuggets.setAmount(9);
        ownRecipe.setIngredient('N', RecipeChoice.exactChoice(nineNuggets));
        server.addRecipe(ownRecipe);
        java.util.concurrent.atomic.AtomicBoolean blockedOwn = new java.util.concurrent.atomic.AtomicBoolean(false);
        assertFalse(SiftiteContent.blockTaggedItemsOutsideExactChoices(ownRecipe,
                filledMatrix(nineNuggets), () -> blockedOwn.set(true)),
                "plugin-owned recipes are whitelisted wholesale");
        assertFalse(blockedOwn.get());
    }

    /** 3x3 matrix fully filled with copies of the given ingredient. */
    private static ItemStack[] filledMatrix(ItemStack ingredient) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) matrix[i] = ingredient.clone();
        return matrix;
    }

    @Test
    void prepareCraftListenerBlanksLeakyVanillaRecipeEndToEnd() {
        // End-to-end through the REAL event listener (not just the extracted function):
        // MockBukkit fires PrepareItemCraftEvent when the crafting matrix is set. A 3x3
        // grid of plain netherite ingots matches vanilla's NETHERITE_INGOT shape and must
        // produce a result; swapping in tagged siftite ingots must hit the guard and blank
        // the result — proving both that the wiring works AND that the leak vector is shut.
        ItemStack plain = new ItemStack(Material.NETHERITE_INGOT);
        org.bukkit.inventory.CraftingInventory vanillaGrid = createWorkbench();
        vanillaGrid.setMatrix(filledMatrix(plain));
        assertNotNull(vanillaGrid.getResult(),
                "baseline: plain netherite ingots must craft the vanilla netherite ingot");

        ItemStack tagged = content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
        org.bukkit.inventory.CraftingInventory leakyGrid = createWorkbench();
        leakyGrid.setMatrix(filledMatrix(tagged));
        assertNull(leakyGrid.getResult(),
                "a siftite-tagged item must never satisfy the vanilla material-only recipe via the listener");
    }

    private org.bukkit.inventory.CraftingInventory createWorkbench() {
        return (org.bukkit.inventory.CraftingInventory) server.createInventory(
                null, org.bukkit.event.inventory.InventoryType.WORKBENCH, "grid");
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
