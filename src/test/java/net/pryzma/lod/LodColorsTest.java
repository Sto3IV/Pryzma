package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Packed RGB arithmetic of the LOD colours. */
class LodColorsTest {
    @Test
    void whiteIsTheIdentityOfTheTint() {
        assertEquals(0x91BD59, LodColors.multiply(0x91BD59, 0xFFFFFF));
        assertEquals(0x91BD59, LodColors.multiply(0xFFFFFF, 0x91BD59));
        assertEquals(0x000000, LodColors.multiply(0x91BD59, 0x000000));
        assertEquals(0x404040, LodColors.multiply(0x808080, 0x808080), "128 * 128 / 255 rounds to 64");
        assertEquals(0x91BD59, LodColors.multiply(0xFF91BD59, 0xFFFFFFFF), "alpha bytes are ignored");
    }

    @Test
    void shadeScalesEveryChannel() {
        assertEquals(0x91BD59, LodColors.shade(0x91BD59, 1.0F));
        assertEquals(0xA0A0A0, LodColors.shade(0xC8C8C8, 0.8F));
        assertEquals(0x990000, LodColors.shade(0xFF0000, 0.6F));
        assertEquals(0x000000, LodColors.shade(0xFFFFFF, 0.0F));
    }

    @Test
    void nativeImagePixelsAreAbgr() {
        assertEquals(0x112233, LodColors.abgrToRgb(0xFF332211));
        assertEquals(0xFF0000, LodColors.abgrToRgb(0x000000FF));
        assertEquals(0x0000FF, LodColors.abgrToRgb(0x00FF0000));
    }
}
