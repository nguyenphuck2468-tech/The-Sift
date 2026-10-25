package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalSongTrackerTest {
    private static final int[] SONG = {1, 3, 7, 6, 5, 2, 4, 8};

    @Test
    void successfulSongStartsGlobalCooldown() {
        PortalSongTracker tracker = new PortalSongTracker();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < SONG.length - 1; i++) {
            assertEquals(PortalSongTracker.Result.INCOMPLETE,
                    tracker.play(player, SONG[i], 1_000L + i, 60_000L, 30_000L));
        }
        assertEquals(PortalSongTracker.Result.COMPLETE,
                tracker.play(player, SONG[SONG.length - 1], 2_000L, 60_000L, 30_000L));
        tracker.startCooldown(2_000L, 30_000L);
        assertEquals(PortalSongTracker.Result.COOLDOWN,
                tracker.play(UUID.randomUUID(), SONG[0], 2_001L, 60_000L, 30_000L));
        assertEquals(PortalSongTracker.Result.INCOMPLETE,
                tracker.play(UUID.randomUUID(), SONG[0], 32_001L, 60_000L, 30_000L));
    }

    @Test
    void incompleteProgressExpiresAfterConfiguredIdleTime() {
        PortalSongTracker tracker = new PortalSongTracker();
        UUID player = UUID.randomUUID();
        assertEquals(PortalSongTracker.Result.INCOMPLETE, tracker.play(player, SONG[0], 100L, 1_000L, 0L));
        tracker.expire(1_101L, 1_000L);
        assertEquals(0, tracker.progress(player));
    }

    @Test
    void quittingPlayerCanBeRemovedWithoutClearingOtherProgress() {
        PortalSongTracker tracker = new PortalSongTracker();
        UUID leaving = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        tracker.play(leaving, SONG[0], 100L, 60_000L, 0L);
        tracker.play(staying, SONG[0], 100L, 60_000L, 0L);
        tracker.remove(leaving);
        assertEquals(0, tracker.progress(leaving));
        assertEquals(1, tracker.progress(staying));
        assertEquals(1, tracker.activeProgressCount());
    }
}
