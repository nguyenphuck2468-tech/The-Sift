package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingerBarterTest {
    @Test
    void tableEdgesMatchTheReferenceMod() {
        assertEquals(Material.IRON_BLOCK, SingerBarter.pick(0).material());
        assertEquals(Material.IRON_BLOCK, SingerBarter.pick(29).material());
        assertEquals(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, SingerBarter.pick(30).material());
        assertEquals(Material.ANCIENT_DEBRIS, SingerBarter.pick(50).material());
        assertEquals(Material.NETHERITE_INGOT, SingerBarter.pick(139).material());
    }

    @Test
    void lastNineRollsAreRandomWool() {
        assertTrue(SingerBarter.pick(191).randomWool());
        assertTrue(SingerBarter.pick(199).randomWool());
        assertEquals(Material.STRING, SingerBarter.pick(190).material());
    }

    @Test
    void everyRollYieldsAValidRange() {
        for (int roll = 0; roll < SingerBarter.TOTAL_ROLL; roll++) {
            SingerBarter.Reward reward = SingerBarter.pick(roll);
            assertTrue(reward.min() >= 1 && reward.max() >= reward.min());
        }
    }
}
