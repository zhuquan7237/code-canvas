package com.nous.codecanvas.util;

public class XmlFormatter {

    public static String formatForDisplay(String rawXml) {
        if (rawXml == null) return "";
        // Indentation formatting and escaping helper
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        String[] lines = rawXml.replace(">", ">\n").replace("<", "\n<").split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("</")) {
                indent = Math.max(0, indent - 1);
            }
            for (int i = 0; i < indent; i++) {
                sb.append("  ");
            }
            sb.append(trimmed).append("\n");
            if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.startsWith("<?") && !trimmed.startsWith("<!") && !trimmed.endsWith("/>") && !trimmed.contains("</")) {
                indent++;
            }
        }
        return sb.toString().trim();
    }
}
