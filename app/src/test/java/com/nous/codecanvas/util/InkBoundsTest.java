package com.nous.codecanvas.util;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InkBoundsTest {

    @Test
    public void aWideDrawingOnADarkPageCropsToTheDrawing() {
        int w = 40;
        int h = 30;
        int[] pixels = new int[w * h];
        int dark = 0xFF121417;
        for (int i = 0; i < pixels.length; i++) pixels[i] = dark;
        // A bright band in the middle, the shape of a wide SVG letterboxed on a tall capture.
        for (int y = 12; y <= 16; y++) {
            for (int x = 4; x <= 35; x++) pixels[y * w + x] = 0xFFF6EAD7;
        }
        int[] box = InkBounds.box(pixels, w, h);
        // The crop covers the bright band and drops the dark letterbox above and below it.
        assertTrue(box[1] <= 12);
        assertTrue(box[1] + box[3] >= 17);
        assertTrue(box[3] < h / 2);
        assertTrue(box[2] > w / 2);
    }

    @Test
    public void aFullBleedPictureIsLeftAlone() {
        int[] pixels = new int[16 * 16];
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0xFF336699;
        assertNull(InkBounds.box(pixels, 16, 16));
    }
}
