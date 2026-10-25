package com.gamepathics.thesift.foundation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks transient per-player portal melodies and a server-wide successful-song cooldown. */
final class PortalSongTracker {
    enum Result { INCOMPLETE, COMPLETE, WRONG, COOLDOWN }

    private static final List<Integer> SONG = List.of(1, 3, 7, 6, 5, 2, 4, 8);
    private final Map<UUID, Progress> progressByPlayer = new HashMap<>();
    private long nextAllowedSongAtMillis;

    Result play(UUID playerId, int note, long nowMillis, long timeoutMillis, long cooldownMillis) {
        expire(nowMillis, timeoutMillis);
        if (nowMillis < nextAllowedSongAtMillis) return Result.COOLDOWN;
        Progress progress = progressByPlayer.computeIfAbsent(playerId, ignored -> new Progress());
        progress.lastActivityMillis = nowMillis;
        int expected = SONG.get(progress.notes.size());
        if (note != expected) {
            progress.notes.clear();
            if (note == SONG.getFirst()) progress.notes.add(note);
            return Result.WRONG;
        }
        progress.notes.add(note);
        if (progress.notes.size() != SONG.size()) return Result.INCOMPLETE;
        progressByPlayer.remove(playerId);
        return Result.COMPLETE;
    }

    void startCooldown(long nowMillis, long cooldownMillis) {
        nextAllowedSongAtMillis = nowMillis + Math.max(0L, cooldownMillis);
    }

    void expire(long nowMillis, long timeoutMillis) {
        long timeout = Math.max(0L, timeoutMillis);
        progressByPlayer.entrySet().removeIf(entry -> nowMillis - entry.getValue().lastActivityMillis > timeout);
    }

    void remove(UUID playerId) {
        progressByPlayer.remove(playerId);
    }

    int activeProgressCount() {
        return progressByPlayer.size();
    }

    int progress(UUID playerId) {
        Progress progress = progressByPlayer.get(playerId);
        return progress == null ? 0 : progress.notes.size();
    }

    private static final class Progress {
        private final List<Integer> notes = new ArrayList<>();
        private long lastActivityMillis;
    }
}
