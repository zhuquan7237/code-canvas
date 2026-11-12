package com.nous.codecanvas.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A collapsible reading of formatted JSON. Values are the original lines, so a number
 * is never rewritten. Each key carries a path such as {@code $.preview.scripts}.
 */
public final class JsonOutline {

    private JsonOutline() {
    }

    public static String toHtml(String formatted) {
        if (formatted == null || formatted.isEmpty()) return "";
        String[] lines = formatted.split("\n", -1);
        StringBuilder out = new StringBuilder();
        List<String> path = new ArrayList<>();
        int open = 0;
        for (String line : lines) {
            String trim = line.trim();
            if (trim.equals("{") || trim.equals("[")) {
                out.append("<div class=\"cc-br\">").append(escape(line)).append("</div>");
                continue;
            }
            if (trim.startsWith("}") || trim.startsWith("]")) {
                if (open > 0) {
                    path.remove(path.size() - 1);
                    out.append("</div></details>");
                    open--;
                }
                out.append("<div class=\"cc-br\">").append(escape(line)).append("</div>");
                continue;
            }
            String key = keyOf(trim);
            String rest = restOf(trim, key);
            boolean container = rest.startsWith("{") || rest.startsWith("[");
            if (key != null && container) {
                path.add(unquote(key));
                String shown = "$." + String.join(".", path);
                out.append("<details open class=\"cc-node\"><summary>")
                        .append("<span class=\"k\">").append(escape(key)).append("</span> ")
                        .append(pathChip(shown))
                        .append("</summary><div>");
                open++;
            } else if (key != null) {
                List<String> full = new ArrayList<>(path);
                full.add(unquote(key));
                String shown = "$." + String.join(".", full);
                out.append("<div class=\"cc-row\">").append(escape(line)).append(' ')
                        .append(pathChip(shown)).append("</div>");
            } else {
                out.append("<div class=\"cc-row\">").append(escape(line)).append("</div>");
            }
        }
        while (open-- > 0) out.append("</div></details>");
        return out.toString();
    }

    private static String pathChip(String path) {
        return "<button type=\"button\" class=\"cc-path\" data-path=\"" + escape(path) + "\">"
                + escape(path) + "</button>";
    }

    /** The quoted key when the line is {@code "key": ...}, otherwise null. */
    static String keyOf(String trim) {
        if (trim == null || !trim.startsWith("\"")) return null;
        int end = endOfString(trim);
        if (end < 0) return null;
        String after = trim.substring(end).trim();
        if (!after.startsWith(":")) return null;
        return trim.substring(0, end);
    }

    private static String restOf(String trim, String key) {
        if (key == null) return trim;
        String after = trim.substring(key.length()).trim();
        if (after.startsWith(":")) after = after.substring(1).trim();
        return after;
    }

    private static int endOfString(String text) {
        boolean escaped = false;
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i + 1;
            }
        }
        return -1;
    }

    private static String unquote(String quoted) {
        if (quoted.length() < 2) return quoted;
        return quoted.substring(1, quoted.length() - 1);
    }

    private static String escape(String text) {
        return PreviewPage.escape(text);
    }
}
