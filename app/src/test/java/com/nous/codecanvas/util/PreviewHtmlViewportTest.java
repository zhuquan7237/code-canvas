package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A page without a viewport meta tag is laid out on WebView's ~980px virtual canvas and zoomed
 * out, so identical code renders large or tiny depending on whether the author added the tag.
 * That is the "why is one preview big and another small" bug; the tag is added here instead of
 * hoping the author included it.
 */
public class PreviewHtmlViewportTest {

    @Test
    public void addsTheTagInsideAnExistingHead() {
        String out = PreviewHtml.ensureViewport("<html><head><title>t</title></head><body>x</body></html>");
        assertTrue(out.contains("<head>\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"));
        assertTrue("the original head content survives", out.contains("<title>t</title>"));
    }

    @Test
    public void respectsAnAuthorSuppliedViewport() {
        String html = "<html><head><meta name=\"viewport\" content=\"width=640\"></head><body>x</body></html>";
        assertEquals(html, PreviewHtml.ensureViewport(html));
        String odd = "<html><head><meta NAME='viewport' content='width=320'></head></html>";
        assertEquals(odd, PreviewHtml.ensureViewport(odd));
    }

    @Test
    public void handlesAnHtmlWithoutHead() {
        String out = PreviewHtml.ensureViewport("<html><body>hi</body></html>");
        assertTrue(out.contains("<head><meta name=\"viewport\""));
        assertTrue("body is still there", out.contains("<body>hi</body>"));
    }

    @Test
    public void wrapsAFragmentSoItHasSomewhereToLive() {
        String out = PreviewHtml.ensureViewport("<div class=\"card\">hi</div>");
        assertTrue(out.startsWith("<!DOCTYPE html>"));
        assertTrue(out.contains("device-width"));
        assertTrue(out.contains("<div class=\"card\">hi</div>"));
    }

    @Test
    public void nullAndEmptyPassThrough() {
        assertEquals(null, PreviewHtml.ensureViewport(null));
        assertEquals("", PreviewHtml.ensureViewport(""));
    }

    @Test
    public void plainTextIsEscapedNotParsed() {
        String html = PreviewHtml.forDocument("<script>alert(1)</script>", RenderKind.RAW, false);
        assertFalse("source must not reach the renderer as markup", html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("device-width"));
    }

    @Test
    public void svgGoesThroughTheResponsiveWrapper() {
        String html = PreviewHtml.forDocument("<svg width=\"10\" height=\"10\"></svg>", RenderKind.SVG, true);
        assertTrue(html.contains("svg-wrapper"));
        assertTrue("the wrapper scales to the container width", html.contains("width: 100%;"));
    }
}
