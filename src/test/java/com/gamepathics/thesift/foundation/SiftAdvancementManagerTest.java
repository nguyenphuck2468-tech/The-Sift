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
        manager = new SiftAdvancementManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pickingUpRiftKeyRequestsItsAdvancement() {
        var player = server.addPlayer();
        Item key = player.getWorld().dropItem(player.getLocation(),
                new RiftManager(plugin).createRiftKey());
        EntityPickupItemEvent event = new EntityPickupItemEvent(player, key, 0);
        NamespacedKey advancementKey = new NamespacedKey(plugin, "rift_key");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getAdvancement(advancementKey)).thenReturn(null);
            manager.onPickup(event);

            bukkit.verify(() -> Bukkit.getAdvancement(advancementKey));
        }
    }

    @Test
    void craftingSiftiteTemplateDoesNotRequestGearAdvancement() {
        var player = server.addPlayer();
        SiftiteContent items = new SiftiteContent(plugin);
        Recipe recipe = mock(Recipe.class);
        when(recipe.getResult()).thenReturn(items.item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "siftite_upgrade_smithing_template", "Siftite Upgrade Template"));
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getRecipe()).thenReturn(recipe);
        when(event.getWhoClicked()).thenReturn(player);
        NamespacedKey gearAdvancement = new NamespacedKey(plugin, "shard_gear");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getAdvancement(gearAdvancement)).thenReturn(null);
            manager.onCraft(event);

            bukkit.verify(() -> Bukkit.getAdvancement(gearAdvancement), never());
        }
    }

    @Test
    void killingTheSifterDoesNotRequestOtherBossAdvancements() {
        var killer = server.addPlayer();
        LivingEntity dead = mock(LivingEntity.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        NamespacedKey creatureKey = new NamespacedKey(plugin, "sift_creature");
        when(dead.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(eq(creatureKey), eq(PersistentDataType.STRING))).thenReturn("the_sifter");
        when(dead.getKiller()).thenReturn(killer);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(dead);
        NamespacedKey sifterAdvancement = new NamespacedKey(plugin, "sifter");
        NamespacedKey tyrantAdvancement = new NamespacedKey(plugin, "tyrant");
        NamespacedKey matriarchAdvancement = new NamespacedKey(plugin, "matriarch");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getAdvancement(sifterAdvancement)).thenReturn(null);
            bukkit.when(() -> Bukkit.getAdvancement(tyrantAdvancement)).thenReturn(null);
            bukkit.when(() -> Bukkit.getAdvancement(matriarchAdvancement)).thenReturn(null);
            manager.onCreatureDeath(event);

            bukkit.verify(() -> Bukkit.getAdvancement(sifterAdvancement));
            bukkit.verify(() -> Bukkit.getAdvancement(tyrantAdvancement), never());
            bukkit.verify(() -> Bukkit.getAdvancement(matriarchAdvancement), never());
        }
    }

    @Test
    void drinkingIchorThroughCustomInteractionRequestsStormAdvancement() {
        var player = server.addPlayer();
        IchorContent ichor = new IchorContent(plugin);
        ItemStack bottle = ichor.ichorBottle();
        player.getInventory().setItemInMainHand(bottle);
        IchorListener ichorListener = new IchorListener(plugin, ichor, mock(IchorState.class));
        server.getPluginManager().registerEvents(ichorListener, plugin);
        server.getPluginManager().registerEvents(manager, plugin);
        NamespacedKey stormAdvancement = new NamespacedKey(plugin, "storm");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.getAdvancement(stormAdvancement)).thenReturn(null);
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                    bottle, null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(event);

            assertTrue(event.isCancelled(), "IchorListener should use its custom consumption path");
            bukkit.verify(() -> Bukkit.getAdvancement(stormAdvancement));
        }
    }
}
