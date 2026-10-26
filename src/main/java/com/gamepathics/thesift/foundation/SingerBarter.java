package com.gamepathics.thesift.foundation;

import org.bukkit.Material;

import java.util.List;

/** Singer reward table ported from the reference mod (200-roll weighted table, Soul Block per trade). */
final class SingerBarter {
    record Reward(Material material, int min, int max, boolean randomWool) {
        Reward(Material material, int min, int max) { this(material, min, max, false); }
    }

    private record Entry(int weight, Reward reward) {}

    private static final List<Entry> TABLE = List.of(
            new Entry(30, new Reward(Material.IRON_BLOCK, 2, 4)),
            new Entry(20, new Reward(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1)),
            new Entry(19, new Reward(Material.ANCIENT_DEBRIS, 4, 8)),
            // The mod drops its custom "Wellspring" disc here; a vanilla disc stands in for it.
            new Entry(3, new Reward(Material.MUSIC_DISC_OTHERSIDE, 1, 1)),
            new Entry(20, new Reward(Material.GOLD_BLOCK, 1, 2)),
            new Entry(11, new Reward(Material.WHEAT_SEEDS, 6, 16)),
            new Entry(9, new Reward(Material.BEETROOT_SEEDS, 4, 8)),
            new Entry(10, new Reward(Material.POTATO, 6, 16)),
            new Entry(10, new Reward(Material.CARROT, 8, 20)),
            new Entry(11, new Reward(Material.NETHERITE_INGOT, 1, 1)),
            new Entry(16, new Reward(Material.BOOKSHELF, 4, 12)),
            new Entry(8, new Reward(Material.END_STONE, 2, 12)),
            new Entry(6, new Reward(Material.CHORUS_FLOWER, 1, 1)),
            new Entry(5, new Reward(Material.ECHO_SHARD, 1, 3)),
            new Entry(5, new Reward(Material.REDSTONE_BLOCK, 4, 16)),
            new Entry(8, new Reward(Material.STRING, 4, 16)));
    private static final Reward WOOL = new Reward(Material.WHITE_WOOL, 9, 24, true);

    static final int TOTAL_ROLL = 200;

    private SingerBarter() {}

    /** @param roll 0 (inclusive) to {@link #TOTAL_ROLL} (exclusive) */
    static Reward pick(int roll) {
        int remaining = roll;
        for (Entry entry : TABLE) {
            remaining -= entry.weight();
            if (remaining < 0) return entry.reward();
        }
        return WOOL;
    }
}
