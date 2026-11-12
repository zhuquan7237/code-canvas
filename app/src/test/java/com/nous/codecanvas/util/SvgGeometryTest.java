package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The measuring rules that decide whether a first open shows the whole drawing. */
public class SvgGeometryTest {

    @Test
    public void viewBoxIsUsedWhenPresent() {
        SvgGeometry.Box box = SvgGeometry.measure("<svg viewBox=\"0 0 1280 800\"><rect/></svg>");
        assertEquals(SvgGeometry.Source.VIEWBOX, box.source);
        assertEquals(0, box.x, 0.001);
        assertEquals(0, box.y, 0.001);
        assertEquals(1280, box.width, 0.001);
        assertEquals(800, box.height, 0.001);
    }

    @Test
    public void nonZeroAndNegativeOriginIsHonoured() {
        // The case the old fit ignored: the drawing does not start at (0,0).
        SvgGeometry.Box box = SvgGeometry.measure("<svg viewBox=\"-100 -50 400 300\"><rect/></svg>");
        assertEquals(-100, box.x, 0.001);
        assertEquals(-50, box.y, 0.001);
        assertEquals(400, box.width, 0.001);
        assertEquals(300, box.height, 0.001);
        double scale = SvgGeometry.fitScale(box, 400, 800, 8);
        assertEquals(384.0 / 400.0, scale, 0.0001);
    }

    @Test
    public void commaSeparatedViewBoxIsAccepted() {
        SvgGeometry.Box box = SvgGeometry.measure("<svg viewBox=\"0,0,640,480\"><rect/></svg>");
        assertEquals(SvgGeometry.Source.VIEWBOX, box.source);
        assertEquals(640, box.width, 0.001);
    }

    @Test
    public void widthAndHeightAreUsedWhenThereIsNoViewBox() {
        SvgGeometry.Box box = SvgGeometry.measure("<svg width=\"300\" height=\"150\"><rect/></svg>");
        assertEquals(SvgGeometry.Source.WIDTH_HEIGHT, box.source);
        assertEquals(300, box.width, 0.001);
        assertEquals(150, box.height, 0.001);
    }

    @Test
    public void unitSuffixedDimensionsAreAccepted() {
        SvgGeometry.Box box = SvgGeometry.measure("<svg width=\"300px\" height=\"150px\"><rect/></svg>");
        assertEquals(SvgGeometry.Source.WIDTH_HEIGHT, box.source);
        assertEquals(300, box.width, 0.001);
    }

    @Test
    public void percentageDimensionsFallBack() {
        // A percentage is not a coordinate, so nothing can be derived from the markup.
        SvgGeometry.Box box = SvgGeometry.measure("<svg width=\"100%\" height=\"100%\"><rect/></svg>");
        assertEquals(SvgGeometry.Source.FALLBACK, box.source);
        assertFalse(box.isValid());
    }

    @Test
    public void missingDimensionsFallBack() {
        SvgGeometry.Box box = SvgGeometry.measure("<svg><rect/></svg>");
        assertEquals(SvgGeometry.Source.FALLBACK, box.source);
    }

    @Test
    public void childElementAttributesDoNotLeakIntoTheRootMeasurement() {
        // The rect's width must not be mistaken for the document's width.
        SvgGeometry.Box box = SvgGeometry.measure(
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"40\" height=\"40\"/></svg>");
        assertEquals(SvgGeometry.Source.FALLBACK, box.source);
    }

    @Test
    public void fitScaleLeavesTheRequestedPadding() {
        SvgGeometry.Box box = SvgGeometry.measure("<svg viewBox=\"0 0 1000 1000\"><rect/></svg>");
        // 1000x1000 into 400x800 with 20 padding: limited by width -> (400-40)/1000.
        assertEquals(0.36, SvgGeometry.fitScale(box, 400, 800, 20), 0.0001);
    }

    @Test
    public void fitScaleIsZeroForDegenerateInput() {
        SvgGeometry.Box fallback = SvgGeometry.measure("<svg width=\"100%\"><rect/></svg>");
        assertEquals(0, SvgGeometry.fitScale(fallback, 400, 800, 8), 0.0001);
        assertEquals(0, SvgGeometry.fitScale(null, 400, 800, 8), 0.0001);
        SvgGeometry.Box box = SvgGeometry.measure("<svg viewBox=\"0 0 100 100\"><rect/></svg>");
        assertEquals(0, SvgGeometry.fitScale(box, 4, 800, 8), 0.0001);
    }

    @Test
    public void filtersAndStrokesMarkTheBoxAsPadded() {
        SvgGeometry.Box filtered = SvgGeometry.measure(
                "<svg viewBox=\"0 0 100 100\"><g filter=\"url(#shadow)\"><rect/></g></svg>");
        assertTrue("a drop shadow paints outside the viewBox", filtered.padded);

        SvgGeometry.Box stroked = SvgGeometry.measure(
                "<svg viewBox=\"0 0 100 100\"><rect stroke-width=\"8\"/></svg>");
        assertTrue(stroked.padded);

        SvgGeometry.Box plain = SvgGeometry.measure("<svg viewBox=\"0 0 100 100\"><rect/></svg>");
        assertFalse(plain.padded);
    }

    @Test
    public void fitToContentViewBoxGrowsTheBoxWithoutTouchingTheDocument() {
        String svg = "<svg viewBox=\"0 0 100 100\"><rect/></svg>";
        String box = SvgGeometry.fitContentViewBox(svg, 0.1);
        assertNotNull(box);
        String[] parts = box.split(" ");
        assertEquals(-10.0, Double.parseDouble(parts[0]), 0.001);
        assertEquals(120.0, Double.parseDouble(parts[2]), 0.001);
        // The source text is untouched: fitting is a preview adjustment, never an edit.
        assertTrue(svg.contains("viewBox=\"0 0 100 100\""));
    }

    @Test
    public void fitToContentViewBoxIsNullWhenNothingCanBeMeasured() {
        assertNull(SvgGeometry.fitContentViewBox("<svg width=\"100%\"><rect/></svg>", 0.1));
    }

    @Test
    public void internalClippingIsDetectedSoTheUiCanWarn() {
        assertTrue(SvgGeometry.hasInternalClipping(
                "<svg><defs><clipPath id=\"c\"><rect/></clipPath></defs><g clip-path=\"url(#c)\"/></svg>"));
        assertTrue(SvgGeometry.hasInternalClipping("<svg><mask id=\"m\"/></svg>"));
        assertFalse(SvgGeometry.hasInternalClipping("<svg><rect/></svg>"));
    }
}
