package com.nous.codecanvas.editor;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    private static final int COLOR_TAG = 0xFF0D9488; // Teal
    private static final int COLOR_ATTR = 0xFF2563EB; // Blue
    private static final int COLOR_STRING = 0xFF16A34A; // Green
    private static final int COLOR_COMMENT = 0xFF64748B; // Muted slate

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

        int tagColor = isDark ? 0xFF2DD4BF : 0xFF0D9488;
        int attrColor = isDark ? 0xFF60A5FA : 0xFF2563EB;
        int strColor = isDark ? 0xFF4ADE80 : 0xFF16A34A;
        int commentColor = isDark ? 0xFF94A3B8 : 0xFF64748B;

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
