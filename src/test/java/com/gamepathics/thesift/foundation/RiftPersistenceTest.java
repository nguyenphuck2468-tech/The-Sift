package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** P6.4: expired rift entries are skipped, portal entries with -1 never expire. */
class RiftPersistenceTest {
    @Test
    void skipExpiredDropsPastEntriesAndKeepsPortalNeverExpire() {
        RiftManager.RiftSaveEntry expired = new RiftManager.RiftSaveEntry();
        expired.id = "expired";
        expired.expiresAt = 1000L;

        RiftManager.RiftSaveEntry live = new RiftManager.RiftSaveEntry();
        live.id = "live";
        live.expiresAt = System.currentTimeMillis() + 60_000L;

        RiftManager.RiftSaveEntry portal = new RiftManager.RiftSaveEntry();
        portal.id = "portal";
        portal.portal = true;
        portal.expiresAt = -1L;

        var kept = RiftManager.skipExpired(new RiftManager.RiftSaveEntry[]{expired, live, portal, null},
                System.currentTimeMillis());

        assertEquals(2, kept.size());
        assertEquals("live", kept.get(0).id);
        assertEquals("portal", kept.get(1).id);
    }
}
