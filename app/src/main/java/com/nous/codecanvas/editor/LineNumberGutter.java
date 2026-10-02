package com.nous.codecanvas.editor;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

/**
 * Line-number gutter for the code editor.
 *
 * <p>Kept as pure string/offset maths so it can be unit tested: the editor itself cannot be
 * verified without a device, and "the numbers stopped matching the code" is exactly the kind of
 * bug that would otherwise only show up in a screenshot.</p>
 */
public final class LineNumberGutter {

    /** Beyond this the gutter string itself becomes the memory problem; the code is unreadable anyway. */
    public static final int MAX_LINES = 20000;

    private LineNumberGutter() {}

    /** 1-based line count of a document. An empty document is one line, like every editor. */
    public static int lineCount(CharSequence text) {
        if (text == null || text.length() == 0) return 1;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return Math.min(lines, MAX_LINES);
    }

    /** 1-based line that contains this cursor offset. */
    public static int lineOf(CharSequence text, int offset) {
        if (text == null) return 1;
        int limit = Math.max(0, Math.min(offset, text.length()));
        int line = 1;
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return Math.min(line, MAX_LINES);
    }

    /** "1\n2\n...\nN" — the plain gutter text. */
    public static String numbers(int lineCount) {
        int n = Math.max(1, Math.min(lineCount, MAX_LINES));
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 1; i <= n; i++) {
            if (i > 1) sb.append('\n');
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * The gutter as displayed: the current line is emphasised so the cursor's row is findable
     * without hunting. Returns a plain string when there is nothing to emphasise.
     */
    public static CharSequence highlighted(int lineCount, int currentLine,
                                          int normalColor, int activeColor) {
        String text = numbers(lineCount);
        if (currentLine < 1 || currentLine > lineCount) return text;
        SpannableString span = new SpannableString(text);
        int start = 0;
        for (int line = 1; line < currentLine; line++) {
            start = text.indexOf('\n', start) + 1;
        }
        int end = text.indexOf('\n', start);
        if (end < 0) end = text.length();
        span.setSpan(new ForegroundColorSpan(activeColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }
}
