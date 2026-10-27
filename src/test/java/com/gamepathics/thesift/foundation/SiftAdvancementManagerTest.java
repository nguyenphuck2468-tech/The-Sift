package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SiftAdvancementManagerTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private SiftAdvancementManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("providers.strict", false);
        manager = new SiftAdvancementManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pickingUpCharoiteRequestsKindaPurpleish() {
        var player = server.addPlayer();
        Item charoite = player.getWorld().dropItem(player.getLocation(),
                new SiftiteContent(plugin).item(Material.AMETHYST_SHARD, "charoite", "Charoite"));
        EntityPickupItemEvent event = new EntityPickupItemEvent(player, charoite, 0);
        NamespacedKey advancementKey = new NamespacedKey(plugin, "kinda_purpleish");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getAdvancement(advancementKey)).thenReturn(null);
            manager.onPickup(event);

            bukkit.verify(() -> Bukkit.getAdvancement(advancementKey));
        }
    }

    @Test
    void craftingSiftiteTemplateDoesNotRequestTheArmorSetAdvancement() {
        var player = server.addPlayer();
        SiftiteContent items = new SiftiteContent(plugin);
        Recipe recipe = mock(Recipe.class);
        when(recipe.getResult()).thenReturn(items.item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "siftite_upgrade_smithing_template", "Siftite Upgrade Template"));
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getRecipe()).thenReturn(recipe);
        when(event.getWhoClicked()).thenReturn(player);
        NamespacedKey setAdvancement = new NamespacedKey(plugin, "cover_me_in_siftite");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getAdvancement(setAdvancement)).thenReturn(null);
            manager.onCraft(event);

            bukkit.verify(() -> Bukkit.getAdvancement(setAdvancement), never());
        }
    }

    @Test
    void drinkingIchorThroughCustomInteractionRequestsBloodOfTheGods() {
        var player = server.addPlayer();
        IchorContent ichor = new IchorContent(plugin);
        ItemStack bottle = ichor.ichorBottle();
        player.getInventory().setItemInMainHand(bottle);
        IchorListener ichorListener = new IchorListener(plugin, ichor, mock(IchorState.class));
        server.getPluginManager().registerEvents(ichorListener, plugin);
        server.getPluginManager().registerEvents(manager, plugin);
        NamespacedKey advancementKey = new NamespacedKey(plugin, "blood_of_the_gods");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getAdvancement(advancementKey)).thenReturn(null);
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                    bottle, null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(event);

            assertTrue(event.isCancelled(), "IchorListener should use its custom consumption path");
            bukkit.verify(() -> Bukkit.getAdvancement(advancementKey));
        }
    }
}
