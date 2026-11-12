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
    private static final Pattern CODE_NUMBER = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern PY_KEYWORD = words(
            "def|class|import|from|return|if|elif|else|for|while|try|except|finally|with|as|"
                    + "lambda|yield|pass|break|continue|in|not|and|or|True|False|None|async|await");
    private static final Pattern JS_KEYWORD = words(
            "function|const|let|var|async|await|return|if|else|for|while|class|import|export|from|"
                    + "new|this|typeof|interface|type|extends|implements|public|private|static|"
                    + "void|null|undefined|true|false|switch|case|break|continue");
    private static final Pattern KT_KEYWORD = words(
            "fun|class|val|var|if|else|when|return|object|interface|package|import|override|"
                    + "suspend|data|companion|init|null|true|false|private|public|internal");
    private static final Pattern SQL_KEYWORD = words(
            "select|from|where|join|inner|left|right|on|group|by|order|insert|into|values|"
                    + "update|set|delete|create|table|and|or|not|null|as|distinct|limit");
    private static final Pattern SH_KEYWORD = words(
            "if|then|else|elif|fi|for|do|done|while|case|esac|echo|export|local|return|"
                    + "function|in|fi");
    private static final Pattern CSS_PROPERTY = Pattern.compile("(?m)[a-zA-Z-]+(?=\\s*:)");
    private static final Pattern HASH_COMMENT = Pattern.compile("(?m)#.*$");
    private static final Pattern SLASH_COMMENT = Pattern.compile("(?m)//.*$|/\\*[\\s\\S]*?\\*/");
    private static final Pattern SQL_COMMENT = Pattern.compile("(?m)--.*$|/\\*[\\s\\S]*?\\*/");
    private static final int WINDOW_CAP = 6000;

    private static Pattern words(String body) {
        return Pattern.compile("\\b(?:" + body + ")\\b");
    }

    /** Which keyword set a file name should use. Unknown extensions stay on the shared set. */
    public static String languageForTitle(String title) {
        if (title == null) return "code";
        int dot = title.lastIndexOf('.');
        String ext = dot >= 0 ? title.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
        switch (ext) {
            case "py":
            case "pyw":
                return "py";
            case "js":
            case "jsx":
            case "mjs":
            case "cjs":
            case "ts":
            case "tsx":
                return "js";
            case "kt":
            case "kts":
            case "java":
                return "kt";
            case "css":
            case "scss":
            case "less":
                return "css";
            case "sql":
                return "sql";
            case "sh":
            case "bash":
            case "zsh":
                return "sh";
            case "yaml":
            case "yml":
                return "yaml";
            default:
                return "code";
        }
    }

    public static void highlight(Editable editable, boolean isDark) {
        if (editable == null || editable.length() == 0) return;
        highlightRange(editable, isDark, 0, editable.length());
    }

    /**
     * Colour only {@code [start, end)}.
     *
     * <p>Whole-document highlighting is what made typing in a large SVG lag: four regex passes over
     * hundreds of kilobytes, producing tens of thousands of span objects, on the UI thread after
     * every keystroke. The editor now passes the visible window instead.</p>
     *
     * <p>Spans are matched against a substring and then offset back, so a match that straddles the
     * window edge is simply not painted — the next repaint picks it up once it scrolls in.</p>
     */
    public static void highlightRange(Editable editable, boolean isDark, int start, int end) {
        if (editable == null || editable.length() == 0) return;
        int from = Math.max(0, Math.min(start, editable.length()));
        int to = Math.max(from, Math.min(end, editable.length()));

        // Strip previous spans inside the window only: spans outside it stay valid, and removing
        // them would force a repaint of the whole document.
        ForegroundColorSpan[] spans = editable.getSpans(from, to, ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
        if (to <= from) return;

        int tagColor = isDark ? NIGHT_TAG : DAY_TAG;
        int attrColor = isDark ? NIGHT_ATTR : DAY_ATTR;
        int strColor = isDark ? NIGHT_STRING : DAY_STRING;
        int commentColor = isDark ? NIGHT_COMMENT : DAY_COMMENT;

        String window = editable.subSequence(from, to).toString();

        Matcher tagMatcher = XML_TAG_PATTERN.matcher(window);
        while (tagMatcher.find()) {
            setSpan(editable, from, tagMatcher.start(), tagMatcher.end(), tagColor);
        }

        Matcher attrMatcher = XML_ATTR_PATTERN.matcher(window);
        while (attrMatcher.find()) {
            setSpan(editable, from, attrMatcher.start(), attrMatcher.end(), attrColor);
        }

        Matcher strMatcher = STRING_PATTERN.matcher(window);
        while (strMatcher.find()) {
            setSpan(editable, from, strMatcher.start(), strMatcher.end(), strColor);
        }

        Matcher commentMatcher = COMMENT_PATTERN.matcher(window);
        while (commentMatcher.find()) {
            setSpan(editable, from, commentMatcher.start(), commentMatcher.end(), commentColor);
        }
    }

    /**
     * Colour for ordinary source files. Keywords, strings, numbers and comments only.
     * This does not parse or run the language.
     */
    public static void highlightCode(Editable editable, boolean isDark, int start, int end) {
        highlightCode(editable, isDark, start, end, "code");
    }

    /**
     * Colour one language inside the visible window only. The window is capped so a fast scroll
     * through a large file cannot run the matchers over tens of thousands of characters.
     */
    public static void highlightCode(Editable editable, boolean isDark, int start, int end,
                                     String language) {
        if (editable == null || editable.length() == 0) return;
        int from = Math.max(0, Math.min(start, editable.length()));
        int to = Math.max(from, Math.min(end, editable.length()));
        if (to - from > WINDOW_CAP) to = from + WINDOW_CAP;
        ForegroundColorSpan[] spans = editable.getSpans(from, to, ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) editable.removeSpan(span);
        if (to <= from) return;
        int keyColor = isDark ? NIGHT_ATTR : DAY_ATTR;
        int strColor = isDark ? NIGHT_STRING : DAY_STRING;
        int numColor = isDark ? NIGHT_TAG : DAY_TAG;
        int commentColor = isDark ? NIGHT_COMMENT : DAY_COMMENT;
        String window = editable.subSequence(from, to).toString();
        String lang = language == null ? "code" : language;
        Pattern keywords = keywordsFor(lang);
        if ("css".equals(lang)) {
            apply(editable, from, CSS_PROPERTY.matcher(window), keyColor);
        } else if (keywords != null) {
            apply(editable, from, keywords.matcher(window), keyColor);
        }
        apply(editable, from, CODE_NUMBER.matcher(window), numColor);
        apply(editable, from, STRING_PATTERN.matcher(window), strColor);
        apply(editable, from, commentFor(lang).matcher(window), commentColor);
    }

    private static Pattern keywordsFor(String language) {
        switch (language) {
            case "py": return PY_KEYWORD;
            case "js": return JS_KEYWORD;
            case "kt": return KT_KEYWORD;
            case "sql": return SQL_KEYWORD;
            case "sh":
            case "yaml": return SH_KEYWORD;
            case "css": return null;
            default: return JS_KEYWORD;
        }
    }

    private static Pattern commentFor(String language) {
        switch (language) {
            case "py":
            case "sh":
            case "yaml":
                return HASH_COMMENT;
            case "sql":
                return SQL_COMMENT;
            default:
                return SLASH_COMMENT;
        }
    }

    private static void apply(Editable editable, int offset, Matcher matcher, int color) {
        while (matcher.find()) setSpan(editable, offset, matcher.start(), matcher.end(), color);
    }

    private static void setSpan(Editable editable, int offset, int start, int end, int color) {
        int s = offset + start;
        int e = offset + end;
        if (s < 0 || e > editable.length() || s >= e) return;
        editable.setSpan(new ForegroundColorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}