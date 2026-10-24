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
}
