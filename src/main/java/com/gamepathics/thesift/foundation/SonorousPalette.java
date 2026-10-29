package com.gamepathics.thesift.foundation;

/** Color values transcribed from the public reference source; no texture or shader file is copied. */
final class SonorousPalette {
    private static final int[] NOTE_RGB = {
            0xB02E26, 0x3C44AA, 0xF9801D, 0x8932B8,
            0x169C9C, 0x80C71F, 0xFED83D, 0xF38BAA
    };
    static final int PORTAL_TEAL_RGB = 0x33A8C2;
    static final int PORTAL_MIST_RGB = 0xF0FEFF;

    private SonorousPalette() {}

    static int noteRgb(int note) {
        if (note < 1 || note > NOTE_RGB.length) throw new IllegalArgumentException("note must be 1..8");
        return NOTE_RGB[note - 1];
    }
}
