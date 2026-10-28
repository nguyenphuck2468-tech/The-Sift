package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperPdcCustomDataTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void publicBukkitValuesCustomDataIsReadableThroughPdc() {
        NamespacedKey key = new NamespacedKey("thesift", "content");
        ItemStack stack = new ItemStack(Material.SOUL_SAND);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "soul_block");
        stack.setItemMeta(meta);

        assertEquals("soul_block", stack.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING));
    }
}
