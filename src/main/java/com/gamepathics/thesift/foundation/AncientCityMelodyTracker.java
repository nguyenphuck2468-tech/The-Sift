package com.gamepathics.thesift.foundation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/** Per-player, per-gate melody progress with timeout and quit cleanup. */
final class AncientCityMelodyTracker {
    enum Result { INCOMPLETE, COMPLETE, WRONG }
    static final int[] SONG = {1, 3, 7, 6, 5, 2, 4, 8};
    private final Map<Key, Progress> progress = new HashMap<>();

    Result play(UUID player, String gate, int note, String noteBlock, long nowMillis, long timeoutMillis) {
        Key key = new Key(player, gate);
        Progress current = progress.get(key);
        if (current != null && nowMillis - current.lastAt > timeoutMillis) {
            progress.remove(key);
            current = null;
        }
        int next = current == null ? 0 : current.next;
        if (note != SONG[next] || current != null && current.usedBlocks.contains(noteBlock)) {
            progress.remove(key);
            return Result.WRONG;
        }
        if (next == SONG.length - 1) {
            progress.remove(key);
            return Result.COMPLETE;
        }
        Set<String> used = current == null ? new HashSet<>() : new HashSet<>(current.usedBlocks);
        used.add(noteBlock);
        progress.put(key, new Progress(next + 1, nowMillis, used));
        return Result.INCOMPLETE;
    }

    int progress(UUID player, String gate) {
        Progress current = progress.get(new Key(player, gate));
        return current == null ? 0 : current.next;
    }

    void removePlayer(UUID player) { progress.keySet().removeIf(key -> key.player.equals(player)); }
    void clear() { progress.clear(); }

    void expire(long nowMillis, long timeoutMillis) {
        progress.entrySet().removeIf(entry -> nowMillis - entry.getValue().lastAt > timeoutMillis);
    }

    private record Key(UUID player, String gate) {}
    private record Progress(int next, long lastAt, Set<String> usedBlocks) {}
}
