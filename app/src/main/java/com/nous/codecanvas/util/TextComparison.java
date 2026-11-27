package com.nous.codecanvas.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline decoded-text comparison with explicit work and display limits. */
public final class TextComparison {
    public static final int MAX_CHARS = 65_536;
    public static final int MAX_LINES = 1000;
    public static final int MAX_LINE_CHARS = 1024;
    private TextComparison() {}

    public static final class Row {
        public final boolean added;
        public final int number;
        public final String text;

        Row(boolean added, int number, String text) {
            this.added = added;
            this.number = number;
            this.text = text;
        }
    }

    public static final class Result {
        public final List<Row> changes;
        public final int added, removed;
        public final boolean leftEndsWithNewline, rightEndsWithNewline;

        Result(List<Row> changes, int added, int removed, boolean leftFinal, boolean rightFinal) {
            this.changes = Collections.unmodifiableList(changes);
            this.added = added;
            this.removed = removed;
            this.leftEndsWithNewline = leftFinal;
            this.rightEndsWithNewline = rightFinal;
        }

        public boolean same() {
            return changes.isEmpty() && leftEndsWithNewline == rightEndsWithNewline;
        }
    }

    public static final class LimitExceeded extends Exception {}

    public static Result compare(String before, String after) throws LimitExceeded {
        String a = normalized(before);
        String b = normalized(after);
        boolean leftFinal = a.endsWith("\n"), rightFinal = b.endsWith("\n");
        String[] left = lines(a, leftFinal), right = lines(b, rightFinal);
        // Trim shared ends to avoid allocating a matrix for an unchanged document.
        int lo = 0, n = left.length, m = right.length;
        while (lo < n && lo < m && left[lo].equals(right[lo])) lo++;
        while (n > lo && m > lo && left[n-1].equals(right[m-1])) { n--; m--; }
        List<Row> changes = new ArrayList<>();
        int added = 0, removed = 0;
        int[] lcs = new int[(n-lo+1) * (m-lo+1)];
        int width = m-lo+1;
        for (int i=n-1; i>=lo; i--) {
            for (int j=m-1; j>=lo; j--) {
                int k=(i-lo)*width + j-lo;
                lcs[k] = left[i].equals(right[j]) ? 1+lcs[k+width+1]
                        : Math.max(lcs[k+width], lcs[k+1]);
            }
        }
        int i=lo, j=lo;
        while (i<n || j<m) {
            int k=(i-lo)*width + j-lo;
            if (i<n && j<m && left[i].equals(right[j])) { i++; j++; }
            else if (i<n && (j==m || lcs[k+width]>=lcs[k+1])) {
                changes.add(new Row(false, i+1, left[i++])); removed++;
            } else {
                changes.add(new Row(true, j+1, right[j++])); added++;
            }
        }
        return new Result(changes, added, removed, leftFinal, rightFinal);
    }

    private static String normalized(String input) throws LimitExceeded {
        if (exceedsCharacters(input, MAX_CHARS)) throw new LimitExceeded();
        return input.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String[] lines(String input, boolean finalNewline) throws LimitExceeded {
        if (input.isEmpty()) return new String[0];
        String[] rows = (finalNewline ? input.substring(0, input.length()-1) : input).split("\n", -1);
        if (rows.length > MAX_LINES) throw new LimitExceeded();
        for (String row : rows) if (exceedsCharacters(row, MAX_LINE_CHARS)) throw new LimitExceeded();
        return rows;
    }

    public static int characterCount(String text) {
        return text.codePointCount(0, text.length());
    }

    private static boolean exceedsCharacters(String value, int limit) {
        // A code point costs no more than two UTF-16 units; bound work before counting.
        return value.length() > limit && (value.length() > limit * 2 || characterCount(value) > limit);
    }

    /** A visual marker only; neither source string is changed. */
    public static String visibleTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end-1)==' ' || line.charAt(end-1)=='\t')) end--;
        StringBuilder display = new StringBuilder(line.substring(0,end));
        for (int i=end; i<line.length(); i++) display.append(line.charAt(i)==' ' ? '␠' : '⇥');
        return display.toString();
    }
}
