package com.gamepathics.thesift.foundation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the reference mod's SonorousConsoles: every group ("console") of Sonorous note blocks keeps a
 * sliding window of the last eight played notes (no reset on a wrong note). The opening melody is
 * 1,3,7,6,5,2,4,8 and the closing melody is the same sequence reversed.
 */
final class SonorousConsoleTracker {
    enum Match { NONE, OPEN, CLOSE }

    record Note(String world, int x, int y, int z, int index) {}

    static final int[] OPEN_SEQUENCE = {1, 3, 7, 6, 5, 2, 4, 8};
    static final int[] CLOSE_SEQUENCE = {8, 4, 2, 5, 6, 7, 3, 1};

    private final Map<String, Deque<Note>> buffers = new HashMap<>();
    private List<Note> lastMatch = List.of();

    Match record(String consoleKey, Note note) {
        Deque<Note> buffer = buffers.computeIfAbsent(consoleKey, ignored -> new ArrayDeque<>());
        buffer.addLast(note);
        while (buffer.size() > OPEN_SEQUENCE.length) buffer.removeFirst();
        if (matches(buffer, OPEN_SEQUENCE)) {
            lastMatch = List.copyOf(buffer);
            buffers.remove(consoleKey);
            return Match.OPEN;
        }
        if (matches(buffer, CLOSE_SEQUENCE)) {
            lastMatch = List.copyOf(buffer);
            buffers.remove(consoleKey);
            return Match.CLOSE;
        }
        return Match.NONE;
    }

    /** Notes (in play order) of the most recent OPEN or CLOSE match. */
    List<Note> lastMatch() {
        return lastMatch;
    }

    void clear() {
        buffers.clear();
        lastMatch = List.of();
    }

    private static boolean matches(Deque<Note> buffer, int[] target) {
        if (buffer.size() != target.length) return false;
        int i = 0;
        for (Note note : buffer) {
            if (note.index() != target[i++]) return false;
        }
        return true;
    }
}
