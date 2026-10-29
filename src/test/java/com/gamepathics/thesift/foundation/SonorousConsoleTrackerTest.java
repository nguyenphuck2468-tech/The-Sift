package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SonorousConsoleTrackerTest {
    private static SonorousConsoleTracker.Match play(SonorousConsoleTracker tracker, int... notes) {
        SonorousConsoleTracker.Match last = SonorousConsoleTracker.Match.NONE;
        for (int note : notes) last = tracker.record("c", new SonorousConsoleTracker.Note("w", note, 0, 0, note));
        return last;
    }

    @Test
    void openingMelodyMatches() {
        assertEquals(SonorousConsoleTracker.Match.OPEN, play(new SonorousConsoleTracker(), 1, 3, 7, 6, 5, 2, 4, 8));
    }

    @Test
    void closingMelodyIsTheReverse() {
        assertEquals(SonorousConsoleTracker.Match.CLOSE, play(new SonorousConsoleTracker(), 8, 4, 2, 5, 6, 7, 3, 1));
    }

    @Test
    void wrongNoteDoesNotResetTheSlidingWindow() {
        assertEquals(SonorousConsoleTracker.Match.OPEN,
                play(new SonorousConsoleTracker(), 5, 5, 1, 3, 7, 6, 5, 2, 4, 8));
    }

    @Test
    void incompleteMelodyDoesNotMatch() {
        assertEquals(SonorousConsoleTracker.Match.NONE, play(new SonorousConsoleTracker(), 1, 3, 7, 6, 5, 2, 4));
    }

    @Test
    void consolesAreIndependent() {
        SonorousConsoleTracker tracker = new SonorousConsoleTracker();
        for (int note : new int[]{1, 3, 7, 6}) tracker.record("a", new SonorousConsoleTracker.Note("w", 0, 0, 0, note));
        SonorousConsoleTracker.Match last = SonorousConsoleTracker.Match.NONE;
        for (int note : new int[]{5, 2, 4, 8}) last = tracker.record("b", new SonorousConsoleTracker.Note("w", 0, 0, 0, note));
        assertEquals(SonorousConsoleTracker.Match.NONE, last);
    }
}
