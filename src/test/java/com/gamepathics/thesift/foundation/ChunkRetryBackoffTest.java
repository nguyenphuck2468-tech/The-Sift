package com.gamepathics.thesift.foundation;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class ChunkRetryBackoffTest {
    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void failedChunkRetryIsPersistedAndUsesExponentialDelay() {
        var chunk = server.addSimpleWorld("retry-test").getChunkAt(0, 0);
        NamespacedKey attempts = new NamespacedKey(plugin, "retry_attempts");
        NamespacedKey retryAt = new NamespacedKey(plugin, "retry_at");

        assertEquals(60_000L, ChunkRetryBackoff.recordFailure(chunk, attempts, retryAt, 1_000L));
        assertEquals(1, ChunkRetryBackoff.attempts(chunk, attempts));
        assertTrue(ChunkRetryBackoff.isDeferred(chunk, retryAt, 60_999L));
        assertFalse(ChunkRetryBackoff.isDeferred(chunk, retryAt, 61_000L));
        assertEquals(120_000L, ChunkRetryBackoff.recordFailure(chunk, attempts, retryAt, 61_000L));
    }

    @Test
    void successfulDecorationClearsPersistentRetryState() {
        var chunk = server.addSimpleWorld("retry-clear-test").getChunkAt(0, 0);
        NamespacedKey attempts = new NamespacedKey(plugin, "retry_attempts");
        NamespacedKey retryAt = new NamespacedKey(plugin, "retry_at");
        ChunkRetryBackoff.recordFailure(chunk, attempts, retryAt, 1_000L);

        ChunkRetryBackoff.clear(chunk, attempts, retryAt);

        assertEquals(0, ChunkRetryBackoff.attempts(chunk, attempts));
        assertFalse(chunk.getPersistentDataContainer().has(retryAt, PersistentDataType.LONG));
    }
}
