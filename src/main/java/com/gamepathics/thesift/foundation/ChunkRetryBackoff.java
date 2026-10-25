package com.gamepathics.thesift.foundation;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

/** Persistent exponential retry schedule for failed chunk decoration. */
final class ChunkRetryBackoff {
    private static final long BASE_DELAY_MILLIS = 60_000L;
    private static final long MAX_DELAY_MILLIS = 6 * 60 * 60 * 1000L;
    private static final int MAX_ATTEMPT_COUNTER = 32;

    private ChunkRetryBackoff() {}

    static boolean isDeferred(Chunk chunk, NamespacedKey retryAtKey, long nowMillis) {
        Long retryAt = chunk.getPersistentDataContainer().get(retryAtKey, PersistentDataType.LONG);
        return retryAt != null && retryAt > nowMillis;
    }

    static long recordFailure(Chunk chunk, NamespacedKey attemptsKey, NamespacedKey retryAtKey,
                              long nowMillis) {
        var data = chunk.getPersistentDataContainer();
        Integer oldAttempts = data.get(attemptsKey, PersistentDataType.INTEGER);
        int attempts = Math.min(MAX_ATTEMPT_COUNTER, (oldAttempts == null ? 0 : oldAttempts) + 1);
        data.set(attemptsKey, PersistentDataType.INTEGER, attempts);
        int shift = Math.min(attempts - 1, 9);
        long delay = Math.min(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS << shift);
        data.set(retryAtKey, PersistentDataType.LONG, nowMillis + delay);
        return delay;
    }

    static void clear(Chunk chunk, NamespacedKey attemptsKey, NamespacedKey retryAtKey) {
        chunk.getPersistentDataContainer().remove(attemptsKey);
        chunk.getPersistentDataContainer().remove(retryAtKey);
    }

    static int attempts(Chunk chunk, NamespacedKey attemptsKey) {
        Integer attempts = chunk.getPersistentDataContainer().get(attemptsKey, PersistentDataType.INTEGER);
        return attempts == null ? 0 : attempts;
    }
}
