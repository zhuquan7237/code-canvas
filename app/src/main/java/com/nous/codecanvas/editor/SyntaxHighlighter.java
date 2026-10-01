package com.nous.codecanvas.editor;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token-driven syntax highlighting. The four palette values below are the canonical
 * day/night pairs asserted by {@code ColorContrastTest}; they mirror
 * {@code R.color.syntax_*} so spans always contrast against the code surface.
 * No hardcoded dark-mode stroke: the night set is genuinely lighter.
 */
public class SyntaxHighlighter {

    // Day
    public static final int DAY_TAG = 0xFF0F6B5C;
    public static final int DAY_ATTR = 0xFF1D4FB8;
    public static final int DAY_STRING = 0xFF156B32;
    public static final int DAY_COMMENT = 0xFF55637A;

    // Night
    public static final int NIGHT_TAG = 0xFF5FD3C0;
    public static final int NIGHT_ATTR = 0xFF9CC2FF;
    public static final int NIGHT_STRING = 0xFF7EE0A0;
    public static final int NIGHT_COMMENT = 0xFF9AA9C0;

    private static final Pattern XML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z0-9_:-]+(\\s|/?>)");
    private static final Pattern XML_ATTR_PATTERN = Pattern.compile("\\s([a-zA-Z0-9_:-]+)=");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--[\\s\\S]*?-->|/\\*[\\s\\S]*?\\*/|//.*");

    public static void highlight(Editable editable, boolean isDark) {
        if (editable == null || editable.length() == 0) return;

        // Strip previous spans
        ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) {
            editable.removeSpan(span);
        }

        int tagColor = isDark ? NIGHT_TAG : DAY_TAG;
        int attrColor = isDark ? NIGHT_ATTR : DAY_ATTR;
        int strColor = isDark ? NIGHT_STRING : DAY_STRING;
        int commentColor = isDark ? NIGHT_COMMENT : DAY_COMMENT;

        // Apply Tag regex
        Matcher tagMatcher = XML_TAG_PATTERN.matcher(editable);
        while (tagMatcher.find()) {
            editable.setSpan(new ForegroundColorSpan(tagColor), tagMatcher.start(), tagMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Apply Attr regex
        Matcher attrMatcher = XML_ATTR_PATTERN.matcher(editable);
        while (attrMatcher.find()) {
            editable.setSpan(new ForegroundColorSpan(attrColor), attrMatcher.start(), attrMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Apply String regex
        Matcher strMatcher = STRING_PATTERN.matcher(editable);
        while (strMatcher.find()) {
            editable.setSpan(new ForegroundColorSpan(strColor), strMatcher.start(), strMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Apply Comment regex
        Matcher commentMatcher = COMMENT_PATTERN.matcher(editable);
        while (commentMatcher.find()) {
            editable.setSpan(new ForegroundColorSpan(commentColor), commentMatcher.start(), commentMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}