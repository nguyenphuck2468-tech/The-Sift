package com.gamepathics.thesift.foundation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Hard dependency rules. The Sift depends fully on provider plugins: every {@code all-of} token must be
 * present and every {@code any-of} group needs at least one present token. A token is a plugin name, or
 * {@code service:item}, {@code service:block}, {@code service:mob} for a registered Sift provider service.
 */
final class RequiredPlugins {
    static final List<String> DEFAULT_ALL_OF = List.of("ModelEngine");
    static final Map<String, List<String>> DEFAULT_ANY_OF = defaultAnyOf();

    private RequiredPlugins() {}

    private static Map<String, List<String>> defaultAnyOf() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("items", List.of("Nexo", "ItemsAdder", "Oraxen", "service:item"));
        groups.put("blocks", List.of("Nexo", "ItemsAdder", "Oraxen", "service:block"));
        groups.put("mobs", List.of("MythicMobs", "ItemsAdder", "EliteMobs", "service:mob"));
        return groups;
    }

    /** Human-readable descriptions of every unmet rule; empty when all requirements are satisfied. */
    static List<String> missing(FileConfiguration config, Predicate<String> present) {
        List<String> allOf = config.contains("requirements.all-of")
                ? config.getStringList("requirements.all-of") : DEFAULT_ALL_OF;
        Map<String, List<String>> anyOf = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("requirements.any-of");
        if (section == null) anyOf.putAll(DEFAULT_ANY_OF);
        else for (String group : section.getKeys(false)) anyOf.put(group, section.getStringList(group));
        return missing(allOf, anyOf, present);
    }

    static List<String> missing(List<String> allOf, Map<String, List<String>> anyOf, Predicate<String> present) {
        List<String> missing = new ArrayList<>();
        for (String token : allOf) {
            if (!present.test(token)) missing.add(token + " (required)");
        }
        for (Map.Entry<String, List<String>> group : anyOf.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            if (group.getValue().stream().noneMatch(present)) {
                missing.add("one of " + group.getValue() + " for " + group.getKey());
            }
        }
        return missing;
    }
}
