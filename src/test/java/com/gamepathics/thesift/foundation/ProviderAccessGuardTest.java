package com.gamepathics.thesift.foundation;

import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderAccessGuardTest {
    @Test
    void onlyBlocksCrossWorldEntryIntoLockedSift() {
        NamespacedKey overworld = NamespacedKey.minecraft("overworld");
        NamespacedKey sift = SiftDimensionManager.DIMENSION_KEY;

        assertTrue(ProviderAccessGuard.shouldBlock(overworld, sift, true, false));
        assertFalse(ProviderAccessGuard.shouldBlock(sift, sift, true, false));
        assertFalse(ProviderAccessGuard.shouldBlock(overworld, sift, true, true));
        assertFalse(ProviderAccessGuard.shouldBlock(overworld, sift, false, false));
        assertFalse(ProviderAccessGuard.shouldBlock(overworld, overworld, true, false));
    }

    @Test
    void pistonMovementTracksPushAndPullDirection() {
        assertEquals(BlockFace.EAST, SoulBlockListener.movementDirection(BlockFace.EAST, false));
        assertEquals(BlockFace.WEST, SoulBlockListener.movementDirection(BlockFace.EAST, true));
    }
}
