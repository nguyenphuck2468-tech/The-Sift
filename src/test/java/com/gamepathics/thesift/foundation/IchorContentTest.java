package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class IchorContentTest {

    private JavaPlugin mockPlugin;
    private IchorContent ichorContent;
    private NamespacedKey contentKey;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        mockPlugin = MockBukkit.createMockPlugin("TheSift");
        mockPlugin.getConfig().set("providers.strict", false);
        contentKey = new NamespacedKey("thesift", "content");
        ichorContent = new IchorContent(mockPlugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void create_returnsItemWithPersistentData() {
        ItemStack item = ichorContent.create(Material.GLASS_BOTTLE, "ichor_bottle", "Ichor Bottle");
        
        assertNotNull(item);
        assertEquals(Material.GLASS_BOTTLE, item.getType());
        assertTrue(item.hasItemMeta());
        
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertEquals("§dIchor Bottle", meta.getDisplayName());
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String storedId = container.get(contentKey, PersistentDataType.STRING);
        assertEquals("ichor_bottle", storedId);
    }

    @Test
    void strictModeRefusesVanillaTaggedIchorItemWhenProviderIsMissing() {
        mockPlugin.getConfig().set("providers.strict", true);
        mockPlugin.getConfig().set("integrations.items.provider", "missing-provider");
        assertNull(ichorContent.ichorBottle());
    }

    @Test
    void is_returnsTrueForMatchingId() {
        ItemStack item = ichorContent.create(Material.GLASS_BOTTLE, "ichor_bottle", "Ichor Bottle");
        
        assertTrue(ichorContent.is(item, "ichor_bottle"));
    }

    @Test
    void is_returnsFalseForNonMatchingId() {
        ItemStack item = ichorContent.create(Material.GLASS_BOTTLE, "ichor_bottle", "Ichor Bottle");
        
        assertFalse(ichorContent.is(item, "ichor_glass"));
    }

    @Test
    void is_returnsFalseForNullItem() {
        assertFalse(ichorContent.is(null, "ichor_bottle"));
    }

    @Test
    void is_returnsFalseForItemWithoutMeta() {
        ItemStack item = new ItemStack(Material.GLASS_BOTTLE);
        // no item meta
        
        assertFalse(ichorContent.is(item, "ichor_bottle"));
    }

    @Test
    void is_returnsFalseForItemWithoutPersistentData() {
        ItemStack item = new ItemStack(Material.GLASS_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Test");
        item.setItemMeta(meta);
        // no persistent data container entry
        
        assertFalse(ichorContent.is(item, "ichor_bottle"));
    }

    @Test
    void ichorBottle_createsCorrectItem() {
        ItemStack item = ichorContent.ichorBottle();
        
        assertNotNull(item);
        assertTrue(ichorContent.is(item, IchorContent.ICHOR_BOTTLE_ID));
    }

    @Test
    void ichorGlass_createsCorrectItem() {
        ItemStack item = ichorContent.ichorGlass();
        
        assertNotNull(item);
        assertTrue(ichorContent.is(item, IchorContent.ICHOR_GLASS_ID));
    }

    @Test
    void ichorSnow_createsCorrectItem() {
        ItemStack item = ichorContent.ichorSnow();
        
        assertNotNull(item);
        assertTrue(ichorContent.is(item, IchorContent.ICHOR_SNOW_ID));
    }

    @Test
    void ichorSnowBlock_createsCorrectItem() {
        ItemStack item = ichorContent.ichorSnowBlock();
        
        assertNotNull(item);
        assertTrue(ichorContent.is(item, IchorContent.ICHOR_SNOW_BLOCK_ID));
    }

    @Test
    void registerRecipes_doesNotThrow() {
        // The dedicated recipe test verifies the exact ingredient behavior.
        assertDoesNotThrow(() -> ichorContent.registerRecipes());
    }
}
