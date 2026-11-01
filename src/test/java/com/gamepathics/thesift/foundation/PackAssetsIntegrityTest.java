package com.gamepathics.thesift.foundation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled resource pack carries the reference mod's imported assets (see
 * THIRD_PARTY_LICENSES/CREDITS.md). This test pins the pack together: every item
 * definition's model reference resolves, every model's fully-namespaced texture reference
 * resolves, and every sounds.json entry points at a bundled OGG. A broken reference here
 * means invisible textures or silent note blocks on real clients.
 */
class PackAssetsIntegrityTest {
    private static final Path PACK = Path.of("src", "main", "resources", "pack");

    /** Resolves a namespaced ref (thesift:item/x) to a file under the pack's assets. */
    private static Path asset(String ref, String kind) {
        int colon = ref.indexOf(':');
        String namespace = colon >= 0 ? ref.substring(0, colon) : "minecraft";
        String value = colon >= 0 ? ref.substring(colon + 1) : ref;
        return PACK.resolve("assets").resolve(namespace).resolve(kind).resolve(value);
    }

    private static boolean existsWithSuffix(Path base, String... suffixes) {
        if (Files.exists(base)) return true;
        for (String suffix : suffixes) {
            if (Files.exists(base.resolveSibling(base.getFileName() + suffix))) return true;
        }
        return false;
    }

    /** Collects every string value stored under the given key, at any depth. */
    private static List<String> stringsUnder(JsonElement element, String key) {
        List<String> found = new ArrayList<>();
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals(key) && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isString()) {
                    found.add(entry.getValue().getAsString());
                }
                found.addAll(stringsUnder(entry.getValue(), key));
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) found.addAll(stringsUnder(child, key));
        }
        return found;
    }

    @Test
    void packRootIsPresent() {
        assertTrue(Files.exists(PACK.resolve("pack.mcmeta")), "pack.mcmeta must ship in the pack");
        assertTrue(Files.isDirectory(PACK.resolve("assets/thesift/textures")),
                "the imported mod textures must ship");
        assertTrue(Files.exists(PACK.resolve("assets/thesift/sounds.json")),
                "the imported mod sounds.json must ship");
    }

    @Test
    void everyItemDefinitionResolvesItsModels() throws Exception {
        Path items = PACK.resolve("assets/thesift/items");
        assertTrue(Files.isDirectory(items), "the pack must ship item definitions");
        List<String> broken = new ArrayList<>();
        try (var stream = Files.walk(items)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject definition = new Gson().fromJson(Files.readString(file), JsonObject.class);
                for (String model : stringsUnder(definition, "model")) {
                    if (!model.startsWith("thesift:")) continue; // vanilla refs are fine
                    if (!existsWithSuffix(asset(model, "models"), ".json")) {
                        broken.add(file.getFileName() + " -> missing model " + model);
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), "broken item model references: " + broken);
    }

    @Test
    void everyModelTextureRefResolves() throws Exception {
        Path models = PACK.resolve("assets/thesift/models");
        assertTrue(Files.isDirectory(models));
        List<String> broken = new ArrayList<>();
        try (var stream = Files.walk(models)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject model = new Gson().fromJson(Files.readString(file), JsonObject.class);
                if (!model.has("textures")) continue;
                for (var entry : model.getAsJsonObject("textures").entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) continue;
                    String ref = entry.getValue().getAsString();
                    if (!ref.startsWith("thesift:")) continue; // partial/vanilla refs depend on parents
                    if (!existsWithSuffix(asset(ref, "textures"), ".png", ".png.mcmeta")) {
                        broken.add(file.getFileName() + " -> missing texture " + ref);
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), "broken model texture references: " + broken);
    }

    @Test
    void everySoundEventResolvesToABundledFile() throws Exception {
        Path soundsJson = PACK.resolve("assets/thesift/sounds.json");
        assertTrue(Files.exists(soundsJson));
        JsonObject sounds = new Gson().fromJson(Files.readString(soundsJson), JsonObject.class);
        assertTrue(sounds.entrySet().size() >= 40, "expected the mod's sound catalogue, got "
                + sounds.entrySet().size() + " events");
        List<String> broken = new ArrayList<>();
        for (var entry : sounds.entrySet()) {
            for (JsonElement sound : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonPrimitive() ? sound.getAsString()
                        : sound.getAsJsonObject().get("name").getAsString();
                if (name.contains(":")) continue; // vanilla references
                Path ogg = PACK.resolve("assets/thesift/sounds").resolve(name + ".ogg");
                Path wav = PACK.resolve("assets/thesift/sounds").resolve(name + ".wav");
                if (!Files.exists(ogg) && !Files.exists(wav)) {
                    broken.add(entry.getKey() + " -> missing sound file " + name);
                }
            }
        }
        assertTrue(broken.isEmpty(), "broken sound references: " + broken);
    }
}
