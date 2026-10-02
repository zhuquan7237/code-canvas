package com.nous.codecanvas.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps SVG markup in responsive HTML so it can be previewed safely in a WebView.
 *
 * <p>Fixes the "imported SVG only shows the middle band" bug:
 * 1. Overrides any {@code preserveAspectRatio="...slice..."} to {@code "xMidYMid meet"}.
 *    "slice" tells the renderer to crop overflowing dimensions to fill the viewport, which
 *    destroys wide artworks on portrait mobile screens.
 * 2. Enforces {@code max-width: 100% !important} and {@code max-height: 100% !important} so
 *    wide or tall drawings fit entirely within the screen without losing any content.
 * 3. Sanitizes root {@code width: 100vw; height: 100vh} styling that forces cropping.
 */
public class SvgWrapper {

    private static final Pattern ROOT_SVG_PATTERN = Pattern.compile("<svg\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESERVE_ASPECT_PATTERN = Pattern.compile("preserveAspectRatio\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESERVE_ASPECT_APOS_PATTERN = Pattern.compile("preserveAspectRatio\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);

    public static String wrapSvgInResponsiveHtml(String svgContent, boolean darkTheme) {
        String bgColor = darkTheme ? "#121417" : "#F8FAFC";
        String gridColor = darkTheme ? "rgba(255,255,255,0.05)" : "rgba(0,0,0,0.04)";

        String sanitizedSvg = sanitizeRootSvg(svgContent);

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">\n" +
                "  <style>\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "    html, body {\n" +
                "      width: 100%;\n" +
                "      height: 100%;\n" +
                "      background-color: " + bgColor + ";\n" +
                "      background-image: linear-gradient(" + gridColor + " 1px, transparent 1px), linear-gradient(90deg, " + gridColor + " 1px, transparent 1px);\n" +
                "      background-size: 20px 20px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      overflow: auto;\n" +
                "      padding: 8px;\n" +
                "    }\n" +
                "    .svg-wrapper {\n" +
                "      width: 100%;\n" +
                "      height: 100%;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "    }\n" +
                "    .svg-wrapper svg {\n" +
                "      /* Ensure full containment: both width and height are respected without cropping */\n" +
                "      max-width: 100% !important;\n" +
                "      max-height: 100% !important;\n" +
                "      width: auto !important;\n" +
                "      height: auto !important;\n" +
                "      object-fit: contain !important;\n" +
                "      display: block;\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"svg-wrapper\">\n" +
                sanitizedSvg + "\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private static String sanitizeRootSvg(String content) {
        if (content == null) return "";
        Matcher m = ROOT_SVG_PATTERN.matcher(content);
        if (!m.find()) {
            return content;
        }

        String attrs = m.group(1);
        String updatedAttrs = attrs;

        // 1. Remove inline 100vw/100vh from root tag if present
        updatedAttrs = updatedAttrs.replaceAll("(?i)(width\\s*:\\s*100vw\\s*;?)", "");
        updatedAttrs = updatedAttrs.replaceAll("(?i)(height\\s*:\\s*100vh\\s*;?)", "");

        // 2. Replace or add preserveAspectRatio="xMidYMid meet"
        Matcher aspectMatch = PRESERVE_ASPECT_PATTERN.matcher(updatedAttrs);
        if (aspectMatch.find()) {
            String val = aspectMatch.group(1);
            if (val.toLowerCase().contains("slice")) {
                updatedAttrs = aspectMatch.replaceFirst("preserveAspectRatio=\"xMidYMid meet\"");
            }
        } else {
            Matcher aspectAposMatch = PRESERVE_ASPECT_APOS_PATTERN.matcher(updatedAttrs);
            if (aspectAposMatch.find()) {
                String val = aspectAposMatch.group(1);
                if (val.toLowerCase().contains("slice")) {
                    updatedAttrs = aspectAposMatch.replaceFirst("preserveAspectRatio=\"xMidYMid meet\"");
                }
            } else {
                // Not specified at all: add explicitly to guarantee 'meet'
                updatedAttrs = updatedAttrs + " preserveAspectRatio=\"xMidYMid meet\"";
            }
        }

        return content.substring(0, m.start(1)) + updatedAttrs + content.substring(m.end(1));
    }
}
