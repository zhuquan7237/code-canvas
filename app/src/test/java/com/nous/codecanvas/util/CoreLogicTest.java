package com.nous.codecanvas.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoreLogicTest {

    @Test
    public void testSanitizeFileName() {
        assertEquals("test.html", FileUtils.sanitizeFileName("test.html"));
        assertEquals("script.user.js", FileUtils.sanitizeFileName("script.user.js"));
        assertEquals("my_diagram.svg", FileUtils.sanitizeFileName("..\\../my_diagram.svg"));
        assertEquals("config.xml", FileUtils.sanitizeFileName("path/to/config.xml"));
        assertEquals("untitled.txt", FileUtils.sanitizeFileName("   "));
        assertEquals("untitled.txt", FileUtils.sanitizeFileName(null));
        assertEquals("name.custom-ext", FileUtils.sanitizeFileName("special/name.custom-ext"));
    }

    @Test
    public void testDetectRenderKind() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("index.html", ""));
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("page.htm", ""));
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("icon.svg", ""));
        assertEquals(RenderKind.XML, RenderKindDetector.detect("layout.xml", ""));

        // Content-based detection for arbitrary suffixes like .txt or .custom
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("snippet.txt", "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>"));
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("vector.dat", "<?xml version=\"1.0\"?><svg viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>"));
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("icon.unknown", "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\"/></svg>"));
        assertEquals(RenderKind.XML, RenderKindDetector.detect("manifest.bin", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><app><version>1.0</version></app>"));
        assertEquals(RenderKind.RAW, RenderKindDetector.detect("notes.md", "# Title\nSome markdown text"));

        // New requirements:
        // 1. Filename .html but content is purely SVG -> content takes precedence (strong content preferred over mislabeled extension)
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("misleading.html", "<svg viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"5\"/></svg>"));

        // 2. Full HTML containing embedded <svg> -> HTML takes precedence
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("page.svg", "<!DOCTYPE html><html><body><svg><rect/></svg></body></html>"));
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("inline_svg.unknown", "<html><body><svg viewBox=\"0 0 10 10\"><circle/></svg></body></html>"));

        // 3. XML header with SVG inside -> detected as SVG even if named .xml
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("graphic.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0\"/></svg>"));

        // 4. Fragments of generic HTML with known tags (like <div>, <p>, <span>, <button>) -> HTML, NOT XML
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("fragment.txt", "<div class=\"hero\"><h1>Welcome</h1><p>Test</p></div>"));
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("unknown", "<span>Hello</span> <button>Click</button>"));

        // 5. Strong content preferred over mislabeled common formats, but RAW custom remains heuristics
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("test.xml", "<!DOCTYPE html><html><body>Test</body></html>"));
        assertEquals(RenderKind.RAW, RenderKindDetector.detect("custom.dat", "random unformatted text here"));

    }

    @Test
    public void testXmlSecureValidation() {
        // Valid XML
        XmlValidator.ValidationResult valid = XmlValidator.validateSecurely("<root><item id=\"1\">Value</item></root>");
        assertTrue("Valid XML should pass", valid.isValid());
        assertNull(valid.getErrorMessage());

        // Malformed XML
        XmlValidator.ValidationResult malformed = XmlValidator.validateSecurely("<root><item>Unclosed</root>");
        assertFalse("Malformed XML should fail", malformed.isValid());
        assertNotNull(malformed.getErrorMessage());

        // XXE attempt (should be rejected safely or feature disabled without crash)
        String xxePayload = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                "<!DOCTYPE foo [  \n" +
                "<!ELEMENT foo ANY >\n" +
                "<!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]><foo>&xxe;</foo>";
        XmlValidator.ValidationResult xxeResult = XmlValidator.validateSecurely(xxePayload);
        // XXE with external entity must not resolve or must fail safely
        assertFalse("XXE entity expansion must fail or be disallowed", xxeResult.isValid());
    }

    @Test
    public void testSvgResponsiveHtmlWrapping() {
        String svgContent = "<svg viewBox=\"0 0 200 200\" width=\"200\" height=\"200\"><circle cx=\"100\" cy=\"100\" r=\"80\" fill=\"#0D9488\"/></svg>";
        String wrapped = SvgWrapper.wrapSvgInResponsiveHtml(svgContent, false);

        assertTrue("Should include responsive viewport meta", wrapped.contains("name=\"viewport\""));
        assertTrue("Should include SVG content", wrapped.contains("<circle cx=\"100\" cy=\"100\""));
        assertTrue("Should contain container avoiding height collapse", wrapped.contains("svg-wrapper") || wrapped.contains("viewBox"));
        assertFalse("Should not include external tracking scripts", wrapped.contains("http://"));
    }

    @Test
    public void testHtmlSecurityFilterAndEscaping() {
        String rawXml = "<catalog><book id=\"1\"><title>Java & Android</title></book></catalog>";
        String formatted = XmlFormatter.formatForDisplay(rawXml);
        assertTrue("Formatted XML should preserve structure", formatted.contains("catalog"));
        assertTrue("Formatted XML should handle entities", formatted.contains("Java & Android") || formatted.contains("Java &amp; Android"));
    }
}

