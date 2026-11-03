package com.gamepathics.thesift.foundation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins integrations/content-spec.json to the IDs and statlines the runtime actually spawns
 * (review P1: the spec had drifted from the code — strict readiness could pass via
 * {@code the_sifter} while the ecology herds spawned an unlisted {@code sifter}).
 * The spec also drives the generated MythicMobs YAML, so a drift here becomes a gameplay
 * difference on provider servers, not just a documentation mismatch.
 */
class ContentSpecSyncTest {
    private static final Path SPEC = Path.of("integrations", "content-spec.json");

    private record Spec(Map<String, JsonObject> mobs, Map<String, JsonObject> items) {}

    private static Spec load() throws Exception {
        assertTrue(Files.exists(SPEC), "content-spec.json must stay at integrations/ for the build generator");
        JsonObject root = new Gson().fromJson(Files.readString(SPEC), JsonObject.class);
        Map<String, JsonObject> mobs = new LinkedHashMap<>();
        for (var element : (JsonArray) root.get("mobs")) {
            JsonObject mob = element.getAsJsonObject();
            mobs.put(mob.get("id").getAsString(), mob);
        }
        Map<String, JsonObject> items = new LinkedHashMap<>();
        for (var element : (JsonArray) root.get("items")) {
            JsonObject item = element.getAsJsonObject();
            items.put(item.get("id").getAsString(), item);
        }
        return new Spec(mobs, items);
    }

    private static int health(JsonObject mob) {
        return mob.get("health").getAsInt();
    }

    private static int damage(JsonObject mob) {
        return mob.get("damage").getAsInt();
    }

    @Test
    void everyCoreEcologyMobIsInTheSpec() throws Exception {
        Spec spec = load();
        // SiftEcologyManager.FAN_CORE_MOBS + manager-spawned core mobs.
        for (String id : new String[]{"singer", "echo_golem", "blub", "sifter", "dark_sniffer"}) {
            assertTrue(spec.mobs().containsKey(id), "core mob '" + id + "' is missing from the spec");
        }
    }

    @Test
    void coreSifterIsPresentWithoutFanmadeVariants() throws Exception {
        Spec spec = load();
        assertTrue(spec.mobs().containsKey("sifter"));
        assertFalse(spec.mobs().containsKey("the_sifter"));
        assertFalse(spec.mobs().containsKey("spire_tyrant"));
    }

    @Test
    void mobStatlinesMatchTheRuntimeDefaults() throws Exception {
        Spec spec = load();
        // Reference-mod statlines mirrored by modMobStats() and the managers.
        assertEquals(40, health(spec.mobs().get("sifter")));
        assertEquals(10, damage(spec.mobs().get("sifter")));
        assertEquals(40, health(spec.mobs().get("dark_sniffer")));
        assertEquals(7, damage(spec.mobs().get("dark_sniffer")));
        assertEquals(20, health(spec.mobs().get("singer")));
        assertEquals(0, damage(spec.mobs().get("singer")));
        assertEquals(34, health(spec.mobs().get("echo_golem")));
        assertEquals(0, damage(spec.mobs().get("echo_golem")));
        assertEquals("RABBIT", spec.mobs().get("blub").get("type").getAsString(),
                "blub's spec type must match the summonCreature fallback (RABBIT)");
    }

    @Test
    void sifterMeatItemsExistForTheDropAndSmeltRecipe() throws Exception {
        Spec spec = load();
        for (String id : new String[]{"raw_sifter_meat", "cooked_sifter_meat"}) {
            assertTrue(spec.items().containsKey(id), "item '" + id + "' is missing from the spec");
        }
    }
}
