package com.nous.codecanvas.util;

import java.util.ArrayDeque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collapsible structure HTML for XML and YAML. Error lines link back into the editor
 * with {@code https://codecanvas.invalid/jump?line=N}.
 */
public final class StructureView {

    private static final Pattern XML_LINE = Pattern.compile(
            "(?i)(?:line(?:\\s*number)?|row)\\s*[:=]?\\s*(\\d+)");

    private StructureView() {
    }

    public static int xmlErrorLine(String message) {
        if (message == null) return 0;
        Matcher matcher = XML_LINE.matcher(message);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String xmlHtml(String formatted, int errorLine) {
        if (formatted == null || formatted.isEmpty()) return "";
        String[] lines = formatted.split("\n", -1);
        StringBuilder out = new StringBuilder();
        ArrayDeque<String> open = new ArrayDeque<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trim = line.trim();
            boolean closing = trim.startsWith("</");
            boolean opening = trim.startsWith("<") && !closing && !trim.startsWith("<?")
                    && !trim.startsWith("<!");
            boolean self = trim.endsWith("/>") || trim.contains("</");
            String row = rowHtml("cc-xml", line, i + 1, errorLine);
            if (closing && !opening) {
                out.append(row);
                if (!open.isEmpty()) {
                    open.pop();
                    out.append("</div></details>");
                }
            } else if (opening && !self) {
                out.append("<details open class=\"cc-fold\"><summary>").append(row)
                        .append("</summary><div class=\"cc-kids\">");
                open.push(trim);
            } else {
                out.append(row);
            }
        }
        while (!open.isEmpty()) {
            open.pop();
            out.append("</div></details>");
        }
        return out.toString();
    }

    public static String yamlHtml(List<StructuredText.YamlRow> rows) {
        StringBuilder out = new StringBuilder();
        if (rows == null) return "";
        ArrayDeque<Integer> depths = new ArrayDeque<>();
        for (int i = 0; i < rows.size(); i++) {
            StructuredText.YamlRow row = rows.get(i);
            while (!depths.isEmpty() && depths.peek() >= row.depth) {
                out.append("</div></details>");
                depths.pop();
            }
            boolean child = i + 1 < rows.size() && rows.get(i + 1).depth > row.depth;
            String cls = row.problem ? "cc-yaml bad" : "cc-yaml";
            String body = "<span class=\"cc-ln\">" + row.line + "</span> "
                    + PreviewPage.escape(row.label);
            if (row.problem) {
                body += " <a href=\"https://codecanvas.invalid/jump?line=" + row.line + "\">定位</a>";
            }
            if (child) {
                out.append("<details open class=\"cc-fold\"><summary class=\"").append(cls)
                        .append("\">").append(body).append("</summary><div class=\"cc-kids\">");
                depths.push(row.depth);
            } else {
                out.append("<div class=\"").append(cls).append("\" style=\"margin-left:")
                        .append(row.depth * 12).append("px\">").append(body).append("</div>");
            }
        }
        while (!depths.isEmpty()) {
            out.append("</div></details>");
            depths.pop();
        }
        return out.toString();
    }

    private static String rowHtml(String cls, String line, int lineNo, int errorLine) {
        boolean bad = errorLine > 0 && errorLine == lineNo;
        StringBuilder row = new StringBuilder();
        row.append("<div class=\"").append(cls);
        if (bad) row.append(" bad");
        row.append("\"><span class=\"cc-ln\">").append(lineNo).append("</span> ")
                .append(PreviewPage.escape(line));
        if (bad) {
            row.append(" <a href=\"https://codecanvas.invalid/jump?line=").append(lineNo)
                    .append("\">定位</a>");
        }
        row.append("</div>");
        return row.toString();
    }
}
