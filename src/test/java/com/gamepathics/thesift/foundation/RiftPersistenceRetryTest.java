package com.gamepathics.thesift.foundation;

import com.google.gson.Gson;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real save/load roundtrip for rifts.json (review P1): a rift whose world has not loaded yet
 * must stay pending and be restored on a later pass — never dropped, never duplicated, and
 * never restored without its gate metadata.
 */
class RiftPersistenceRetryTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private RiftManager manager;
    private Path saveFile;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("providers.strict", false);
        saveFile = plugin.getDataFolder().toPath().resolve("rifts.json");
        Files.createDirectories(saveFile.getParent());
        Files.deleteIfExists(saveFile);
        manager = new RiftManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RiftManager.RiftSaveEntry entry(String world, String exitWorld) {
        RiftManager.RiftSaveEntry entry = new RiftManager.RiftSaveEntry();
        entry.id = UUID.randomUUID().toString();
        entry.world = world;
        entry.cx = 10;
        entry.cy = 40;
        entry.cz = 10;
        entry.exitWorld = exitWorld;
        entry.ex = -10;
        entry.ey = 40;
        entry.ez = -10;
        entry.portal = true;
        entry.expiresAt = -1L;
        return entry;
    }

    private void writeSave(RiftManager.RiftSaveEntry... entries) throws Exception {
        Files.writeString(saveFile, new Gson().toJson(entries));
    }

    @Test
    void pendingWorldDefersRestoreThenRestoresOnRetry() throws Exception {
        server.addSimpleWorld("overworld");
        writeSave(entry("overworld", "sift_dimension"));

        manager.loadRifts();
        assertEquals(0, manager.activeRiftCount());
        assertTrue(Files.exists(saveFile), "file must be preserved while a world is missing");

        server.addSimpleWorld("sift_dimension");
        manager.loadRifts();
        assertEquals(1, manager.activeRiftCount());
        assertEquals(1, manager.activeRiftIds().size());
        assertFalse(Files.exists(saveFile), "fully restored save must be consumed");
    }

    @Test
    void missingGateArrivalWorldDefersTheWholeEntry() throws Exception {
        server.addSimpleWorld("overworld");
        server.addSimpleWorld("sift_dimension");
        RiftManager.RiftSaveEntry gated = entry("overworld", "sift_dimension");
        gated.gateMinX = 0.0;
        gated.gateMinY = 40.0;
        gated.gateMinZ = 0.0;
        gated.gateMaxX = 20.0;
        gated.gateMaxY = 48.0;
        gated.gateMaxZ = 6.0;
        // The arrival world has not loaded yet (a later WorldLoadEvent would create it).
        gated.gateArrivalWorld = "sift_dimension_pending";
        gated.gateArrivalX = 5;
        gated.gateArrivalY = 41;
        gated.gateArrivalZ = 5;
        writeSave(gated);

        manager.loadRifts();
        assertEquals(0, manager.activeRiftCount(),
                "a rift must not be restored without its gate arrival world");
        assertTrue(Files.exists(saveFile));

        server.addSimpleWorld("sift_dimension_pending");
        manager.loadRifts();
        assertEquals(1, manager.activeRiftCount());
        assertFalse(Files.exists(saveFile));

        RiftManager.RiftInstance rift = manager.activeRift(manager.activeRiftIds().get(0));
        assertNotNull(rift);
        assertTrue(rift.inGate(new Location(server.getWorld("overworld"), 10.5, 41.9, 3.5)),
                "gate metadata must survive the deferred restore");
    }

    @Test
    void partialRetryDoesNotDuplicateAlreadyRestoredEntries() throws Exception {
        server.addSimpleWorld("overworld");
        writeSave(entry("overworld", "overworld"), entry("overworld", "sift_dimension"));

        manager.loadRifts();
        assertEquals(1, manager.activeRiftCount());

        // Retry while the second rift's world is still missing: the restored one must not
        // be added twice and the file must stay pending.
        manager.loadRifts();
        assertEquals(1, manager.activeRiftCount());
        assertTrue(Files.exists(saveFile));

        server.addSimpleWorld("sift_dimension");
        manager.loadRifts();
        assertEquals(2, manager.activeRiftCount());
        assertFalse(Files.exists(saveFile));
    }

    @Test
    void fullyExpiredSaveIsConsumedWithoutRestoringAnything() throws Exception {
        server.addSimpleWorld("overworld");
        RiftManager.RiftSaveEntry expired = entry("overworld", "overworld");
        expired.expiresAt = 1L; // long in the past
        expired.portal = false;
        writeSave(expired);

        manager.loadRifts();
        assertEquals(0, manager.activeRiftCount());
        assertFalse(Files.exists(saveFile),
                "an all-expired save must be consumed (the old restored==entries.length check never fired here)");
    }

    @Test
    void missingFileLoadsHarmlesslyTwice() {
        manager.loadRifts();
        assertEquals(0, manager.activeRiftCount());
        manager.loadRifts();
        assertEquals(0, manager.activeRiftCount());
        assertNull(manager.activeRift(UUID.randomUUID()));
    }
}
