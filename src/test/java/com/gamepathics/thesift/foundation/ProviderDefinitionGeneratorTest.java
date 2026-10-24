package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderDefinitionGeneratorTest {
    private static final Path ROOT = Path.of("build/generated/provider-definitions");
    private static final Pattern IDS = Pattern.compile("\"id\"\\s*:\\s*\"([a-z0-9_]+)\"");

    @Test
    void generatedProviderFilesContainEveryCanonicalSliceId() throws Exception {
        String spec = Files.readString(Path.of("integrations/content-spec.json"));
        assertTrue(Files.readString(ROOT.resolve("content-spec.json")).equals(spec),
                "generator must retain a byte-identical canonical source copy");

        String itemsSection = section(spec, "items", "mobs");
        String mobsSection = section(spec, "mobs", "blocks");
        String blocksSection = section(spec, "blocks", null);
        for (String id : ids(itemsSection)) {
            assertContains(ROOT.resolve("nexo/items.yml"), id);
            assertContains(ROOT.resolve("itemsadder/items.yml"), id);
            assertContains(ROOT.resolve("oraxen/items.yml"), id);
        }
        for (String id : ids(blocksSection)) {
            assertContains(ROOT.resolve("nexo/items.yml"), id);
            assertContains(ROOT.resolve("itemsadder/items.yml"), id);
            assertContains(ROOT.resolve("oraxen/items.yml"), id);
        }
        for (String id : ids(mobsSection)) assertContains(ROOT.resolve("mythicmobs/TheSift.yml"), id);
    }

    private static String section(String spec, String name, String next) {
        String start = "\"" + name + "\"\\s*:\\s*\\[";
        String end = next == null ? "\\]\\s*}" : "\\]\\s*,\\s*\"" + next + "\"";
        Matcher matcher = Pattern.compile(start + "(.*?)" + end, Pattern.DOTALL).matcher(spec);
        if (!matcher.find()) throw new AssertionError("Missing content spec section: " + name);
        return matcher.group(1);
    }

    private static List<String> ids(String section) {
        Matcher matcher = IDS.matcher(section);
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private static void assertContains(Path path, String id) throws Exception {
        String generated = Files.readString(path);
        assertTrue(generated.contains(id + ":") || generated.contains("\"" + id + "\""),
                () -> path + " is missing canonical ID " + id);
    }
}
