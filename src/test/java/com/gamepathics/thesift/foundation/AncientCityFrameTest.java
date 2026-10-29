package com.gamepathics.thesift.foundation;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AncientCityFrameTest {
    @Test
    void recognizesBothObservedFrameOrientationsFromBoundedBlockData() {
        var zFrame = AncientCityFrame.fromPiece(new BoundingBox(0, -40, 0, 10, -20, 40), frame(0, -35, 10, 0, -28, 31));
        var xFrame = AncientCityFrame.fromPiece(new BoundingBox(0, -40, 0, 40, -20, 10), frame(10, -35, 0, 31, -28, 0));

        assertTrue(zFrame.isPresent());
        assertEquals(AncientCityFrame.Axis.Z, zFrame.orElseThrow().longAxis());
        assertTrue(xFrame.isPresent());
        assertEquals(AncientCityFrame.Axis.X, xFrame.orElseThrow().longAxis());
    }

    @Test
    void rejectsIncompleteOrNonRectangularFrame() {
        List<AncientCityFrame.BlockPos> incomplete = frame(0, -35, 0, 0, -28, 21);
        incomplete.remove(0);
        assertTrue(AncientCityFrame.fromPiece(new BoundingBox(-1, -36, -1, 2, -27, 22), incomplete).isEmpty());
    }

    @Test
    void placesEightCenteredBlocksAlongFrameAndThreeBlocksFromFace() {
        AncientCityFrame zFrame = new AncientCityFrame(0, -35, 0, 0, -28, 21, AncientCityFrame.Axis.Z);
        List<AncientCityFrame.BlockPos> positions = zFrame.sonorousPositions(AncientCityFrame.Side.NEGATIVE);

        assertEquals(8, positions.size());
        assertEquals(List.of(-3, -3, -3, -3, -3, -3, -3, -3), positions.stream().map(AncientCityFrame.BlockPos::x).toList());
        assertEquals(List.of(3, 5, 7, 9, 11, 13, 15, 17), positions.stream().map(AncientCityFrame.BlockPos::z).toList());
        assertTrue(positions.stream().allMatch(position -> position.y() == -35));
    }

    @Test
    void choosesMostOpenFaceAndStableCoordinateHashOnTie() {
        assertEquals(AncientCityFrame.Side.POSITIVE, AncientCityFrame.chooseSide(12, 14, 10, 20));
        assertEquals(AncientCityFrame.chooseSide(12, 12, 10, 20),
                AncientCityFrame.chooseSide(12, 12, 10, 20));
        assertNotEquals(AncientCityFrame.chooseSide(0, 0, 10, 20),
                AncientCityFrame.chooseSide(0, 0, 10, 21));
    }

    @Test
    void portalOpeningCheckIsLimitedToFrameInteriorAndPlane() {
        AncientCityFrame frame = new AncientCityFrame(0, -35, 10, 0, -28, 31, AncientCityFrame.Axis.Z);
        assertTrue(frame.containsPortalOpening(0.5, -31.0, 20.5));
        assertFalse(frame.containsPortalOpening(0.5, -35.0, 20.5));
        assertFalse(frame.containsPortalOpening(2.0, -31.0, 20.5));
        assertFalse(frame.containsPortalOpening(0.5, -31.0, 32.0));
    }

    private static List<AncientCityFrame.BlockPos> frame(int minX, int minY, int minZ,
                                                          int maxX, int maxY, int maxZ) {
        boolean alongX = maxX - minX == 21;
        List<AncientCityFrame.BlockPos> positions = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) for (int offset = 0; offset < 22; offset++) {
            boolean edge = y == minY || y == maxY || offset == 0 || offset == 21;
            if (!edge) continue;
            positions.add(alongX
                    ? new AncientCityFrame.BlockPos(minX + offset, y, minZ)
                    : new AncientCityFrame.BlockPos(minX, y, minZ + offset));
        }
        return positions;
    }
}
