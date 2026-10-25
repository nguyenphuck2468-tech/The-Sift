package com.gamepathics.thesift.foundation;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.Material;
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

    @Test
    void strictBlockPlacementDoesNotUseVanillaFallbackAndReportsExactLocation() {
        var world = server.addSimpleWorld("sift-strict-placement");
        var block = world.getBlockAt(3, 70, 5);
        block.setType(Material.AIR);
        java.util.List<String> failures = new java.util.ArrayList<>();

        assertThrows(IllegalStateException.class, () -> SiftEcologyManager.placeBlock(
                block, "sift_grass", Material.MOSS_BLOCK, true, () -> false, failures::add));
        assertEquals(Material.AIR, block.getType());
        assertEquals(1, failures.size());
        assertTrue(failures.getFirst().contains("sift_grass"));
        assertTrue(failures.getFirst().contains("3,70,5"));
    }

    @Test
    void nonStrictBlockPlacementKeepsVanillaFallback() {
        var world = server.addSimpleWorld("sift-dev-placement");
        var block = world.getBlockAt(3, 70, 5);
        block.setType(Material.AIR);
        assertTrue(SiftEcologyManager.placeBlock(block, "sift_grass", Material.MOSS_BLOCK,
                false, () -> false, ignored -> fail("dev fallback must not count as strict failure")));
        assertEquals(Material.MOSS_BLOCK, block.getType());
    }

    @Test
    void retrySkipsProviderBlockAlreadyPlacedByEarlierPartialAttempt() {
        var block = server.addSimpleWorld("sift-idempotent-retry").getBlockAt(3, 70, 5);
        AtomicInteger placeCalls = new AtomicInteger();

        assertTrue(SiftEcologyManager.placeBlock(block, "sift_grass", Material.MOSS_BLOCK,
                true, () -> true, () -> { placeCalls.incrementAndGet(); return true; },
                ignored -> fail("recognized provider block should be kept")));

        assertEquals(0, placeCalls.get());
    }

    @Test
    void missingStrictProviderClearsChunkMarkerAndRetryCanComplete() {
        var world = server.addSimpleWorld("sift-strict-retry");
        var chunk = world.getChunkAt(0, 0);
        var block = world.getBlockAt(3, 70, 5);
        NamespacedKey marker = new NamespacedKey(plugin, "sift_ecology_generated");
        block.setType(Material.AIR);

        assertThrows(IllegalStateException.class, () -> SiftEcologyManager.decorateChunkOnce(chunk, marker,
                () -> SiftEcologyManager.placeBlock(block, "sift_grass", Material.MOSS_BLOCK,
                        true, () -> false, ignored -> {})));
        assertFalse(chunk.getPersistentDataContainer().has(marker, PersistentDataType.BYTE));
        assertEquals(Material.AIR, block.getType());

        assertTrue(SiftEcologyManager.decorateChunkOnce(chunk, marker,
                () -> assertTrue(SiftEcologyManager.placeBlock(block, "sift_grass", Material.MOSS_BLOCK,
                        true, () -> { block.setType(Material.DIAMOND_BLOCK); return true; }, ignored -> {}))));
        assertEquals((byte) 2, chunk.getPersistentDataContainer().get(marker, PersistentDataType.BYTE));
        assertEquals(Material.DIAMOND_BLOCK, block.getType());
    }
}
