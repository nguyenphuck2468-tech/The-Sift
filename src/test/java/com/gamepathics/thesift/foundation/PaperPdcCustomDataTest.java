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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void datapackCustomDataUsesPaperPublicBukkitValuesBridge() throws IOException {
        List<String> resources = List.of(
                "datapack/data/thesift/recipe/soul_block.json",
                "datapack/data/thesift/loot_table/block/soul_block.json",
                "datapack/data/thesift/loot_table/block/sonorous_deepslate.json",
                "datapack/data/thesift/loot_table/entity/blub.json",
                "datapack/data/thesift/loot_table/entity/sifter.json"
        );

        for (String resource : resources) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertTrue(stream != null, "Missing resource: " + resource);
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(json.contains("\"minecraft:custom_data\": {\"PublicBukkitValues\":"),
                        "custom_data must wrap Bukkit PDC under PublicBukkitValues: " + resource);
            }
        }
    }
}
