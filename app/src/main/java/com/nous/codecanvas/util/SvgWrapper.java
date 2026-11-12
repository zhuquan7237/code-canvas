package com.nous.codecanvas.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The page an SVG is previewed in.
 *
 * <p>Two responsibilities, both learned from real defects:</p>
 * <ol>
 *   <li><b>Nothing is cropped.</b> An artwork authored for a full screen usually carries
 *       {@code preserveAspectRatio="…slice"} plus {@code width:100vw;height:100vh}. {@code slice}
 *       means "crop the overflow to fill the viewport", which on a portrait phone cuts a wide
 *       drawing down to its middle band. Any {@code slice} is rewritten to {@code meet} and the
 *       viewport units are stripped.</li>
 *   <li><b>Zooming stays vector and costs nothing per frame.</b> The page exposes
 *       {@code CCViewport.set(scale, dx, dy)}, which writes one CSS transform on the wrapper. The
 *       SVG is parsed once; a pinch changes a transform, not a document.</li>
 * </ol>
 *
 * <p>Links inside the artwork are neutralised: this surface owns pan and pinch, so a drag must
 * never navigate. Local files cannot reach the app either — the WebView has no JS bridge, no file
 * access and no network unless the user explicitly allows it.</p>
 */
public final class SvgWrapper {

