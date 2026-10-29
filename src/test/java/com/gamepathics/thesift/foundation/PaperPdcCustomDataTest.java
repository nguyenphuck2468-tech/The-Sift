package com.gamepathics.thesift.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    void datapackCustomDataUsesPaperPublicBukkitValuesBridge() throws Exception {
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
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                
                List<String> payloads = new ArrayList<>();
                if (root.has("pools")) {
                    JsonArray pools = root.getAsJsonArray("pools");
                    for (JsonElement poolEl : pools) {
                        JsonObject pool = poolEl.getAsJsonObject();
                        if (pool.has("entries")) {
                            for (JsonElement entryEl : pool.getAsJsonArray("entries")) {
                                JsonObject entry = entryEl.getAsJsonObject();
                                if (entry.has("functions")) {
                                    for (JsonElement funcEl : entry.getAsJsonArray("functions")) {
                                        JsonObject func = funcEl.getAsJsonObject();
                                        if ("minecraft:set_components".equals(func.get("function").getAsString())) {
                                            JsonObject components = func.getAsJsonObject("components");
                                            JsonObject customData = components.getAsJsonObject("minecraft:custom_data");
                                            JsonObject pbv = customData.getAsJsonObject("PublicBukkitValues");
                                            payloads.add(pbv.get("thesift:content").getAsString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (root.has("result")) {
                    JsonObject result = root.getAsJsonObject("result");
                    JsonObject components = result.getAsJsonObject("components");
                    JsonObject customData = components.getAsJsonObject("minecraft:custom_data");
                    JsonObject pbv = customData.getAsJsonObject("PublicBukkitValues");
                    payloads.add(pbv.get("thesift:content").getAsString());
                }
                
                assertTrue(payloads.size() >= 1, "No custom_data payload found in " + resource);
                for (String payload : payloads) {
                    assertTrue(payload != null && !payload.isEmpty(), "Empty payload in " + resource);
                }
                if (resource.endsWith("sifter.json")) {
                    assertEquals(2, payloads.size(), "sifter.json should have 2 payloads");
                }
            }
        }
    }
}
