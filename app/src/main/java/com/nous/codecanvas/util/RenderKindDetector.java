package com.nous.codecanvas.util;

import java.util.Locale;
import java.util.regex.Pattern;

public class RenderKindDetector {

    // Known common HTML tag fragments (div, p, span, button, section, article, h1-h6, table, ul, ol, form, input)
    private static final Pattern HTML_FRAGMENT_PATTERN = Pattern.compile(
            "<(?:div|p|span|button|section|article|header|footer|nav|h[1-6]|table|ul|ol|li|form|input|label|textarea|select|main|aside)[\\s>/]",
            Pattern.CASE_INSENSITIVE
    );

    public static RenderKind detect(String fileName, String content) {
        String ext = FileUtils.getExtension(fileName).toLowerCase(Locale.ROOT);

        if (content == null || content.trim().isEmpty()) {
            if ("html".equals(ext) || "htm".equals(ext)) return RenderKind.HTML;
            if ("svg".equals(ext)) return RenderKind.SVG;
            if ("xml".equals(ext)) return RenderKind.XML;
            return RenderKind.RAW;
        }

        String sample = content.trim();
        if (sample.length() > 3000) {
            sample = sample.substring(0, 3000);
        }
        String lowerSample = sample.toLowerCase(Locale.ROOT);

        // 1. Full HTML check takes precedence over embedded SVG:
        // <!doctype html or <html> or <body
        boolean isFullHtml = lowerSample.contains("<!doctype html") ||
                (lowerSample.contains("<html") && lowerSample.contains("</html>")) ||
                (lowerSample.contains("<body") && lowerSample.contains("</body>"));

        if (isFullHtml) {
            return RenderKind.HTML;
        }

        // 2. Pure SVG check:
        // Starts with <svg, or starts with <?xml and contains <svg>
        boolean isSvgTag = lowerSample.startsWith("<svg") || lowerSample.matches("(?s)^<\\?xml[^>]*>\\s*<svg.*");
        if (isSvgTag && lowerSample.contains("</svg>")) {
            return RenderKind.SVG;
        }

        // Also if contains <svg tag and has svg xmlns or closing tag, and does NOT contain generic HTML wrapper
        if (lowerSample.contains("<svg") && lowerSample.contains("</svg>")) {
            // If it's not wrapped in <html>/<body> or html fragments
            if (!lowerSample.contains("<html") && !lowerSample.contains("<body") && !lowerSample.contains("<div")) {
                return RenderKind.SVG;
            }
        }

        // 3. Known HTML fragments: generic HTML with known tags (like <div>, <p>, <span>, <button>)
        if (HTML_FRAGMENT_PATTERN.matcher(lowerSample).find()) {
            return RenderKind.HTML;
        }

        // 4. Filename extension hints if content didn't strongly resolve above
        if ("html".equals(ext) || "htm".equals(ext)) {
            return RenderKind.HTML;
        }
        if ("svg".equals(ext)) {
            return RenderKind.SVG;
        }
        if ("xml".equals(ext)) {
            return RenderKind.XML;
        }

        // 5. XML check
        if (lowerSample.startsWith("<?xml") || (lowerSample.startsWith("<") && lowerSample.endsWith(">") && lowerSample.contains("</"))) {
            return RenderKind.XML;
        }

        return RenderKind.RAW;
    }
}
