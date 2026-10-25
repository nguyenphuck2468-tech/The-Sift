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
            "thesift:bulb_forest", "thesift:crystal_spires", "thesift:pink_meadows", "thesift:shattered_peaks"
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
        assertEquals(CANONICAL_BIOMES, dimensionBiomes.stream().filter(CANONICAL_BIOMES::contains).collect(java.util.stream.Collectors.toSet()));
        assertTrue(progressionBiomes.isEmpty(), "Progression pack must not define biome JSON");
        assertTrue(Files.isDirectory(PROGRESSION.resolve("data/thesift/advancement")), "Progression advancements must remain");
    }

    @Test
    void biomeFeatureReferencesResolveThroughPlacedAndConfiguredFeatures() throws IOException {
        Map<String, Path> placedFeatures = resources(DIMENSION.resolve("data"), "worldgen/placed_feature");
        Map<String, Path> configuredFeatures = resources(DIMENSION.resolve("data"), "worldgen/feature");
        for (Path biome : jsonFiles(DIMENSION.resolve("data"))) {
            if (!biome.toString().replace('\\', '/').contains("/worldgen/biome/")) continue;
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
