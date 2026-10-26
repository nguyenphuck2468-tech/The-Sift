package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Location;
import org.bukkit.entity.IronGolem;
import org.bukkit.persistence.PersistentDataType;

import static org.junit.jupiter.api.Assertions.*;

class EchoGolemManagerTest {

    private ServerMock server;
    private JavaPlugin mockPlugin;
    private EchoGolemManager echoGolemManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        mockPlugin = MockBukkit.createMockPlugin("TheSift");
        echoGolemManager = new EchoGolemManager(mockPlugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> echoGolemManager.shutdown());
    }

    @Test
    void start_doesNotThrow() {
        assertDoesNotThrow(() -> echoGolemManager.start());
    }

    @Test
    void reloadedEchoGolemRecreatesItsBossBarOnce() {
        var world = server.addSimpleWorld("golem-load");
        IronGolem golem = world.spawn(new Location(world, 0, 64, 0), IronGolem.class);
        golem.getPersistentDataContainer().set(new NamespacedKey(mockPlugin, "echo_golem_entity"),
                PersistentDataType.STRING, "echo_golem");
        EntityAddToWorldEvent event = new EntityAddToWorldEvent(golem, world);

        echoGolemManager.onEntityAddedToWorld(event);
        echoGolemManager.onEntityAddedToWorld(event);

        assertEquals(1, echoGolemManager.trackedBossBarCount());
    }

    @Test
    void charoiteInArmorOrOffhandCountsAsProtection() {
        mockPlugin.getConfig().set("providers.strict", false);
        var world = server.addSimpleWorld("golem-charoite");
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        ItemStack charoite = echoGolemManager.content().item(Material.AMETHYST_SHARD, "charoite", "Charoite");

        assertFalse(echoGolemManager.protectedByCharoite(player));

        player.getInventory().setHelmet(charoite);
        assertTrue(echoGolemManager.protectedByCharoite(player));

        player.getInventory().setHelmet(null);
        player.getInventory().setItemInOffHand(charoite);
        assertTrue(echoGolemManager.protectedByCharoite(player));

        // Untagged vanilla items must not be mistaken for Charoite.
        player.getInventory().setItemInOffHand(new ItemStack(Material.AMETHYST_SHARD));
        assertFalse(echoGolemManager.protectedByCharoite(player));
    }

    @Test
    void charoiteProtectionCanBeDisabledByConfig() {
        mockPlugin.getConfig().set("providers.strict", false);
        mockPlugin.getConfig().set("echo-golem.charoite-protection", false);
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0,
                echoGolemManager.content().item(Material.AMETHYST_SHARD, "charoite", "Charoite"));

        assertFalse(echoGolemManager.protectedByCharoite(player));
    }
}

class RiftManagerTest {

    private JavaPlugin mockPlugin;
    private RiftManager riftManager;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        riftManager = new RiftManager(mockPlugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createRiftKey_returnsItemWithPersistentData() {
        ItemStack key = riftManager.createRiftKey();
        
        assertNotNull(key);
        assertEquals(Material.TRIAL_KEY, key.getType());
        assertTrue(key.hasItemMeta());
        assertEquals("§5§lRift Key", key.getItemMeta().getDisplayName());
    }

    @Test
    void isRiftKey_returnsTrueForCreatedKey() {
        ItemStack key = riftManager.createRiftKey();
        
        assertTrue(riftManager.isRiftKey(key));
    }

    @Test
    void isRiftKey_returnsFalseForNull() {
        assertFalse(riftManager.isRiftKey(null));
    }

    @Test
    void isRiftKey_returnsFalseForRegularItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        
        assertFalse(riftManager.isRiftKey(item));
    }

    @Test
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> riftManager.shutdown());
    }

    @Test
    void start_doesNotThrow() {
        assertDoesNotThrow(() -> riftManager.start());
    }
}

class SiftiteDurabilityTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void durabilityMultiplierRaisesCustomMaxDamageOnFreshGear() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        mockPlugin.getConfig().set("siftite.durability-multiplier", 1.5);
        SiftiteContent content = new SiftiteContent(mockPlugin);

        ItemStack spear = content.item(Material.NETHERITE_SWORD, "siftite_spear", "Siftite Spear");
        assertNotNull(spear);
        var meta = spear.getItemMeta();
        assertInstanceOf(org.bukkit.inventory.meta.Damageable.class, meta);
        org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
        int vanilla = Material.NETHERITE_SWORD.getMaxDurability();
        assertTrue(damageable.hasMaxDamage());
        assertEquals(vanilla + Math.round(vanilla * 0.5f), damageable.getMaxDamage());
        assertEquals(0, damageable.getDamage());
    }

    @Test
    void multiplierOfOneLeavesGearAtVanillaDurability() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        mockPlugin.getConfig().set("siftite.durability-multiplier", 1.0);
        SiftiteContent content = new SiftiteContent(mockPlugin);

        ItemStack sword = content.item(Material.NETHERITE_SWORD, "siftite_spear", "Siftite Spear");
        var meta = sword.getItemMeta();
        assertInstanceOf(org.bukkit.inventory.meta.Damageable.class, meta);
        org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
        assertFalse(damageable.hasMaxDamage(), "no custom ceiling should be written at x1.0");
    }

    @Test
    void nonDamageableItemsIgnoreTheMultiplier() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        mockPlugin.getConfig().set("siftite.durability-multiplier", 2.0);
        SiftiteContent content = new SiftiteContent(mockPlugin);

        // siftite_ingot maps to NETHERITE_INGOT which has no durability: must not throw.
        ItemStack ingot = content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
        assertNotNull(ingot);
        assertTrue(content.is(ingot, "siftite_ingot"));
    }
}

class MeatFoodChainTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cookedMeatIdsResolveToTaggedItems() {
        MockBukkit.mock();
        JavaPlugin mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        SiftiteContent content = new SiftiteContent(mockPlugin);

        ItemStack rawSifter = content.forId("raw_sifter_meat");
        ItemStack cookedSifter = content.forId("cooked_sifter_meat");
        ItemStack blubMeat = content.forId("blub_meat");
        ItemStack cookedBlub = content.forId("cooked_blub_meat");

        assertNotNull(rawSifter);
        assertNotNull(cookedSifter);
        assertNotNull(blubMeat);
        assertNotNull(cookedBlub);
        assertTrue(content.is(rawSifter, "raw_sifter_meat"));
        assertTrue(content.is(cookedSifter, "cooked_sifter_meat"));
        assertTrue(content.is(blubMeat, "blub_meat"));
        assertTrue(content.is(cookedBlub, "cooked_blub_meat"));
    }

    @Test
    void smeltingRecipesAreRegisteredAndReloadSafe() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        SiftiteContent content = new SiftiteContent(mockPlugin);

        content.registerRecipes();
        NamespacedKey sifterKey = new NamespacedKey(mockPlugin, "smelt_raw_sifter_meat");
        var recipe = server.getRecipe(sifterKey);
        assertNotNull(recipe, "smelt_raw_sifter_meat recipe should exist after registerRecipes()");
        assertInstanceOf(org.bukkit.inventory.FurnaceRecipe.class, recipe);

        // Re-registering must replace (not duplicate or crash) the recipe.
        content.registerRecipes();
        assertNotNull(server.getRecipe(sifterKey));
    }
}
