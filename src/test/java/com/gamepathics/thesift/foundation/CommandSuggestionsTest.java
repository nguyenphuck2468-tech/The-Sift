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
        org.junit.jupiter.api.Assertions.assertTrue(TheSiftFoundationPlugin.requiresAdminPermission(new String[]{"give", "rift_key", "Alex"}));
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
    void summonSuggestionsHideDisabledFanContent() {
        // Fan stand-ins are hidden unless their gate is on (review: the old list always
        // offered prism_warden, spire_tyrant, loomer... even with every fan flag off).
        var gateOn = TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"summon", ""}, List.of(), species -> true);
        var gateOff = TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"summon", ""}, List.of(),
                species -> species.equals("singer") || species.equals("blub") || species.equals("rift"));
        assertTrue(gateOn.contains("prism_warden") && gateOn.contains("loomer"));
        assertFalse(gateOff.contains("prism_warden"));
        assertFalse(gateOff.contains("loomer"));
        assertTrue(gateOff.contains("singer") && gateOff.contains("blub") && gateOff.contains("rift"),
                "core mobs and rift stay suggested");
    }
}
