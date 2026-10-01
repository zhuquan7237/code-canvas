package com.nous.codecanvas.util;

import java.util.Locale;

public class RenderKindDetector {

    public static RenderKind detect(String fileName, String content) {
        String ext = FileUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
        if ("html".equals(ext) || "htm".equals(ext)) {
            return RenderKind.HTML;
        }
        if ("svg".equals(ext)) {
            return RenderKind.SVG;
        }
        if ("xml".equals(ext)) {
            return RenderKind.XML;
        }

        if (content == null || content.trim().isEmpty()) {
            return RenderKind.RAW;
        }

        String sample = content.trim();
        if (sample.length() > 2000) {
            sample = sample.substring(0, 2000);
        }
        String lowerSample = sample.toLowerCase(Locale.ROOT);

        // Check for SVG: contains <svg tag
        if (lowerSample.contains("<svg") && lowerSample.contains("</svg>") || lowerSample.matches("(?s).*<svg[\\s>].*")) {
            return RenderKind.SVG;
        }

        // Check for HTML: doctype html or <html> or <body>
        if (lowerSample.contains("<!doctype html") ||
            (lowerSample.contains("<html") && lowerSample.contains("</html>")) ||
            (lowerSample.contains("<body") && lowerSample.contains("</body>"))) {
            return RenderKind.HTML;
        }

        // Check for XML
        if (lowerSample.startsWith("<?xml") || (lowerSample.startsWith("<") && lowerSample.endsWith(">") && lowerSample.contains("</"))) {
            return RenderKind.XML;
        }

        return RenderKind.RAW;
    }
}
