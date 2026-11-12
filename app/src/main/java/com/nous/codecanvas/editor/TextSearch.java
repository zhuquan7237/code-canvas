package com.nous.codecanvas.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds every occurrence of a literal string, for the code screen's search bar.
 *
 * <p>Deliberately plain-text, not regex: this stage is "find the same text again", and a regex
 * engine would make a search for {@code (} or {@code *} fail in a way users read as a bug. Case
 * sensitivity is the only option, and it is applied by comparing folded characters <b>without
 * changing the offsets</b> — a match must still map back to the exact span in the original text,
 * including for Chinese, symbols and pasted multi-line snippets.</p>
 */
public final class TextSearch {

    /** One match, as offsets into the searched text. */
    public static final class Match {
        public final int start;
        public final int end;

        Match(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int length() {
            return end - start;
        }

        @Override
        public String toString() {
            return "[" + start + "," + end + ")";
        }
    }

    public static final class Result {
        public final List<Match> matches;
        public final String query;
        public final boolean caseSensitive;

        Result(List<Match> matches, String query, boolean caseSensitive) {
            this.matches = matches;
            this.query = query;
            this.caseSensitive = caseSensitive;
        }

        public int count() {
            return matches.size();
        }

        public boolean isEmpty() {
            return matches.isEmpty();
        }

        /** 1-based index of the match at or after {@code offset}, or 0 when there is none. */
        public int indexAtOrAfter(int offset) {
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i).start >= offset) return i + 1;
            }
            return matches.isEmpty() ? 0 : 1;
        }

        /** 1-based index of the match containing or before {@code offset}, or 0 when none. */
        public int indexAtOrBefore(int offset) {
            for (int i = matches.size() - 1; i >= 0; i--) {
                if (matches.get(i).start <= offset) return i + 1;
            }
            return matches.isEmpty() ? 0 : matches.size();
        }
    }

    private TextSearch() {
    }

    /**
     * @param text          the text to search
     * @param query         the literal to find; empty means "no search"
     * @param caseSensitive whether case must match exactly
     */
    public static Result find(String text, String query, boolean caseSensitive) {
        List<Match> matches = new ArrayList<>();
        if (text == null || query == null || query.isEmpty()) {
            return new Result(matches, query == null ? "" : query, caseSensitive);
        }
        // Case folding can change the length of some characters in some locales, which would break
        // offset mapping. Folding per character keeps the two strings aligned.
        String haystack = caseSensitive ? text : fold(text);
        String needle = caseSensitive ? query : fold(query);
        if (needle.length() != query.length()) {
            // A fold that changes length cannot be mapped back; fall back to exact matching rather
            // than reporting offsets that point at the wrong characters.
            haystack = text;
            needle = query;
        }
        int from = 0;
        while (from <= haystack.length() - needle.length()) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) break;
            matches.add(new Match(at, at + needle.length()));
            // Overlapping matches are not useful for navigation, and stepping by one character
            // would make "aa" in "aaa" report two matches that highlight the same region.
            from = at + Math.max(1, needle.length());
        }
        return new Result(matches, query, caseSensitive);
    }

    /** Next index with wraparound: the last match goes back to the first. */
    public static int nextIndex(int current, int count) {
        if (count <= 0) return 0;
        return (current + 1) % count;
    }

    /** Previous index with wraparound: the first match goes back to the last. */
    public static int previousIndex(int current, int count) {
        if (count <= 0) return 0;
        return (current - 1 + count) % count;
    }

    /** "3 / 17" — the counter the search bar shows. */
    public static String counter(int currentIndex, int count) {
        if (count <= 0) return "0 / 0";
        return (currentIndex + 1) + " / " + count;
    }

    /**
     * The text a selection should pre-fill the search box with: the selected text when there is a
     * selection, otherwise null.
     */
    public static String queryFromSelection(CharSequence text, int selStart, int selEnd) {
        if (text == null || selStart < 0 || selEnd <= selStart || selEnd > text.length()) return null;
        String selected = text.subSequence(selStart, selEnd).toString();
        if (selected.trim().isEmpty()) return null;
        // A pasted multi-line snippet is a legitimate query; only a whole-document selection is
        // not, because that would make the search box unusable.
        if (selected.length() > 2000) return null;
        return selected;
    }

    /**
     * Per-character case folding that never changes length. Only ASCII is folded: it is the case
     * that matters in code, and it cannot shift offsets for Chinese or symbols.
     */
    static String fold(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c >= 'A' && c <= 'Z') {
                chars[i] = (char) (c + 32);
            }
        }
        return new String(chars);
    }

    static String describe(Result result) {
        if (result.query.isEmpty()) return "输入要查找的内容";
        if (result.isEmpty()) return "没有找到「" + result.query + "」";
        return result.count() + " 处匹配";
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
