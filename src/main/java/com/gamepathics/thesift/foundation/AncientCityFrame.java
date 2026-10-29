package com.gamepathics.thesift.foundation;

import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Validated reinforced-deepslate frame geometry read from one generated structure piece. */
record AncientCityFrame(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Axis longAxis) {
    enum Axis { X, Z }
    record BlockPos(int x, int y, int z) {}
    enum Side { NEGATIVE, POSITIVE }

    static Optional<AncientCityFrame> fromPiece(BoundingBox pieceBounds, Collection<BlockPos> reinforced) {
        if (pieceBounds == null || reinforced == null || reinforced.isEmpty()) return Optional.empty();
        int minX = reinforced.stream().mapToInt(BlockPos::x).min().orElseThrow();
        int maxX = reinforced.stream().mapToInt(BlockPos::x).max().orElseThrow();
        int minY = reinforced.stream().mapToInt(BlockPos::y).min().orElseThrow();
        int maxY = reinforced.stream().mapToInt(BlockPos::y).max().orElseThrow();
        int minZ = reinforced.stream().mapToInt(BlockPos::z).min().orElseThrow();
        int maxZ = reinforced.stream().mapToInt(BlockPos::z).max().orElseThrow();
        boolean alongX = maxX - minX + 1 == 22 && maxZ == minZ;
        boolean alongZ = maxZ - minZ + 1 == 22 && maxX == minX;
        if ((!alongX && !alongZ) || maxY - minY + 1 != 8) return Optional.empty();
        int length = alongX ? 22 : 1;
        int depth = alongX ? 1 : 22;
        int expected = 2 * length + 2 * 8 - 4;
        if (reinforced.size() != expected) return Optional.empty();
        Set<BlockPos> blocks = new HashSet<>(reinforced);
        if (blocks.size() != expected) return Optional.empty();
        for (int y = minY; y <= maxY; y++) for (int i = 0; i < 22; i++) {
            int x = alongX ? minX + i : minX;
            int z = alongZ ? minZ + i : minZ;
            boolean edge = i == 0 || i == 21 || y == minY || y == maxY;
            if (edge && !blocks.contains(new BlockPos(x, y, z))) return Optional.empty();
        }
        // The structure-piece bounds are the scan limit; the selected frame must belong to them.
        if (!pieceBounds.contains(minX, minY, minZ) || !pieceBounds.contains(maxX, maxY, maxZ)) return Optional.empty();
        return Optional.of(new AncientCityFrame(minX, minY, minZ, maxX, maxY, maxZ,
                alongX ? Axis.X : Axis.Z));
    }

    List<BlockPos> sonorousPositions(Side side) {
        List<BlockPos> result = new ArrayList<>(8);
        boolean alongX = longAxis == Axis.X;
        int low = alongX ? minX : minZ;
        // Eight blocks at two-block intervals span 15 blocks; this start centers to within half a block.
        int first = low + 3;
        int face = alongX ? (side == Side.NEGATIVE ? minZ - 3 : maxZ + 3)
                : (side == Side.NEGATIVE ? minX - 3 : maxX + 3);
        for (int i = 0; i < 8; i++) {
            int along = first + i * 2;
            result.add(alongX ? new BlockPos(along, minY, face) : new BlockPos(face, minY, along));
        }
        return List.copyOf(result);
    }

    boolean containsPortalOpening(double x, double y, double z) {
        if (y <= minY || y >= maxY) return false;
        if (longAxis == Axis.X) {
            double plane = minZ + 0.5;
            return x > minX + 1.0 && x < maxX + 1.0 && Math.abs(z - plane) <= 0.7;
        }
        double plane = minX + 0.5;
        return z > minZ + 1.0 && z < maxZ + 1.0 && Math.abs(x - plane) <= 0.7;
    }

    static Side chooseSide(int negativeOpenCells, int positiveOpenCells, int originX, int originZ) {
        if (negativeOpenCells > positiveOpenCells) return Side.NEGATIVE;
        if (positiveOpenCells > negativeOpenCells) return Side.POSITIVE;
        return (coordinateTieHash(originX, originZ) & 1) == 0 ? Side.NEGATIVE : Side.POSITIVE;
    }

    static int coordinateTieHash(int x, int z) {
        int h = 0x811c9dc5;
        h = (h ^ x) * 0x01000193;
        return (h ^ z) * 0x01000193;
    }

    static List<BlockPos> sorted(Collection<BlockPos> positions) {
        return positions.stream().sorted(Comparator.comparingInt(BlockPos::x)
                .thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::z)).toList();
    }
}
