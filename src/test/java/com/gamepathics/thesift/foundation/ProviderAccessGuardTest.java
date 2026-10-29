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
        // Extend: block moves in the reported direction.
        assertEquals(BlockFace.EAST,  SoulBlockListener.movementDirection(BlockFace.EAST,  false));
        assertEquals(BlockFace.WEST,  SoulBlockListener.movementDirection(BlockFace.WEST,  false));
        assertEquals(BlockFace.NORTH, SoulBlockListener.movementDirection(BlockFace.NORTH, false));
        assertEquals(BlockFace.SOUTH, SoulBlockListener.movementDirection(BlockFace.SOUTH, false));
        assertEquals(BlockFace.UP,    SoulBlockListener.movementDirection(BlockFace.UP,    false));
        assertEquals(BlockFace.DOWN,  SoulBlockListener.movementDirection(BlockFace.DOWN,  false));

        // Retract: block moves in the opposite of the reported direction.
        assertEquals(BlockFace.WEST,  SoulBlockListener.movementDirection(BlockFace.EAST,  true));
        assertEquals(BlockFace.EAST,  SoulBlockListener.movementDirection(BlockFace.WEST,  true));
        assertEquals(BlockFace.SOUTH, SoulBlockListener.movementDirection(BlockFace.NORTH, true));
        assertEquals(BlockFace.NORTH, SoulBlockListener.movementDirection(BlockFace.SOUTH, true));
        assertEquals(BlockFace.DOWN,  SoulBlockListener.movementDirection(BlockFace.UP,    true));
        assertEquals(BlockFace.UP,    SoulBlockListener.movementDirection(BlockFace.DOWN,  true));
    }
}

