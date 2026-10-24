package com.gamepathics.thesift.foundation;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SiftEcologyChunkMarkerTest {
    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void failedDecorationLeavesNoCompletionMarkerAndCanRetry() {
        var world = server.addSimpleWorld("sift-test");
        var chunk = world.getChunkAt(0, 0);
        NamespacedKey marker = new NamespacedKey(plugin, "sift_ecology_generated");
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> SiftEcologyManager.decorateChunkOnce(
                chunk, marker, () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("simulated mid-decoration failure");
                }));
        assertFalse(chunk.getPersistentDataContainer().has(marker, PersistentDataType.BYTE));

        assertTrue(SiftEcologyManager.decorateChunkOnce(chunk, marker, attempts::incrementAndGet));
        assertEquals((byte) 2, chunk.getPersistentDataContainer().get(marker, PersistentDataType.BYTE));
        assertFalse(SiftEcologyManager.decorateChunkOnce(chunk, marker,
                () -> fail("completed chunks must not run decoration again")));
        assertEquals(2, attempts.get());
    }
}
