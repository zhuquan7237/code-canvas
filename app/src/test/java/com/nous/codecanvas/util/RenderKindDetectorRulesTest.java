package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Thirty cases for how a document gets classified before it is rendered.
 *
 * <p>This is the decision that made the app look broken rather than the code look wrong: when a
 * document is classified as RAW or XML it is shown as a tree or as text, so an SVG the user pasted
 * out of a chat simply "did not render". Every rule below therefore guards a user-visible outcome,
 * which is why the cases are written as behaviour rather than as implementation detail.</p>
 */
public class RenderKindDetectorRulesTest {

    // --- extension decides when there is nothing to read ---------------------------------------

    @Test
    public void emptyHtmlFileIsHtml() {
        // An empty .html the user just created must open as a web canvas, not as raw text.
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.html", ""));
    }

    @Test
    public void emptySvgFileIsSvg() {
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("a.svg", ""));
    }

    @Test
    public void emptyXmlFileIsXml() {
        assertEquals(RenderKind.XML, RenderKindDetector.detect("a.xml", ""));
    }

    @Test
    public void emptyUnknownExtensionIsRaw() {
        assertEquals(RenderKind.RAW, RenderKindDetector.detect("a.custom", ""));
    }

    @Test
    public void emptyWithNullFileNameIsRaw() {
        assertEquals(RenderKind.RAW, RenderKindDetector.detect(null, ""));
    }

    @Test
    public void whitespaceOnlyCountsAsEmpty() {
        // A file holding just a stray newline is still an empty file; the extension should win.
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("a.svg", "   \n\t  "));
    }

    @Test
    public void htmExtensionIsTreatedAsHtml() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.htm", ""));
    }

    @Test
    public void xhtmlExtensionIsTreatedAsHtml() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.xhtml", ""));
    }

    // --- an explicit doctype is the strongest signal -------------------------------------------

    @Test
    public void doctypeBeatsAMisleadingExtension() {
        // Someone saved a web page as .svg; the document says what it is.
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("mislabelled.svg",
                "<!DOCTYPE html><html><body>hi</body></html>"));
    }

    @Test
    public void doctypeIsRecognisedCaseInsensitively() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.custom",
                "<!doctype HTML>\n<html><body>hi</body></html>"));
    }

    @Test
    public void doctypeAfterACommentIsStillFound() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.custom",
                "<!-- generated --><!DOCTYPE html><html></html>"));
    }

    // --- the root element decides when the extension is not decisive ---------------------------

    @Test
    public void bareHtmlFragmentIsHtml() {
        // AI chats hand over fragments constantly; these must render, not show as text.
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.txt", "<div><h1>hi</h1></div>"));
    }

    @Test
    public void bareSvgWithoutXmlnsIsStillSvg() {
        // Missing xmlns is extremely common from generators and must not demote it to XML.
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("a.txt",
                "<svg width=\"10\" height=\"10\"><circle r=\"4\"/></svg>"));
    }

    @Test
    public void xmlDeclarationBeforeSvgStillYieldsSvg() {
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("a.custom",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>"));
    }

    @Test
    public void byteOrderMarkBeforeSvgStillYieldsSvg() {
        // Files written on Windows often carry a BOM; it must not be mistaken for text.
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("a.svg",
                "\uFEFF<svg xmlns=\"http://www.w3.org/2000/svg\"/>"));
    }

    @Test
    public void nonHtmlRootIsXml() {
        assertEquals(RenderKind.XML, RenderKindDetector.detect("a.txt",
                "<config><item key=\"a\"/></config>"));
    }

    @Test
    public void styleRootCountsAsHtml() {
        // A pasted <style> block is a web thing, not a generic XML document.
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.txt", "<style>body{color:red}</style>"));
    }

    @Test
    public void scriptRootCountsAsHtml() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.txt", "<script>console.log(1)</script>"));
    }

    @Test
    public void canvasRootCountsAsHtml() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.txt", "<canvas id=\"c\"></canvas>"));
    }

    @Test
    public void tableRootCountsAsHtml() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.txt", "<table><tr><td>1</td></tr></table>"));
    }

    @Test
    public void textBeforeAnyElementIsRaw() {
        // Plain prose is not markup; showing it as a rendering tree would be a lie.
        assertEquals(RenderKind.RAW, RenderKindDetector.detect("a.txt", "这只是一段说明文字，没有标签"));
    }

    @Test
    public void markdownFencedCodeIsRaw() {
        // A leftover ``` fence means the paste was not extracted; better shown as text than faked.
        assertEquals(RenderKind.RAW, RenderKindDetector.detect("a.txt", "```html\n<div>x</div>\n```"));
    }

    @Test
    public void onlyACommentLeavesNothingToClassify() {
        assertEquals(RenderKind.RAW, RenderKindDetector.detect("a.txt", "<!-- 只有注释 -->"));
    }

    // --- extension wins over a root element it disagrees with ----------------------------------

    @Test
    public void svgExtensionBeatsAnHtmlLookingRoot() {
        // An explicit .svg is the author's stated intent even if it opens with a div.
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("a.svg", "<div>not really</div>"));
    }

    @Test
    public void xmlExtensionBeatsAnHtmlLookingRoot() {
        assertEquals(RenderKind.XML, RenderKindDetector.detect("a.xml", "<div>not really</div>"));
    }

    @Test
    public void htmlExtensionBeatsAnUnknownRoot() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.html", "<config><x/></config>"));
    }

    @Test
    public void unknownExtensionWithUnknownRootIsXml() {
        assertEquals(RenderKind.XML, RenderKindDetector.detect("a.custom", "<config><x/></config>"));
    }

    // --- robustness at the edges ---------------------------------------------------------------

    @Test
    public void extensionMatchIsCaseInsensitive() {
        // Phones hand over uppercase names; the suffix must still be honoured.
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("A.SVG", ""));
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("A.HTML", ""));
    }

    @Test
    public void unterminatedCommentDoesNotThrow() {
        // A truncated paste must not crash the list; it just cannot be classified as markup.
        assertEquals(RenderKind.RAW, RenderKindDetector.detect("a.txt", "<!-- 被截断的注释"));
    }

    @Test
    public void cdataBeforeTheRootIsSkipped() {
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("a.custom",
                "<![CDATA[ignored]]><svg xmlns=\"http://www.w3.org/2000/svg\"/>"));
    }

    @Test
    public void veryLongSvgIsStillSvg() {
        // The original bug: a long path graphic had its closing tag far outside a 3000-char window
        // and was demoted to plain text, so it could not be drawn at all.
        StringBuilder longSvg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\">");
        for (int i = 0; i < 40000; i++) {
            longSvg.append("<path d=\"M").append(i).append(",").append(i).append(" L1,1\"/>");
        }
        longSvg.append("</svg>");
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("long.svg", longSvg.toString()));
    }

    @Test
    public void rootLookupIgnoresLeadingBlankLines() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("a.txt", "\n\n\n<section>hi</section>"));
    }
}
