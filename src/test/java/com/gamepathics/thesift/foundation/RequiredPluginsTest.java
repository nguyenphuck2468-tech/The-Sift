package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredPluginsTest {
    private static final Map<String, List<String>> ANY = Map.of(
            "items", List.of("Nexo", "ItemsAdder"), "mobs", List.of("MythicMobs", "service:mob"));

    @Test
    void satisfiedWhenAllOfAndEveryGroupHaveAMember() {
        Set<String> installed = Set.of("ModelEngine", "Nexo", "service:mob");
        assertTrue(RequiredPlugins.missing(List.of("ModelEngine"), ANY, installed::contains).isEmpty());
    }

    @Test
    void reportsMissingRequiredPluginAndEmptyGroups() {
        List<String> missing = RequiredPlugins.missing(List.of("ModelEngine"), ANY, token -> false);
        assertEquals(3, missing.size());
        assertTrue(missing.contains("ModelEngine (required)"));
        assertTrue(missing.stream().anyMatch(line -> line.contains("for items")));
        assertTrue(missing.stream().anyMatch(line -> line.contains("for mobs")));
    }

    @Test
    void oneMemberIsEnoughForAGroup() {
        Set<String> installed = Set.of("ItemsAdder", "MythicMobs");
        assertTrue(RequiredPlugins.missing(List.of(), ANY, installed::contains).isEmpty());
    }
}
