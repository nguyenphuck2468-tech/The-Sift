package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression coverage for the wildlife index (review P1: Blub/Sifter were starved by stale buckets). */
class WildlifeIndexTest {
    @Test
    void refileMovesTheUuidFromTheVanillaTypeBucketToTheSpeciesBucket() {
        WildlifeIndex index = new WildlifeIndex();
        UUID blub = UUID.randomUUID();
        index.index("type:RABBIT", blub);

        index.refile("type:RABBIT", "blub", blub);

        assertTrue(index.bucket("blub").contains(blub), "species tick must see the refiled mob");
        assertFalse(index.bucket("type:RABBIT").contains(blub), "stale vanilla bucket must be emptied");
        assertTrue(index.bucket("type:RABBIT").isEmpty(), "empty bucket must be pruned");
    }

    @Test
    void scrubRemovesTheUuidFromEveryBucket() {
        WildlifeIndex index = new WildlifeIndex();
        UUID sniffer = UUID.randomUUID();
        index.index("dark_sniffer", sniffer);
        index.index("type:SNIFFER", sniffer);

        index.scrub(sniffer);

        assertEquals(List.of(), index.bucket("dark_sniffer"));
        assertEquals(List.of(), index.bucket("type:SNIFFER"));
        assertEquals(0, index.size());
    }

    @Test
    void bucketIsACopySoCallersCannotMutateInternalState() {
        WildlifeIndex index = new WildlifeIndex();
        UUID id = UUID.randomUUID();
        index.index("sifter", id);

        List<UUID> bucket = index.bucket("sifter");
        bucket.clear();

        assertEquals(List.of(id), index.bucket("sifter"));
    }

    @Test
    void indexingTheSameUuidTwiceKeepsOneEntry() {
        WildlifeIndex index = new WildlifeIndex();
        UUID id = UUID.randomUUID();
        index.index("blub", id);
        index.index("blub", id);

        assertEquals(1, index.bucket("blub").size());
    }

    @Test
    void clearEmptiesEveryBucket() {
        WildlifeIndex index = new WildlifeIndex();
        index.index("blub", UUID.randomUUID());
        index.index("sifter", UUID.randomUUID());

        index.clear();

        assertEquals(Set.of(), index.keys());
    }
}
