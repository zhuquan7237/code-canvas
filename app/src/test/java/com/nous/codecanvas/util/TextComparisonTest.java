package com.nous.codecanvas.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class TextComparisonTest {
    @Test public void crlfAndLfAreEquivalentButWhitespaceStaysExact() throws Exception {
        assertTrue(TextComparison.compare("header\r\nitem\r\n", "header\nitem\n").same());
        TextComparison.Result r=TextComparison.compare("header\r\nitem  \r\n","header\nitem\n");
        assertEquals(1,r.removed); assertEquals(1,r.added);
        assertEquals(2,r.changes.get(0).number); assertEquals("item  ",r.changes.get(0).text);
        assertEquals("item␠␠",TextComparison.visibleTrailingWhitespace(r.changes.get(0).text));
        assertEquals("  hi⇥",TextComparison.visibleTrailingWhitespace("  hi\t"));
    }
    @Test public void missingFinalTerminatorIsNeverIdentical() throws Exception {
        TextComparison.Result r=TextComparison.compare("header\nitem\n","header\nitem");
        assertFalse(r.same()); assertTrue(r.changes.isEmpty());
        assertTrue(r.leftEndsWithNewline); assertFalse(r.rightEndsWithNewline);
        assertEquals(1,TextComparison.compare("","\n").added);
    }
    @Test public void additionsAndRemovalsReverseWithTheSources() throws Exception {
        TextComparison.Result r=TextComparison.compare("a\nold\nk\n", "a\nnew\nk\nz\n");
        assertEquals(1,r.removed); assertEquals(2,r.added);
        TextComparison.Result rev=TextComparison.compare("a\nnew\nk\nz\n","a\nold\nk\n");
        assertEquals(2,rev.removed); assertEquals(1,rev.added);
        assertEquals(4,r.changes.get(2).number);
    }
    @Test public void fragmentedChangesKeepOriginalLineNumbers() throws Exception {
        TextComparison.Result r=TextComparison.compare("a\n1\nb\n2\nc","a\n3\nb\n4\nc");
        assertEquals(2,r.removed); assertEquals(2,r.added);
        assertEquals(2,r.changes.get(0).number); assertEquals(4,r.changes.get(3).number);
    }
    @Test public void supplementarySymbolsWithinTheCharacterLimitAreAccepted() throws Exception {
        String emoji = String.join("", java.util.Collections.nCopies(TextComparison.MAX_LINE_CHARS, "🙂"));
        assertEquals(TextComparison.MAX_LINE_CHARS, TextComparison.characterCount(emoji));
        assertEquals(1, TextComparison.compare("", emoji).added);
    }

    @Test(expected=TextComparison.LimitExceeded.class)
    public void supplementarySymbolsAboveTheCharacterLimitAreRejected() throws Exception {
        String emoji = String.join("", java.util.Collections.nCopies(TextComparison.MAX_LINE_CHARS+1, "🙂"));
        TextComparison.compare("", emoji);
    }

    @Test(expected=TextComparison.LimitExceeded.class)
    public void exceptionallyLongSingleLineIsNotLaidOut() throws Exception {
        TextComparison.compare(new String(new char[TextComparison.MAX_LINE_CHARS+1]), "");
    }
    @Test(expected=TextComparison.LimitExceeded.class)
    public void workBoundIsExplicit() throws Exception {
        TextComparison.compare(new String(new char[TextComparison.MAX_LINES+1]).replace('\0','\n'), "");
    }
}
