package com.nous.codecanvas;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.test.InstrumentationTestCase;

import com.nous.codecanvas.ui.PreviewThumbnailCache;

/**
 * A capture that painted nothing must never be stored: the white base coat would replace the
 * file-type cover and read as a broken thumbnail (visible on a real screenshot of a document
 * whose preview had not painted yet).
 *
 * <p>Checked on a device rather than in a JVM test because it is about real bitmaps.</p>
 */
public class ThumbnailBlankTest extends InstrumentationTestCase {

    private Bitmap blank() {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawColor(Color.WHITE);
        return b;
    }

    public void testWhiteFrameIsBlank() {
        Bitmap b = blank();
        assertTrue("a pure white capture is blank", PreviewThumbnailCache.looksBlank(b));
        b.recycle();
    }

    public void testNearlyUniformFrameIsBlank() {
        Bitmap b = blank();
        new Canvas(b).drawColor(Color.rgb(252, 252, 252));
        assertTrue("an almost-uniform capture is blank", PreviewThumbnailCache.looksBlank(b));
        b.recycle();
    }

    public void testPaintedFrameIsNotBlank() {
        Bitmap b = blank();
        Paint p = new Paint();
        p.setColor(Color.rgb(20, 110, 220));
        new Canvas(b).drawRect(20, 20, 300, 160, p);
        assertFalse("a frame with real content is not blank", PreviewThumbnailCache.looksBlank(b));
        b.recycle();
    }

    public void testFlatColourFrameIsNotBlank() {
        Bitmap b = blank();
        new Canvas(b).drawColor(Color.rgb(15, 118, 110));
        assertFalse("a page that painted only a background still has content",
                PreviewThumbnailCache.looksBlank(b));
        b.recycle();
    }

    public void testSmallPaintedBlockIsNotBlank() {
        Bitmap b = blank();
        Paint p = new Paint();
        p.setColor(Color.rgb(30, 41, 59));
        new Canvas(b).drawRect(140, 80, 180, 100, p);
        assertFalse("a real painted block counts as content", PreviewThumbnailCache.looksBlank(b));
        b.recycle();
    }
}
