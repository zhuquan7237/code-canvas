package com.nous.codecanvas.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Search counting, wrapping and offset mapping. */
public class TextSearchTest {

    @Test
    public void countsEveryOccurrence() {
        TextSearch.Result r = TextSearch.find("alpha beta alpha gamma alpha", "alpha", true);
        assertEquals(3, r.count());
        assertEquals(0, r.matches.get(0).start);
        assertEquals(11, r.matches.get(1).start);
    }

    @Test
    public void caseSensitivityIsHonoured() {
        assertEquals(2, TextSearch.find("Alpha alpha", "alpha", false).count());
        assertEquals(1, TextSearch.find("Alpha alpha", "alpha", true).count());
    }

    @Test
    public void chineseIsFound() {
        TextSearch.Result r = TextSearch.find("异种不锈钢激光焊接\n焊缝成形", "激光", false);
        assertEquals(1, r.count());
        assertEquals(5, r.matches.get(0).start);
    }

    @Test
    public void symbolsAreFoundLiterally() {
        // A regex engine would treat these as metacharacters; this is plain text.
        assertEquals(2, TextSearch.find("a(b) c(d)", "(", true).count());
        assertEquals(1, TextSearch.find("x*y", "*", true).count());
        assertEquals(2, TextSearch.find("a.b.c", ".", true).count());
    }

    @Test
    public void multiLinePasteIsFoundAsOneMatch() {
        String text = "before\nline one\nline two\nafter";
        TextSearch.Result r = TextSearch.find(text, "line one\nline two", true);
        assertEquals(1, r.count());
        assertEquals("line one\nline two", text.substring(r.matches.get(0).start, r.matches.get(0).end));
    }

    @Test
    public void offsetsMapBackToTheOriginalTextExactly() {
        String text = "Hello HELLO hello";
        TextSearch.Result r = TextSearch.find(text, "hello", false);
        assertEquals(3, r.count());
        for (TextSearch.Match m : r.matches) {
            assertEquals("hello", text.substring(m.start, m.end).toLowerCase());
        }
    }

    @Test
    public void matchesDoNotOverlap() {
        // "aa" in "aaa" is one match at 0; the leftover "a" is not a second highlight, and
        // stepping character-by-character would report a phantom second one.
        assertEquals(1, TextSearch.find("aaa", "aa", true).count());
        assertEquals(2, TextSearch.find("aaaa", "aa", true).count());
    }

    @Test
    public void emptyQueryFindsNothing() {
        TextSearch.Result r = TextSearch.find("anything", "", true);
        assertTrue(r.isEmpty());
        assertEquals("0 / 0", TextSearch.counter(0, 0));
    }

    @Test
    public void navigationWrapsBothWays() {
        assertEquals(1, TextSearch.nextIndex(0, 3));
        assertEquals(0, TextSearch.nextIndex(2, 3));
        assertEquals(2, TextSearch.previousIndex(0, 3));
        assertEquals(1, TextSearch.previousIndex(2, 3));
        assertEquals(0, TextSearch.nextIndex(0, 0));
    }

    @Test
    public void counterIsOneBased() {
        assertEquals("1 / 3", TextSearch.counter(0, 3));
        assertEquals("3 / 3", TextSearch.counter(2, 3));
    }

    @Test
    public void indexLookupsFindTheRightStartingPoint() {
        TextSearch.Result r = TextSearch.find("a a a a", "a", true);
        assertEquals(1, r.indexAtOrAfter(0));
        assertEquals(4, r.indexAtOrAfter(5));
        assertEquals(2, r.indexAtOrBefore(3));
        assertEquals(4, r.indexAtOrBefore(999));
    }

    @Test
    public void selectionPrefillsTheQuery() {
        String text = "alpha beta gamma";
        assertEquals("beta", TextSearch.queryFromSelection(text, 6, 10));
        assertNull("no selection means no prefill", TextSearch.queryFromSelection(text, 4, 4));
        assertNull("whitespace-only selection is not a query",
                TextSearch.queryFromSelection(text, 5, 6));
    }

    @Test
    public void describeIsHonestAboutNoResults() {
        TextSearch.Result r = TextSearch.find("abc", "zzz", true);
        assertTrue(TextSearch.describe(r).contains("没有找到"));
        assertFalse(TextSearch.describe(TextSearch.find("abc", "b", true)).contains("没有"));
    }

    @Test
    public void searchOverALargeDocumentIsStillExact() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5000; i++) big.append("row ").append(i).append(" NEEDLE\n");
        TextSearch.Result r = TextSearch.find(big.toString(), "needle", false);
        assertEquals(5000, r.count());
    }
}
