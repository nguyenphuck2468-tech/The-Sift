package com.gamepathics.thesift.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackIntegrityTest {
    private static final Path PROGRESSION = Path.of("src/main/resources/datapack");
    private static final Path DIMENSION = Path.of("src/main/resources/sift_dimension_datapack");
    private static final Set<String> CANONICAL_BIOMES = Set.of(
            "thesift:bulb_forest",
            "thesift:carapace",
            "thesift:crystal_spires",
            "thesift:ichor_snowy_peaks",
            "thesift:overgrown_clearing",
            "thesift:overgrown_forest",
            "thesift:overgrown_forest_slopes",
            "thesift:overgrown_peaks",
            "thesift:overgrown_slopes",
            "thesift:pink_meadows",
            "thesift:shattered_peaks",
            "thesift:sift_deep_dark",
            "thesift:sift_wastes",
            "thesift:siftslate_peaks",
            "thesift:siftslate_slopes"
    );

    @Test
    void sculkflowerResourcePackShipsEveryReferencedTexture() throws IOException {
        Path assets = Path.of("src/main/resources/pack/assets/thesift");
        Map<String, String> modelTextures = Map.of(
                "models/block/sculkflower.json", "block/sculkflower",
                "models/block/sculkflower_crop_stage0.json", "block/sculkflower_crop_stage0",
                "models/block/sculkflower_crop_stage1.json", "block/sculkflower_crop_stage1",
                "models/item/sculkflower_seeds.json", "item/sculkflower_seeds"
        );
        for (Map.Entry<String, String> entry : modelTextures.entrySet()) {
            Path model = assets.resolve(entry.getKey());
            assertTrue(Files.exists(model), "missing Sculkflower model: " + model);
            JsonObject root = JsonParser.parseString(Files.readString(model)).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            boolean found = textures.entrySet().stream()
                    .anyMatch(texture -> ("thesift:" + entry.getValue()).equals(texture.getValue().getAsString()));
            assertTrue(found, model + " must reference thesift:" + entry.getValue());
            Path texture = assets.resolve("textures/" + entry.getValue() + ".png");
            assertTrue(Files.exists(texture), model + " references missing texture " + texture);
        }
    }

    @Test
    void allBundledDatapackJsonIsValid() throws IOException {
        for (Path pack : Set.of(PROGRESSION, DIMENSION)) {
            try (Stream<Path> paths = Files.walk(pack)) {
                for (Path json : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                    JsonParser.parseString(Files.readString(json));
                }
            }
        }
    }

    @Test
    void progressionPackShipsLootTablesReferencedByTheFoodChain() throws IOException {
        Path lootRoot = PROGRESSION.resolve("data/thesift/loot_table/entity");
        assertTrue(Files.exists(lootRoot.resolve("sifter.json")),
                "datapack must bundle the Sifter loot table (raw_sifter_meat drop)");
        assertTrue(Files.exists(lootRoot.resolve("blub.json")),
                "datapack must bundle the Blub loot table (blub_meat drop)");
        try (Stream<Path> paths = Files.walk(lootRoot)) {
            for (Path json : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonParser.parseString(Files.readString(json)); // must be valid JSON
            }
        }
    }


    @Test
    void progressionPackShipsBlockLootTablesTagsAndRecipes() throws IOException {
        Path blockLoot = PROGRESSION.resolve("data/thesift/loot_table/block");
        assertTrue(Files.exists(blockLoot.resolve("soul_block.json")),
                "datapack must bundle the Soul Block loot table (tagged drop)");
        assertTrue(Files.exists(blockLoot.resolve("sonorous_deepslate.json")),
                "datapack must bundle the Sonorous Deepslate loot table (tagged drop)");
        Path tags = PROGRESSION.resolve("data/minecraft/tags/block");
        assertTrue(Files.exists(tags.resolve("soul_speed_blocks.json")),
                "datapack must ship the soul_speed_blocks tag used by the mod-parity soul mechanic");
        Path snifferDiggable = tags.resolve("sniffer_diggable_block.json");
        assertTrue(Files.exists(snifferDiggable),
                "datapack must extend the vanilla Sniffer diggable-block tag for Sculk");
        JsonObject snifferTag = JsonParser.parseString(Files.readString(snifferDiggable)).getAsJsonObject();
        assertFalse(snifferTag.get("replace").getAsBoolean(),
                "Sniffer diggable blocks must extend rather than replace vanilla entries");
        assertTrue(snifferTag.getAsJsonArray("values").toString().contains("minecraft:sculk"),
                "Sculk must be a valid additional Sniffer dig target");
        // ancient_city_variants is datapack-facing only: it documents which deepslate variants
        // count as Ancient-City-adjacent for pack-driven mechanics (loot/advancements). The
        // Java portal scanner deliberately matches REINFORCED_DEEPSLATE directly and does NOT
        // read this tag, so the test here only pins the tag's JSON shape.
        Path ancientCityTag = tags.resolve("ancient_city_variants.json");
        assertTrue(Files.exists(ancientCityTag),
                "datapack must ship the ancient_city_variants block tag");
        JsonObject ancientCityValues = JsonParser.parseString(Files.readString(ancientCityTag)).getAsJsonObject();
        assertTrue(ancientCityValues.get("values").getAsJsonArray().toString().contains("deepslate"),
                "ancient_city_variants must list deepslate variant entries");
        Path recipes = PROGRESSION.resolve("data/thesift/recipe");
        assertTrue(Files.exists(recipes.resolve("soul_block.json")),
                "datapack must ship the Soul Block crafting recipe");
        assertFalse(Files.exists(recipes.resolve("blub_campfire.json")),
                "the Blub campfire recipe must NOT be a material-only datapack recipe: plain"
                        + " vanilla mutton would cook into Cooked Blub Meat there; it is"
                        + " registered in Java with an ExactChoice instead");
        for (Path root : Set.of(blockLoot, tags, recipes)) {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path json : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                    JsonObject parsed = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                    if (root.equals(recipes)) {
                        assertTrue(parsed.has("type"), "recipe must declare its type: " + json);
                    } else if (root.equals(tags)) {
                        assertTrue(parsed.has("values"), "tag must declare values: " + json);
                    } else {
                        assertEquals("minecraft:block", parsed.get("type").getAsString(),
                                "block loot tables must be typed minecraft:block: " + json);
                    }
                }
            }
        }
    }

    @Test
    void biomeIdsHaveOneOwnerAndProgressionKeepsAdvancements() throws IOException {
        Set<String> progressionBiomes = biomeIds(PROGRESSION);
        Set<String> dimensionBiomes = biomeIds(DIMENSION);
        Set<String> duplicates = new HashSet<>(progressionBiomes);
        duplicates.retainAll(dimensionBiomes);

        assertTrue(duplicates.isEmpty(), "Biome IDs must be defined by only one bundled pack: " + duplicates);
        assertEquals(CANONICAL_BIOMES, dimensionBiomes);
        assertTrue(progressionBiomes.isEmpty(), "Progression pack must not define biome JSON");
        assertTrue(Files.isDirectory(PROGRESSION.resolve("data/thesift/advancement")), "Progression advancements must remain");
    }

    @Test
    void progressionPackContainsNoBiomeJson() throws IOException {
        Path biomeDir = PROGRESSION.resolve("data/thesift/worldgen/biome");
        assertTrue(Files.notExists(biomeDir) || Files.list(biomeDir).findFirst().isEmpty(),
                "Progression pack must not contain any worldgen/biome JSON to avoid duplicate biome ID ownership");
    }

    @Test
    void biomeFeatureReferencesResolveThroughPlacedAndConfiguredFeatures() throws IOException {
        Map<String, Path> placedFeatures = resources(DIMENSION.resolve("data"), "worldgen/placed_feature");
        // The dimension pack keeps the legacy registry directory name; resolve both spellings.
        Map<String, Path> configuredFeatures = new HashMap<>(resources(DIMENSION.resolve("data"), "worldgen/feature"));
        configuredFeatures.putAll(resources(DIMENSION.resolve("data"), "worldgen/configured_feature"));
        for (Path biome : jsonFiles(DIMENSION.resolve("data"))) {
            String normalized = biome.toString().replace('\\', '/');
            if (!normalized.contains("/worldgen/biome/") || normalized.contains("/tags/")) continue;
            JsonObject root = JsonParser.parseString(Files.readString(biome)).getAsJsonObject();
            JsonArray stages = root.getAsJsonArray("features");
            for (JsonElement stage : stages) {
                for (JsonElement reference : stage.getAsJsonArray()) {
                    String placedId = resourceId(reference.getAsString());
                    Path placedFile = placedFeatures.get(placedId);
                    assertTrue(placedFile != null, biome + " references missing placed feature " + placedId);
                    JsonObject placed = JsonParser.parseString(Files.readString(placedFile)).getAsJsonObject();
                    String featureId = resourceId(placed.get("feature").getAsString());
                    assertTrue(featureId.startsWith("minecraft:") || configuredFeatures.containsKey(featureId),
                            placedFile + " references missing configured feature " + featureId);
                }
            }
        }
    }

    @Test
    void siftslateOreFeaturesAreWiredIntoEveryOreBearingBiome() throws IOException {
        // Step index 6 is the "ore" generation step (after structure/carver/liquid/misc/
        // feature/decoration). The Java stand-in veins in SiftEcologyManager skip themselves
        // when this pack is enabled, so every biome that should contain ore MUST list these
        // placed features here - otherwise disabling the Java fallback would leave it barren.
        Map<String, Path> placedFeatures = resources(DIMENSION.resolve("data"), "worldgen/placed_feature");
        Set<String> required = Set.of(
                "thesift:siftslate_coal_ore", "thesift:siftslate_coal_ore_surface",
                "thesift:siftslate_diamond_ore", "thesift:siftslate_emerald_ore",
                "thesift:siftslate_charoite_ore", "thesift:siftslate_siftite_ore");
        int oreBiomes = 0;
        for (Path biome : jsonFiles(DIMENSION.resolve("data"))) {
            String normalized = biome.toString().replace('\\', '/');
            if (!normalized.contains("/worldgen/biome/")) continue;
            JsonObject root = JsonParser.parseString(Files.readString(biome)).getAsJsonObject();
            JsonArray stages = root.getAsJsonArray("features");
            Set<String> referenced = new HashSet<>();
            for (JsonElement stage : stages) {
                for (JsonElement reference : stage.getAsJsonArray()) {
                    referenced.add(resourceId(reference.getAsString()));
                }
            }
            boolean hasAnyRequired = !Collections.disjoint(referenced, required);
            if (!hasAnyRequired) continue;
            oreBiomes++;
            assertTrue(referenced.containsAll(required),
                    biome + " lists some siftslate ore features but is missing "
                            + required.stream().filter(id -> !referenced.contains(id)).toList());
            for (String id : required) {
                assertTrue(placedFeatures.containsKey(id), "missing placed feature JSON for " + id);
            }
        }
        assertTrue(oreBiomes >= 10,
                "expected at least 10 ore-bearing biomes after wiring ores into all 15, found " + oreBiomes);
    }

    private static Set<String> biomeIds(Path pack) throws IOException {
        Map<String, Path> files = resources(pack.resolve("data"), "worldgen/biome");
        return files.keySet();
    }

    private static Map<String, Path> resources(Path dataRoot, String resourceDirectory) throws IOException {
        Map<String, Path> result = new HashMap<>();
        for (Path file : jsonFiles(dataRoot)) {
            String normalized = file.toString().replace('\\', '/');
            if (normalized.contains("/tags/")) continue;
            String marker = "/" + resourceDirectory + "/";
            int markerIndex = normalized.indexOf(marker);
            if (markerIndex < 0) continue;
            String namespace = normalized.substring(dataRoot.toString().replace('\\', '/').length() + 1, markerIndex);
            String path = normalized.substring(markerIndex + marker.length(), normalized.length() - ".json".length());
            result.put(namespace + ":" + path, file);
        }
        return result;
    }

    private static java.util.List<Path> jsonFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".json")).toList();
        }
    }

    private static String resourceId(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }
}

