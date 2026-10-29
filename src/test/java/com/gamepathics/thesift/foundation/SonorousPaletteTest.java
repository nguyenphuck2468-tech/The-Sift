package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SonorousPaletteTest {
    @Test
    void exposesEightReferenceNoteColorsInMelodyIndexOrder() {
        assertArrayEquals(new int[]{0xB02E26, 0x3C44AA, 0xF9801D, 0x8932B8,
                0x169C9C, 0x80C71F, 0xFED83D, 0xF38BAA},
                java.util.stream.IntStream.rangeClosed(1, 8).map(SonorousPalette::noteRgb).toArray());
    }

    @Test
    void rejectsNotesOutsideTheEightToneRange() {
        assertThrows(IllegalArgumentException.class, () -> SonorousPalette.noteRgb(0));
        assertThrows(IllegalArgumentException.class, () -> SonorousPalette.noteRgb(9));
    }
}
