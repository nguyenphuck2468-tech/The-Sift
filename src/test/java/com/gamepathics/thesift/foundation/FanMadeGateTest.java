package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3/8: the fan-made wildlife gate exempts exactly the five core mod mobs. */
class FanMadeGateTest {
    @Test
    void coreModMobSetIsExactlyTheFiveReferenceMobs() {
        assertEquals(Set.of("singer", "echo_golem", "blub", "sifter", "dark_sniffer"),
                SiftEcologyManager.FAN_CORE_MOBS,
                "the exemption list must track the reference mod's five mobs exactly");
    }

    @Test
    void fanMadeMobIdsAreNotExempt() {
        // A sample of the stand-in ids the gate must keep gating (content.sift_standin_mobs).
        for (String id : Set.of("loomer", "wisp", "violet_wisp", "prismite", "thornback",
                "hollow_bulb", "glowbell", "glimmerwing", "frost_stalker", "drifter", "shardling",
                "sunspore", "sift_wastes_stalker", "koi", "bulb")) {
            assertTrue(!SiftEcologyManager.FAN_CORE_MOBS.contains(id),
                    "fan-made stand-in id must stay gated: " + id);
        }
    }
}
