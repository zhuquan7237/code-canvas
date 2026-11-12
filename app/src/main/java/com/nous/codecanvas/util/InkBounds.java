package com.nous.codecanvas.util;

/**
 * The rectangle of a thumbnail that is not just the page background.
 *
 * <p>A wide drawing letterboxed on a dark page used to be stored whole, so the home tile showed a
 * thin strip with large empty bands. The corners say what "empty" is; everything that differs is
 * the picture.</p>
 */
public final class InkBounds {

    private InkBounds() {
    }

    /**
     * @param pixels row-major ARGB
     * @return {@code {left, top, width, height}} or null when the picture already fills the frame
     */
    public static int[] box(int[] pixels, int width, int height) {
        if (pixels == null || width < 4 || height < 4 || pixels.length < width * height) return null;
        int bg = background(pixels, width, height);
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int step = Math.max(1, Math.min(width, height) / 80);
        for (int y = 0; y < height; y += step) {
            int row = y * width;
            for (int x = 0; x < width; x += step) {
                if (differs(pixels[row + x], bg)) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return null;
        int pad = Math.max(2, step);
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(width - 1, maxX + pad);
        maxY = Math.min(height - 1, maxY + pad);
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        if (cw < 8 || ch < 8) return null;
        if (cw * ch > width * height * 0.92f) return null;
        return new int[]{minX, minY, cw, ch};
    }

    private static int background(int[] pixels, int width, int height) {
        int[] corners = new int[]{
                pixels[0],
                pixels[width - 1],
                pixels[(height - 1) * width],
                pixels[(height - 1) * width + width - 1]
        };
        int r = 0;
        int g = 0;
        int b = 0;
        for (int c : corners) {
            r += (c >> 16) & 255;
            g += (c >> 8) & 255;
            b += c & 255;
        }
        return 0xFF000000 | ((r / 4) << 16) | ((g / 4) << 8) | (b / 4);
    }

    static boolean differs(int pixel, int background) {
        int dr = Math.abs(((pixel >> 16) & 255) - ((background >> 16) & 255));
        int dg = Math.abs(((pixel >> 8) & 255) - ((background >> 8) & 255));
        int db = Math.abs((pixel & 255) - (background & 255));
        return dr + dg + db > 48;
    }
}
