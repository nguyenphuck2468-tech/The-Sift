package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandSuggestionsTest {
    @Test
    void suggestsTheNewContentAndPlayersForGiveAndSummon() {
        assertEquals(List.of("sonorous_deepslate"), TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"give", "son"}, List.of("Alex")));
        assertEquals(List.of("Alex"), TheSiftFoundationPlugin.commandSuggestions(
                new String[]{"summon", "singer", "A"}, List.of("Alex", "Steve")));
    }
}
