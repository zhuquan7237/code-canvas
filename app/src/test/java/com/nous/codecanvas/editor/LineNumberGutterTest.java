package com.nous.codecanvas.editor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The gutter must always describe the code next to it. These are the cases that would otherwise be
 * found by staring at a screenshot.
 */
public class LineNumberGutterTest {

    @Test
    public void emptyDocumentIsOneLine() {
        assertEquals(1, LineNumberGutter.lineCount(""));
        assertEquals(1, LineNumberGutter.lineCount(null));
        assertEquals("1", LineNumberGutter.numbers(1));
    }

    @Test
    public void countsLinesByNewlines() {
        assertEquals(3, LineNumberGutter.lineCount("a\nb\nc"));
        assertEquals(2, LineNumberGutter.lineCount("a\n"));
        assertEquals(2, LineNumberGutter.lineCount("\n"));
        assertEquals(1, LineNumberGutter.lineCount("a b"));
    }

    @Test
    public void numbersAreOneBasedAndNewlineSeparated() {
        assertEquals("1\n2\n3", LineNumberGutter.numbers(3));
        assertEquals(5, LineNumberGutter.numbers(5).split("\n", -1).length);
    }

    @Test
    public void cursorLineTracksTheOffset() {
        String code = "line1\nline2\nline3";
        assertEquals(1, LineNumberGutter.lineOf(code, 0));
        assertEquals(1, LineNumberGutter.lineOf(code, 4));
        assertEquals(2, LineNumberGutter.lineOf(code, 6));
        assertEquals(3, LineNumberGutter.lineOf(code, 12));
        // out-of-range offsets clamp instead of throwing
        assertEquals(3, LineNumberGutter.lineOf(code, 999));
        assertEquals(1, LineNumberGutter.lineOf(code, -5));
    }

    @Test
    public void gutterLengthMatchesTheCodeItDescribes() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            code.append("x\n");
        }
        int lines = LineNumberGutter.lineCount(code);
        assertEquals(251, lines);
        assertEquals(lines, LineNumberGutter.numbers(lines).split("\n", -1).length);
    }

    @Test
    public void absurdLineCountsAreCapped() {
        assertEquals(LineNumberGutter.MAX_LINES, LineNumberGutter.lineCount(repeat('\n', LineNumberGutter.MAX_LINES + 500)));
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
