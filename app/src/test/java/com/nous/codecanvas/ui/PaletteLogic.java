package com.nous.codecanvas.ui;

/**
 * Pure-JVM WCAG 2.1 helpers used by the palette and logic tests.
 * Deliberately free of {@code android.graphics.Color} so it runs in plain JUnit.
 */
public final class PaletteLogic {

    private PaletteLogic() {
    }

    /** WCAG relative luminance for an ARGB int. */
    public static double luminance(int color) {
        double[] channels = {
                ((color >> 16) & 0xFF) / 255.0,
                ((color >> 8) & 0xFF) / 255.0,
                (color & 0xFF) / 255.0
        };
        for (int i = 0; i < 3; i++) {
            channels[i] = channels[i] <= 0.04045
                    ? channels[i] / 12.92
                    : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
        }
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
    }

    /** WCAG contrast ratio between two opaque ARGB ints. */
    public static double contrast(int a, int b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    /** Relative luminance of a {@code #RRGGBB} string. */
    public static double luminance(String hex) {
        return luminance(parseHex(hex));
    }

    /** Contrast ratio between two {@code #RRGGBB} strings. */
    public static double contrast(String a, String b) {
        return contrast(parseHex(a), parseHex(b));
    }

    /** Parses {@code #RRGGBB} or {@code #AARRGGBB} into an ARGB int. */
    public static int parseHex(String hex) {
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() == 6) {
            return 0xFF000000 | (int) Long.parseLong(value, 16);
        }
        if (value.length() == 8) {
            return (int) Long.parseLong(value, 16);
        }
        throw new IllegalArgumentException("Unsupported hex colour: " + hex);
    }

    /** Convenience for asserting an ordinary-text pairing. */
    public static boolean meetsTextAa(int foreground, int background) {
        return contrast(foreground, background) >= 4.5;
    }

    /** Convenience for asserting a functional pairing (icons, strokes, indicators). */
    public static boolean meetsFunctionalAa(int foreground, int background) {
        return contrast(foreground, background) >= 3.0;
    }
}