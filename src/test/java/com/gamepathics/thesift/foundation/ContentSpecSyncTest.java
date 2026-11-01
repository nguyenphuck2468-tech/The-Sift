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
    void bossVariantTheSifterIsFlaggedFanmade() throws Exception {
        Spec spec = load();
        assertTrue(spec.mobs().get("the_sifter").get("fanmade").getAsBoolean(),
                "the_sifter is a plugin boss variant, not a mod mob — it must leave the mandatory readiness set");
        assertTrue(spec.mobs().get("spire_tyrant").get("fanmade").getAsBoolean());
        assertFalse(spec.mobs().get("sifter").get("fanmade") != null
                        && spec.mobs().get("sifter").get("fanmade").getAsBoolean(),
                "the herd mob 'sifter' is a core mod mob and must stay mandatory");
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
        assertEquals("WITCH", spec.mobs().get("sift_wastes_stalker").get("type").getAsString(),
                "sift_wastes_stalker's spec type must match the summonCreature fallback (WITCH)");
    }

    @Test
    void sifterMeatItemsExistForTheDropAndSmeltRecipe() throws Exception {
        Spec spec = load();
        for (String id : new String[]{"raw_sifter_meat", "cooked_sifter_meat",
                "blub_meat", "cooked_blub_meat"}) {
            assertTrue(spec.items().containsKey(id), "item '" + id + "' is missing from the spec");
        }
    }

    @Test
    void fanmadeContentIsFlaggedSoStrictModeCanFilterIt() throws Exception {
        Spec spec = load();
        assertTrue(spec.items().get("raw_loomer").get("fanmade").getAsBoolean(),
                "Loomer is fan-made; its meat must not join the mandatory readiness set");
        assertTrue(spec.items().get("cooked_loomer").get("fanmade").getAsBoolean());
        assertTrue(spec.items().get("blub_meat").get("fanmade").getAsBoolean(),
                "blub_meat is a plugin extension, not a mod item");
        assertTrue(spec.items().get("cooked_blub_meat").get("fanmade").getAsBoolean());
        assertFalse(spec.items().get("raw_sifter_meat").has("fanmade"),
                "raw_sifter_meat is a mod item and must stay mandatory");
    }
}
