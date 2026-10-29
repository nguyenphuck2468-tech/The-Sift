package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandSuggestionsTest {
    @Test
    void travelCommandsArePlayerFacingWithoutAdminPermission() {
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"locate"}));
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"dimension"}));
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"enter"}));
        org.junit.jupiter.api.Assertions.assertFalse(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"return"}));
        org.junit.jupiter.api.Assertions.assertTrue(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"give", "rift_key", "Alex"}));
        org.junit.jupiter.api.Assertions.assertTrue(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"doctor"}));
    }

    @Test
    void suggestsTheNewContentAndPlayersForGiveAndSummon() {
        assertEquals(List.of("sonorous_deepslate"), TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"give", "son"}, List.of("Alex")));
        assertEquals(List.of("Alex"), TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"summon", "singer", "A"}, List.of("Alex", "Steve")));
    }
}
