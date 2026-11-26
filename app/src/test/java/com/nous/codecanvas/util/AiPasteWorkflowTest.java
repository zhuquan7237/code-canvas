package com.nous.codecanvas.util;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AiPasteWorkflowTest {
    private static final String TICKS = "\u0060\u0060\u0060";

    @Test public void readsTildesAndPunctuationInLanguageNames() {
        String reply = "Here is the example:\r\n~~~c++ filename=sample.cpp\r\n#include <vector>\r\n  x();\r\n~~~~\r\nTail";
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extractFenced(reply);
        assertEquals(1, blocks.size());
        AiCodeExtractor.CodeBlock block = blocks.get(0);
        assertEquals("c++", block.language);
        assertEquals("#include <vector>\r\n  x();", block.code);
        assertTrue(block.hasSurroundingText(reply));
        assertEquals(".cpp", PasteFileNaming.extension(block.code, block.language));
    }

    @Test public void outerWrapperDoesNotConsumeIdenticalCodeInsideShorterFence() {
        String inner = TICKS + "json\n{}\n" + TICKS;
        String wrapped = "\uFEFF  " + TICKS + "\u0060markdown\n" + inner + "\n" + TICKS + "\u0060\n\n";
        AiCodeExtractor.CodeBlock block = AiCodeExtractor.extractFenced(wrapped).get(0);
        assertEquals("markdown", block.language);
        assertEquals(inner, block.code);
        assertFalse(block.hasSurroundingText(wrapped));
    }

    @Test public void incompleteOrMismatchedWrappersLeaveTheTextAsIs() {
        for (String multiline : new String[] {
                "~~~python\nprint('x')\n" + TICKS, TICKS + "html\n<h1>unfinished</h1>\n",
                "<html><pre>\n" + TICKS + "python\nabc()\n" + TICKS + "\n</pre></html>"
        }) {
            assertTrue(AiCodeExtractor.extractFenced(multiline).isEmpty());
            assertEquals(multiline, AiCodeExtractor.extract(multiline).get(0).code);
        }
    }

    @Test public void scannerIsBoundedAndKeepsSeparateChoices() {
        String escaped = "~~~py\npass\n~~~\n";
        String input = escaped.repeat(300);
        assertEquals(AiCodeExtractor.MAX_BLOCKS + 1, AiCodeExtractor.extractFenced(input).size());
        assertTrue(AiCodeExtractor.extractFenced(input).get(0).hasSurroundingText(input));
    }

    @Test public void proseDoesNotTriggerRawJavaScriptGuardAndLiteralTextStaysPlain() {
        String input = "import the following example\n" + TICKS + "text/plain\n<html>literal</html>\n" + TICKS;
        AiCodeExtractor.CodeBlock block = AiCodeExtractor.extractFenced(input).get(0);
        assertEquals("text/plain", block.language);
        assertEquals(".txt", PasteFileNaming.extension(block.code, block.language));
        assertTrue(block.hasSurroundingText(input));
    }

    @Test public void preserveClipboardNewlinesOnExportWithoutReformatting() {
        String raw = "Here:\r\n<html>\r\n<h1>hi</h1>\n</html>\r\nDone";
        assertEquals("<html>\r\n<h1>hi</h1>\n</html>", AiCodeExtractor.markupRegion(raw));
        assertEquals(raw, TextCodec.normalizeLineEndings(raw, TextCodec.PRESERVE_LINE_ENDINGS));
    }

    @Test public void exactLanguageLabelsKeepUsefulExtensions() {
        assertEquals(".py", PasteFileNaming.extension("print(1)", "python"));
        assertEquals(".yaml", PasteFileNaming.extension("target: 1", "yaml"));
        assertEquals(".md", PasteFileNaming.extension("# title", "Markdown"));
        assertEquals(".csv", PasteFileNaming.extension("a,b\nx,y", "csv"));
        assertEquals(".jsonl", PasteFileNaming.extension("{}\n{}", "jsonl"));
        assertEquals(".jsx", PasteFileNaming.extension("function App() {}", "jsx"));
        assertEquals(".ts", PasteFileNaming.extension("const x: number = 1", "typescript"));
        assertEquals(".svg", PasteFileNaming.extension("<svg xmlns='http://www.w3.org/2000/svg'></svg>", ""));
        assertEquals(".txt", PasteFileNaming.extension("<h1>literal</h1>", "plaintext"));
        assertEquals(".txt", PasteFileNaming.extension("an ordinary message", "nothtml"));
    }
}