    private static final Pattern ROOT_SVG_PATTERN = Pattern.compile("<svg\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESERVE_ASPECT_PATTERN =
            Pattern.compile("preserveAspectRatio\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESERVE_ASPECT_APOS_PATTERN =
            Pattern.compile("preserveAspectRatio\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);

    private SvgWrapper() {
    }

    /**
     * @param svgContent the document's source
     * @param darkTheme  page background follows the app theme
     */
    public static String wrapSvgInResponsiveHtml(String svgContent, boolean darkTheme) {
        return wrapSvgInResponsiveHtml(svgContent, darkTheme, null);
    }

    /**
     * @param viewBoxOverride a preview-only viewBox (from "fit to content"), or null to keep the
     *                        document's own. Never written back to the file.
     */
    public static String wrapSvgInResponsiveHtml(String svgContent, boolean darkTheme, String viewBoxOverride) {
        String bgColor = darkTheme ? "#121417" : "#F8FAFC";
        String gridColor = darkTheme ? "rgba(255,255,255,0.05)" : "rgba(0,0,0,0.04)";

        String sanitized = sanitizeRootSvg(svgContent);
        if (viewBoxOverride != null && !viewBoxOverride.trim().isEmpty()) {
            sanitized = overrideViewBox(sanitized, viewBoxOverride.trim());
        }

        return "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n"
                + "  <style>\n"
                + "    * { box-sizing: border-box; }\n"
                + "    html, body { width:100%; height:100%; margin:0; padding:0; overflow:hidden;\n"
                + "      background-color:" + bgColor + ";\n"
                + "      background-image: linear-gradient(" + gridColor + " 1px, transparent 1px),"
                + " linear-gradient(90deg, " + gridColor + " 1px, transparent 1px);\n"
                + "      background-size:20px 20px; }\n"
                + "    /* The transform layer. Its child is fitted once; the viewport only scales this. */\n"
                + "    #cc-stage { position:absolute; left:0; top:0; right:0; bottom:0;"
                + " transform-origin:0 0; will-change:transform; }\n"
                + "    .svg-wrapper { position:absolute; left:0; top:0; right:0; bottom:0; display:flex;\n"
                + "      align-items:center; justify-content:center; padding:8px;\n"
                + "      width: 100%; height: 100%; }\n"
                + "    .svg-wrapper svg {\n"
                + "      /* Both axes are capped so a wide or tall drawing arrives whole. */\n"
                + "      max-width: 100% !important;\n"
                + "      max-height: 100% !important;\n"
                + "      width: auto !important;\n"
                + "      height: auto !important;\n"
                + "      object-fit: contain !important;\n"
                + "      display: block; }\n"
                + "    #cc-error { position:absolute; left:0; right:0; bottom:0; padding:10px 14px;\n"
                + "      font:12px/1.5 -apple-system,'Noto Sans SC',sans-serif;\n"
                + "      color:#B42318; background:rgba(251,228,225,0.96); display:none; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div id=\"cc-stage\"><div id=\"cc-fit\" class=\"svg-wrapper\">\n"
                + sanitized + "\n"
                + "  </div></div>\n"
                + "  <div id=\"cc-error\"></div>\n"
                + "  <script>\n"
                + "  (function(){\n"
                + "    var stage = document.getElementById('cc-stage');\n"
                + "    var fit = document.getElementById('cc-fit');\n"
                + "    var errorBox = document.getElementById('cc-error');\n"
                + "    var svg = fit.querySelector('svg');\n"
                + "    if (!svg) {\n"
                + "      errorBox.textContent = '这段内容里没有找到 <svg> 根元素，无法按矢量图预览。';\n"
                + "      errorBox.style.display = 'block';\n"
                + "    }\n"
                + "    // A tap or a drag must never navigate away from the preview.\n"
                + "    document.addEventListener('click', function(e){\n"
                + "      var a = e.target && e.target.closest ? e.target.closest('a') : null;\n"
                + "      if (a) { e.preventDefault(); e.stopPropagation(); }\n"
                + "    }, true);\n"
                + "    var last = { scale: 1, dx: 0, dy: 0 };\n"
                + "    window.CCViewport = {\n"
                + "      set: function(scale, dx, dy){\n"
                + "        last = { scale: scale, dx: dx, dy: dy };\n"
                + "        stage.style.transform = 'translate(' + dx + 'px,' + dy + 'px) scale(' + scale + ')';\n"
                + "      },\n"
                + "      /* Content box in CSS pixels, for fit-to-window checks. */\n"
                + "      bounds: function(){\n"
                + "        if (!svg || !svg.getBoundingClientRect) return null;\n"
                + "        var r = svg.getBoundingClientRect();\n"
                + "        return { x:r.left, y:r.top, w:r.width, h:r.height,\n"
                + "                 vw:window.innerWidth, vh:window.innerHeight };\n"
                + "      },\n"
                + "      /* Measured content box of the drawing itself, in user units. */\n"
                + "      contentBox: function(){\n"
                + "        if (!svg || !svg.getBBox) return null;\n"
                + "        try { var b = svg.getBBox();\n"
                + "          return { x:b.x, y:b.y, w:b.width, h:b.height };\n"
                + "        } catch (e) { return null; }\n"
                + "      }\n"
                + "    };\n"
                + "    window.CCViewport.set(1, 0, 0);\n"
                + "    // A viewport resize (toolbar appearing, rotation, keyboard closing) changes where\n"
                + "    // the centre is. Re-centre ONLY when the user has not zoomed: resetting a zoom the\n"
                + "    // user set is exactly the \"big SVG will not zoom\" defect.\n"
                + "    var lastH = window.innerHeight, lastW = window.innerWidth;\n"
                + "    window.addEventListener('resize', function(){\n"
                + "      if (window.innerHeight === lastH && window.innerWidth === lastW) return;\n"
                + "      lastH = window.innerHeight; lastW = window.innerWidth;\n"
                + "      if (!window.CCViewport || !window.CCViewport.set) return;\n"
                + "      if (last.scale === 1 && last.dx === 0 && last.dy === 0) {\n"
                + "        window.CCViewport.set(1, 0, 0);\n"
                + "      } else {\n"
                + "        window.CCViewport.set(last.scale, last.dx, last.dy);\n"
                + "      }\n"
                + "    });\n"
                + "  })();\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>";
    }

    /**
     * Rewrite the root {@code <svg>} so the drawing arrives whole:
     * <ul>
     *   <li>{@code slice} → {@code meet} (crop → contain)</li>
     *   <li>{@code width:100vw}/{@code height:100vh} removed</li>
     *   <li>an explicit {@code preserveAspectRatio="xMidYMid meet"} added when absent</li>
     * </ul>
     */
    static String sanitizeRootSvg(String content) {
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
            if (val.toLowerCase(Locale.ROOT).contains("slice")) {
                updatedAttrs = aspectMatch.replaceFirst("preserveAspectRatio=\"xMidYMid meet\"");
            }
        } else {
            Matcher aspectAposMatch = PRESERVE_ASPECT_APOS_PATTERN.matcher(updatedAttrs);
            if (aspectAposMatch.find()) {
                String val = aspectAposMatch.group(1);
                if (val.toLowerCase(Locale.ROOT).contains("slice")) {
                    updatedAttrs = aspectAposMatch.replaceFirst("preserveAspectRatio=\"xMidYMid meet\"");
                }
            } else {
                // Not specified at all: add explicitly to guarantee 'meet'
                updatedAttrs = updatedAttrs + " preserveAspectRatio=\"xMidYMid meet\"";
            }
        }

        return content.substring(0, m.start(1)) + updatedAttrs + content.substring(m.end(1));
    }

    /** Preview-only viewBox swap. The document's own viewBox is left in the source untouched. */
    static String overrideViewBox(String content, String viewBox) {
        if (content == null || viewBox == null) return content;
        Matcher m = ROOT_SVG_PATTERN.matcher(content);
        if (!m.find()) return content;
        String attrs = m.group(1);
        String replaced;
        if (attrs.matches("(?is).*\\bviewBox\\s*=.*")) {
            replaced = attrs.replaceAll("(?is)viewBox\\s*=\\s*(\"[^\"]*\"|'[^']*')",
                    "viewBox=\"" + viewBox + "\"");
        } else {
            replaced = attrs + " viewBox=\"" + viewBox + "\"";
        }
        return content.substring(0, m.start(1)) + replaced + content.substring(m.end(1));
    }

    /** True when the source actually contains an SVG root, so the UI can explain a failed render. */
    public static boolean hasSvgRoot(String content) {
        return content != null && ROOT_SVG_PATTERN.matcher(content).find();
    }
}
