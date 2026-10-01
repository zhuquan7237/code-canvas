package com.nous.codecanvas.util;

/**
 * Indents markup for on-screen reading — and refuses to when indenting would misrepresent it.
 *
 * <p>The previous version split the document on every {@code <} and {@code >} and re-indented the
 * pieces unconditionally. That quietly rewrote the reader's view of the file: {@code <p>Hello
 * <b>world</b> !</p>} lost its spacing, a {@code >} inside a quoted attribute broke a tag in half,
 * and CDATA was torn apart. Deep nesting was worse than cosmetic — each level added indentation to
 * every following line, so the output grew quadratically and a small document could produce an
 * enormous one.</p>
 *
 * <p>Now: only element-only markup is reformatted (text content may not be moved), depth and output
 * size are capped, and anything questionable is shown exactly as written. This only affects the
 * display; the stored document is never touched.</p>
 */
public final class XmlFormatter {

    /** Past this depth, indentation stops growing — otherwise output grows with depth squared. */
    private static final int MAX_INDENT_DEPTH = 64;

    private XmlFormatter() {
    }

    public static String formatForDisplay(String rawXml) {
        if (rawXml == null || rawXml.isEmpty()) {
            return "";
        }
        if (!isSafeToReformat(rawXml)) {
            return rawXml;
        }
        long limit = (long) rawXml.length() * 2L + 65536L;

        StringBuilder out = new StringBuilder(Math.min(rawXml.length() * 2, 1 << 20));
        int indent = 0;
        String[] lines = rawXml.replace(">", ">\n").replace("<", "\n<").split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("</")) {
                indent = Math.max(0, indent - 1);
            }
            for (int i = 0; i < indent; i++) {
                out.append("  ");
            }
            out.append(trimmed).append('\n');
            if (out.length() > limit) {
                return rawXml;
            }
            if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.startsWith("<?")
                    && !trimmed.startsWith("<!") && !trimmed.endsWith("/>") && !trimmed.contains("</")
                    && indent < MAX_INDENT_DEPTH) {
                indent++;
            }
        }
        return out.toString().trim();
    }

    /**
     * True only when every tag closes on an unquoted {@code >} and there is no text content between
     * tags. Both conditions exist so reformatting cannot move text that the author placed
     * deliberately.
     */
    static boolean isSafeToReformat(String raw) {
        int n = raw.length();
        int i = 0;
        boolean sawTag = false;
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '<') {
                if (raw.startsWith("<!--", i)) {
                    int end = raw.indexOf("-->", i + 4);
                    if (end < 0) return false;
                    i = end + 3;
                    continue;
                }
                if (raw.startsWith("<![CDATA[", i)) {
                    return false;   // CDATA is verbatim by definition
                }
                if (raw.startsWith("<?", i)) {
                    int end = raw.indexOf("?>", i + 2);
                    if (end < 0) return false;
                    i = end + 2;
                    continue;
                }
                if (raw.startsWith("<!", i)) {
                    int end = raw.indexOf('>', i + 2);
                    if (end < 0) return false;
                    i = end + 1;
                    continue;
                }
                char quote = 0;
                int j = i + 1;
                boolean closed = false;
                for (; j < n; j++) {
                    char d = raw.charAt(j);
                    if (quote != 0) {
                        if (d == quote) quote = 0;
                    } else if (d == '"' || d == '\'') {
                        quote = d;
                    } else if (d == '>') {
                        closed = true;
                        break;
                    }
                }
                if (!closed) return false;   // a '>' hidden in a quoted attribute, or truncated markup
                sawTag = true;
                i = j + 1;
                continue;
            }
            if (!Character.isWhitespace(c)) {
                return false;   // real text between tags: indenting it would change what is shown
            }
            i++;
        }
        return sawTag;
    }

    /** Whether whitespace inside this document is meaningful to whoever consumes it. */
    public static boolean preservesWhitespace(String raw) {
        return raw != null && (raw.contains("xml:space") || raw.contains("<![CDATA["));
    }
}
