package com.nous.codecanvas.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ThumbnailFrameTest {

    @Test
    public void htmlKeepsTheTopOfTheFirstScreen() {
        int[] box = ThumbnailFrame.topCrop(390, 844, ThumbnailFrame.CARD_ASPECT);
        assertEquals(0, box[0]);
        assertEquals(0, box[1]);
        assertEquals(390, box[2]);
        assertEquals(244, box[3]);
        assertTrue(box[1] + box[3] < 844);
    }

    @Test
    public void aWideSvgFitsWithoutStretchingOrCropping() {
        float scale = ThumbnailFrame.fitScale(1200, 400, 320, 200);
        assertEquals(320f / 1200f, scale, 0.0001f);
        assertTrue(400 * scale <= 200f);
        assertEquals(1200 * scale, 320f, 0.01f);
    }
}
