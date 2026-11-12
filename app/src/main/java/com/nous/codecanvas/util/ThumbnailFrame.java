package com.nous.codecanvas.util;

/**
 * How a captured page becomes a home-screen tile.
 *
 * <p>The tile is 16:10. An SVG keeps its whole viewBox, scaled uniformly and letterboxed.
 * An HTML page keeps the top of the first phone screen. Nothing is stretched, and nothing is
 * cropped down to whatever happens to sit in the middle.</p>
 */
public final class ThumbnailFrame {

    /** Card width divided by card height. */
    public static final float CARD_ASPECT = 16f / 10f;

    private ThumbnailFrame() {
    }

    /**
     * A top-anchored crop whose width/height equals {@code aspect}.
     *
     * @return left, top, width, height
     */
    public static int[] topCrop(int srcW, int srcH, float aspect) {
        int width = Math.max(1, srcW);
        int height = Math.max(1, srcH);
        if (aspect <= 0f) aspect = CARD_ASPECT;
        int cropW = width;
        int cropH = Math.max(1, Math.round(width / aspect));
        if (cropH > height) {
            cropH = height;
            cropW = Math.max(1, Math.round(height * aspect));
        }
        int left = Math.max(0, (width - cropW) / 2);
        if (left + cropW > width) cropW = width - left;
        return new int[]{left, 0, cropW, cropH};
    }

    /** Largest uniform scale that fits {@code src} inside {@code dst}. */
    public static float fitScale(int srcW, int srcH, int dstW, int dstH) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return 1f;
        return Math.min(dstW / (float) srcW, dstH / (float) srcH);
    }
}
