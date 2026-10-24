package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class IchorRecipeTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private IchorContent content;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("providers.strict", false);
        content = new IchorContent(plugin);
        content.registerRecipes();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void paneRecipeRequiresTaggedIchorGlass() {
        Recipe registered = server.getRecipe(new NamespacedKey(plugin, "ichor_glass_pane"));
        assertInstanceOf(ShapedRecipe.class, registered);
        RecipeChoice glassChoice = ((ShapedRecipe) registered).getChoiceMap().get('G');

        assertNotNull(glassChoice);
        assertTrue(glassChoice.test(content.ichorGlass()));
        assertFalse(glassChoice.test(new ItemStack(Material.GLASS)));
    }
}
