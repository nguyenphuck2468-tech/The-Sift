package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSuggestionsTest {
    @Test
    void travelCommandsArePlayerFacingWithoutAdminPermission() {
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"locate"}));
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"dimension"}));
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"enter"}));
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"return"}));
        org.junit.jupiter.api.Assertions.assertTrue(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"give", "siftite_ingot", "Alex"}));
        org.junit.jupiter.api.Assertions.assertTrue(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"doctor"}));
    }

    @Test
    void suggestsTheNewContentAndPlayersForGiveAndSummon() {
        assertEquals(List.of("sonorous_deepslate"), TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"give", "son"}, List.of("Alex"), species -> true));
        assertEquals(List.of("Alex"), TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"summon", "singer", "A"}, List.of("Alex", "Steve"), species -> true));
    }

    @Test
    void summonSuggestionsContainOnlyReferenceContent() {
        var suggestions = TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"summon", ""}, List.of(), species -> true);
        assertTrue(suggestions.containsAll(List.of("singer", "echo_golem", "dark_sniffer", "blub", "sifter", "rift")));
        assertFalse(suggestions.contains("prism_warden"));
        assertFalse(suggestions.contains("spire_tyrant"));
        assertFalse(suggestions.contains("loomer"));
    }
}
