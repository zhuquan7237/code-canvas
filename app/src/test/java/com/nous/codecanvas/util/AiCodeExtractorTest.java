package com.nous.codecanvas.util;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AiCodeExtractorTest {

    @Test
    public void testExtractFencedBlocksMarkdownWithProse() {
        String input = "Here is the code you requested:\n\n```html\n<!DOCTYPE html>\n<html>\n<body>Hello</body>\n</html>\n```\n\nHope that helps!";
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(input);
        assertEquals(1, blocks.size());
        assertEquals("html", blocks.get(0).language);
        assertEquals("<!DOCTYPE html>\n<html>\n<body>Hello</body>\n</html>", blocks.get(0).code);
    }

    @Test
    public void testExtractMultipleSeparateBlocksPreservingTypes() {
        String input = "First the HTML:\n```html\n<div class=\"box\">Hello</div>\n```\nNext the CSS:\n```css\n.box { color: red; }\n```\nAnd JS:\n```javascript\nconsole.log(\"ready\");\n```";
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(input);
        assertEquals(3, blocks.size());
        assertEquals("html", blocks.get(0).language);
        assertEquals("<div class=\"box\">Hello</div>", blocks.get(0).code);
        assertEquals("css", blocks.get(1).language);
        assertEquals(".box { color: red; }", blocks.get(1).code);
        assertEquals("javascript", blocks.get(2).language);
        assertEquals("console.log(\"ready\");", blocks.get(2).code);
    }

    @Test
    public void testPreservesCrlfInsideFencedBlocks() {
        String input = "```svg\r\n<svg>\r\n  <circle/>\r\n</svg>\r\n```";
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(input);
        assertEquals(1, blocks.size());
        assertEquals("svg", blocks.get(0).language);
        assertEquals("<svg>\r\n  <circle/>\r\n</svg>", blocks.get(0).code);
    }

    @Test
    public void testUnfencedRawReturnsOneCodeBlockUnchanged() {
        String raw = "<html>\n<body>\n<h1>Raw without fence</h1>\n</body>\n</html>";
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(raw);
        assertEquals(1, blocks.size());
        assertEquals("", blocks.get(0).language);
        assertEquals(raw, blocks.get(0).code);
    }

    @Test
    public void testUnfencedWithUtf8BomPreservesOrHarmlessHandlesBom() {
        String raw = "\uFEFF<svg><circle r=\"5\"/></svg>";
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(raw);
        assertEquals(1, blocks.size());
        assertEquals("<svg><circle r=\"5\"/></svg>", blocks.get(0).code);
    }

    @Test
    public void testEmptyOrNullReturnsSingleEmptyOrEmptyList() {
        List<AiCodeExtractor.CodeBlock> blocksNull = AiCodeExtractor.extract(null);
        assertTrue(blocksNull.isEmpty());

        List<AiCodeExtractor.CodeBlock> blocksBlank = AiCodeExtractor.extract("   \n\t  ");
        assertTrue(blocksBlank.isEmpty());
    }

    @Test
    public void rawHtmlWithMarkdownExampleStaysIntact() {
        String raw="<!DOCTYPE html><html><body><pre>\n```html\n<h1>example</h1>\n```\n</pre></body></html>";
        assertEquals(raw,AiCodeExtractor.extract(raw).get(0).code);
    }

    @Test
    public void rawJavaScriptWithFencedTemplateStaysIntact() {
        String raw="const help = `\n```html\n<h1>example</h1>\n```\n`;";
        assertEquals(raw,AiCodeExtractor.extract(raw).get(0).code);
    }

    @Test
    public void testNeverStripFencesWithinUnwrappedRawHtmlOrScripts() {
        // If the entire text is not a markdown response with fences wrapping it, but rather raw code containing backticks or template literals
        String jsWithTemplateLiteral = "const tpl = `hello ${name}`;";
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(jsWithTemplateLiteral);
        assertEquals(1, blocks.size());
        assertEquals(jsWithTemplateLiteral, blocks.get(0).code);
    }
}
