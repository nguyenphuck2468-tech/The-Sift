package com.gamepathics.thesift.foundation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
