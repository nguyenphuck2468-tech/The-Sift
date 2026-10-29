package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AncientCityMelodyTrackerTest {
    @Test
    void tracksCorrectMelodyByGateAndPlayer() {
        AncientCityMelodyTracker tracker = new AncientCityMelodyTracker();
        UUID player = UUID.randomUUID();
        assertEquals(AncientCityMelodyTracker.Result.INCOMPLETE, tracker.play(player, "gate-a", 1, "n1", 1, 60_000));
        assertEquals(1, tracker.progress(player, "gate-a"));
        assertEquals(0, tracker.progress(player, "gate-b"));
        for (int index = 1; index < AncientCityMelodyTracker.SONG.length - 1; index++)
            assertEquals(AncientCityMelodyTracker.Result.INCOMPLETE,
                    tracker.play(player, "gate-a", AncientCityMelodyTracker.SONG[index], "n" + (index + 1), index + 1L, 60_000));
        assertEquals(AncientCityMelodyTracker.Result.COMPLETE, tracker.play(player, "gate-a", 8, "n8", 20, 60_000));
        assertEquals(0, tracker.progress(player, "gate-a"));
    }

    @Test
    void wrongNoteAndTimeoutResetOnlyThatPlayersProgress() {
        AncientCityMelodyTracker tracker = new AncientCityMelodyTracker();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tracker.play(first, "gate-a", 1, "n1", 10, 100);
        tracker.play(second, "gate-a", 1, "n1", 10, 100);
        assertEquals(AncientCityMelodyTracker.Result.WRONG, tracker.play(first, "gate-a", 2, "n2", 11, 100));
        tracker.expire(111, 100);
        assertEquals(0, tracker.progress(first, "gate-a"));
        assertEquals(0, tracker.progress(second, "gate-a"));
    }

    @Test
    void quittingPlayerProgressCanBeRemovedWithoutAffectingOthers() {
        AncientCityMelodyTracker tracker = new AncientCityMelodyTracker();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tracker.play(first, "gate-a", 1, "n1", 1, 1000);
        tracker.play(second, "gate-a", 1, "n1", 1, 1000);
        tracker.removePlayer(first);
        assertEquals(0, tracker.progress(first, "gate-a"));
        assertEquals(1, tracker.progress(second, "gate-a"));
    }

    @Test
    void requiresEightDistinctNoteBlocks() {
        AncientCityMelodyTracker tracker = new AncientCityMelodyTracker();
        UUID player = UUID.randomUUID();
        assertEquals(AncientCityMelodyTracker.Result.INCOMPLETE, tracker.play(player, "gate-a", 1, "same", 1, 1000));
        assertEquals(AncientCityMelodyTracker.Result.WRONG, tracker.play(player, "gate-a", 3, "same", 2, 1000));
    }
}
