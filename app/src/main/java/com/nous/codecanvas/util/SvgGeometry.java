package com.nous.codecanvas.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out the coordinate box an SVG actually occupies, without a renderer.
 *
 * <p>Fitting an artwork to the screen needs one number: the box it covers. The old wrapper simply
 * set {@code max-width}/{@code max-height} and hoped for the best, which fails in three real cases
 * this class exists to handle explicitly:</p>
 * <ul>
 *   <li><b>A non-zero or negative viewBox origin.</b> {@code viewBox="-100 -50 400 300"} does not
 *       start at (0,0); assuming it does shifts the drawing off-centre.</li>
 *   <li><b>No viewBox but a usable width/height.</b> The box is then {@code 0 0 w h}.</li>
 *   <li><b>Percentage or missing dimensions.</b> Nothing can be derived from the markup, so the
 *       caller is told to measure in the renderer or fall back to the available area.</li>
 * </ul>
 *
 * <p>It also widens the box for content that legitimately paints outside it: {@code overflow:
 * visible}, filters and shadows, and non-zero stroke widths. Measuring only the viewBox is what
 * makes a filtered drop shadow look "cut off at the edge".</p>
 */
public final class SvgGeometry {

    private static final Pattern VIEWBOX = Pattern.compile(
            "viewBox\\s*=\\s*[\"']\\s*([-+0-9.eE]+)[\\s,]+([-+0-9.eE]+)[\\s,]+([-+0-9.eE]+)[\\s,]+([-+0-9.eE]+)\\s*[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WIDTH_ATTR = Pattern.compile(
            "\\bwidth\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEIGHT_ATTR = Pattern.compile(
            "\\bheight\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern STROKE_WIDTH = Pattern.compile(
            "stroke-width\\s*[:=]\\s*[\"']?\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILTER_REF = Pattern.compile(
            "\\bfilter\\s*=\\s*[\"']\\s*url\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern OVERFLOW_VISIBLE = Pattern.compile(
            "overflow\\s*[:=]\\s*[\"']?\\s*visible", Pattern.CASE_INSENSITIVE);

    /** How the box was established, so the UI can be honest about a fallback. */
    public enum Source { VIEWBOX, WIDTH_HEIGHT, MEASURED, FALLBACK }

    public static final class Box {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final Source source;
        /** Painted content is expected outside the box (filter/shadow/overflow/stroke). */
        public final boolean padded;

        Box(double x, double y, double width, double height, Source source, boolean padded) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.source = source;
            this.padded = padded;
        }

        public boolean isValid() {
            return width > 0 && height > 0 && !Double.isNaN(width) && !Double.isNaN(height);
        }

        public double aspect() {
            return height == 0 ? 1 : width / height;
        }

        @Override
        public String toString() {
            return "Box{" + x + "," + y + " " + width + "x" + height + " via " + source
                    + (padded ? " padded" : "") + "}";
        }
    }

    private SvgGeometry() {
    }

    /**
     * The box to fit, or a {@code FALLBACK} box when the markup does not say. A fallback box is
     * never "wrong" — it means "use the available area and tell the user".
     */
    public static Box measure(String svg) {
        if (svg == null || svg.trim().isEmpty()) {
            return new Box(0, 0, 0, 0, Source.FALLBACK, false);
        }
        String root = rootTag(svg);
        boolean padded = expectsOverflow(root, svg);

        Matcher viewBox = VIEWBOX.matcher(root);
        if (viewBox.find()) {
            try {
                double x = Double.parseDouble(viewBox.group(1));
                double y = Double.parseDouble(viewBox.group(2));
                double w = Double.parseDouble(viewBox.group(3));
                double h = Double.parseDouble(viewBox.group(4));
                if (w > 0 && h > 0) {
                    return new Box(x, y, w, h, Source.VIEWBOX, padded);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Double w = lengthValue(root, WIDTH_ATTR);
        Double h = lengthValue(root, HEIGHT_ATTR);
        if (w != null && h != null && w > 0 && h > 0) {
            // No viewBox: the user coordinate system is the width/height box, anchored at 0,0.
            return new Box(0, 0, w, h, Source.WIDTH_HEIGHT, padded);
        }

        return new Box(0, 0, 0, 0, Source.FALLBACK, padded);
    }

    /**
     * Scale that fits {@code box} into {@code viewportW x viewportH} with {@code padding} on every
     * side. Returns 0 when either box is degenerate.
     */
    public static double fitScale(Box box, double viewportW, double viewportH, double padding) {
        if (box == null || !box.isValid()) return 0;
        double availW = viewportW - 2 * padding;
        double availH = viewportH - 2 * padding;
        if (availW <= 0 || availH <= 0) return 0;
        return Math.min(availW / box.width, availH / box.height);
    }

    /**
     * The viewBox that "fit to content" should use: the measured box grown by a margin, so a
     * drawing whose own viewBox clips it can be inspected without editing the file.
     *
     * <p>Deliberately a preview-only adjustment. Internal {@code clipPath} and {@code mask}
     * elements are left exactly as authored — widening the view cannot undo a deliberate clip, and
     * pretending otherwise would change what the document means.</p>
     */
    public static String fitContentViewBox(String svg, double marginRatio) {
        Box box = measure(svg);
        if (!box.isValid()) return null;
        double margin = Math.max(box.width, box.height) * Math.max(0, marginRatio);
        double x = box.x - margin;
        double y = box.y - margin;
        double w = box.width + 2 * margin;
        double h = box.height + 2 * margin;
        return trim(x) + " " + trim(y) + " " + trim(w) + " " + trim(h);
    }

    /** True when the document clips itself on purpose, so "fit to content" cannot reveal more. */
    public static boolean hasInternalClipping(String svg) {
        if (svg == null) return false;
        String lower = svg.toLowerCase(Locale.ROOT);
        return lower.contains("<clippath") || lower.contains("<mask")
                || lower.contains("clip-path=") || lower.contains("clip-path:");
    }

    /**
     * Whether painted content is expected outside the viewBox. Filters, shadows and strokes live on
     * child elements as often as on the root, so this scans the whole document, not just the root
     * tag — measuring only the root is exactly how a drop shadow ends up looking cut off.
     */
    private static boolean expectsOverflow(String root, String whole) {
        String haystack = whole == null ? root : whole;
        if (haystack == null) return false;
        return FILTER_REF.matcher(haystack).find()
                || OVERFLOW_VISIBLE.matcher(haystack).find()
                || STROKE_WIDTH.matcher(haystack).find();
    }

    private static Double lengthValue(String root, Pattern attribute) {
        Matcher m = attribute.matcher(root);
        if (!m.find()) return null;
        String raw = m.group(1).trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty() || raw.endsWith("%")) return null;   // a percentage is not a coordinate
        int unit = raw.length();
        while (unit > 0 && (Character.isLetter(raw.charAt(unit - 1)))) unit--;
        String number = raw.substring(0, unit).trim();
        if (number.isEmpty()) return null;
        try {
            double value = Double.parseDouble(number);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The opening {@code <svg ...>} tag, so attribute search never wanders into child elements. */
    static String rootTag(String svg) {
        int start = indexOfIgnoreCase(svg, "<svg");
        if (start < 0) return svg;
        int end = svg.indexOf('>', start);
        return end > start ? svg.substring(start, end + 1) : svg.substring(start);
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private static String trim(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
