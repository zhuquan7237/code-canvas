package com.nous.codecanvas.util;

/**
 * How source code becomes the HTML that the WebView renders.
 *
 * <p>This used to live inline in the editor, which meant the home-screen thumbnails (rendered
 * elsewhere) could disagree with what the editor showed. One place decides now.
 */
public final class PreviewHtml {

    private PreviewHtml() {}

    // Consume entire start tags including quoted attributes (notably iframe srcdoc).
    // Only outer metadata counts; markup in a quoted attribute or script is not a tag.
    private static final String TAG_END = "(?:\"[^\"]*\"|'[^']*'|[^\"'>]+)*+>";
    private static final java.util.regex.Pattern HEAD_TOKENS = java.util.regex.Pattern.compile(
            "(?is)<!--.*?(?:-->|$)|<(script|style|title|textarea|template|noscript)(?=[\\s/>])"
            + TAG_END + ".*?</\\1\\s*>|<([a-z][a-z0-9:_-]*)(?=[\\s/>])" + TAG_END);
    private static final java.util.regex.Pattern ATTRIBUTE = java.util.regex.Pattern.compile(
            "([^\\s=<>/\"']+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    // A copied source can carry a lone "/" before the HTML doctype. Besides being visible text,
    // it puts the browser in quirks mode and prevents detection of a single inline artwork.
    // Limit recovery to the document prologue and a complete HTML5 doctype, never body text.
    private static final java.util.regex.Pattern STRAY_DOCTYPE_SLASH = java.util.regex.Pattern.compile(
            "(?is)\\A[\\t\\r\\n\\f ]*(?:<!--.*?-->[\\t\\r\\n\\f ]*)*+(/)"
            + "(?=[\\t\\r\\n\\f ]*<!doctype[\\t\\r\\n\\f ]+html[\\t\\r\\n\\f ]*>)");

    private static String recoverHtmlPrologue(String source) {
        if (source == null || source.isEmpty()) return "";
        String html = source.charAt(0) == '\uFEFF' ? source.substring(1) : source;
        java.util.regex.Matcher slash = STRAY_DOCTYPE_SLASH.matcher(html);
        return slash.find() ? html.substring(0, slash.start(1)) + html.substring(slash.end(1)) : html;
    }

    /** The original page stays intact; only preview viewport metadata and artwork sizing change. */
    public static String forWebPage(String html) {
        html = recoverHtmlPrologue(html);
        String layout = "<style>body:has(>canvas:only-of-type):not(:has(>:not(canvas,script,style,template,noscript)))>canvas"
                + "{width:auto!important;height:auto!important;max-width:100%!important;max-height:100vh!important;object-fit:contain}"
                + "</style>";
        if (RenderKindDetector.isSingleInlineSvgDocument(html)) {
            SvgGeometry.Box box = SvgGeometry.measure(RenderKindDetector.extractInlineSvg(html));
            if (box.isValid() && Double.isFinite(box.aspect())) {
                double ratio = box.aspect();
                // Fit the viewport of a single inline stage to its viewBox. Thus an author's
                // "slice" still works as authored, but has no excess space to crop. We don't
                // touch multi-element HTML, script order, the SVG tree, or the original file.
                layout += "<style>body>svg{width:min(100%,100vw,calc(100vh * " + ratio
                        + "))!important;height:auto!important;aspect-ratio:" + ratio
                        + "!important;max-width:100%!important;max-height:none!important;"
                        + "min-height:0!important;min-width:0!important;align-self:center!important;"
                        + "justify-self:center!important;box-sizing:content-box!important}</style>";
            }
        }
        java.util.regex.Matcher tokens = HEAD_TOKENS.matcher(html);
        StringBuilder out = new StringBuilder(html.length() + layout.length() + 160);
        int copied = 0, headEnd = -1, htmlEnd = -1;
        boolean found = false;
        while (tokens.find()) {
            String kind = tokens.group(2);
            if (kind == null) continue;
            if ("head".equalsIgnoreCase(kind)) headEnd = tokens.end();
            if ("html".equalsIgnoreCase(kind)) htmlEnd = tokens.end();
            if (!"meta".equalsIgnoreCase(kind)) continue;
            String name = attribute(tokens.group(), "name");
            if (!"viewport".equalsIgnoreCase(name == null ? "" : name.trim())) continue;
            out.append(html, copied, tokens.start());
            if (!found) {
                String content = attribute(tokens.group(), "content");
                out.append(viewportTag(content)).append(layout);
            }
            copied = tokens.end();
            found = true;
        }
        if (found) return out.append(html, copied, html.length()).toString();
        String injected = viewportTag(null) + layout;
        if (headEnd >= 0) return html.substring(0, headEnd) + injected + html.substring(headEnd);
        if (htmlEnd >= 0) return html.substring(0, htmlEnd) + "<head>" + injected
                + "</head>" + html.substring(htmlEnd);
        // Place metadata after a real doctype so the page keeps its original rendering mode.
        java.util.regex.Matcher doctype = java.util.regex.Pattern.compile(
                "(?is)^\\s*(?:<!--.*?-->\\s*)*<!doctype[^>]*>").matcher(html);
        int offset = doctype.find() ? doctype.end() : 0;
        return html.substring(0, offset) + injected + html.substring(offset);
    }

    private static String attribute(String tag, String name) {
        java.util.regex.Matcher matcher = ATTRIBUTE.matcher(tag);
        while (matcher.find()) {
            if (!name.equalsIgnoreCase(matcher.group(1))) continue;
            for (int i = 2; i <= 4; i++) if (matcher.group(i) != null) return matcher.group(i);
        }
        return null;
    }

    private static String viewportTag(String content) {
        StringBuilder value = new StringBuilder();
        boolean width = false;
        if (content != null) {
            for (String entry : content.split("[,;]")) {
                int equals = entry.indexOf('=');
                if (equals < 0) continue;
                String key = entry.substring(0, equals).trim().toLowerCase(java.util.Locale.ROOT);
                if ("user-scalable".equals(key) || "maximum-scale".equals(key)
                        || "minimum-scale".equals(key)) continue;
                width |= "width".equals(key);
                value.append(entry.trim()).append(", ");
            }
        }
        if (!width) value.append("width=device-width, ");
        return "<meta name=\"viewport\" content=\"" + PreviewPage.escape(value.toString())
                + "minimum-scale=0.1, maximum-scale=8, user-scalable=yes\">";
    }

    /**
     * A page without a viewport meta tag is laid out on WebView's ~980px virtual canvas and then
     * zoomed out to fit, so the very same code can render large or tiny depending on whether the
     * author happened to add the tag. Add it when it is missing so HTML previews use the device
     * width consistently.
     */
    public static String ensureViewport(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        if (html.toLowerCase(java.util.Locale.ROOT).contains("name=\"viewport\"")
                || html.toLowerCase(java.util.Locale.ROOT).contains("name='viewport'")) {
            return html;
        }
        String tag = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">";
        String headOpen = "<head";
        int head = indexOfIgnoreCase(html, headOpen);
        if (head >= 0) {
            int close = html.indexOf('>', head);
            if (close > 0) {
                return html.substring(0, close + 1) + "\n" + tag + html.substring(close + 1);
            }
        }
        int htmlTag = indexOfIgnoreCase(html, "<html");
        if (htmlTag >= 0) {
            int close = html.indexOf('>', htmlTag);
            if (close > 0) {
                return html.substring(0, close + 1) + "\n<head>" + tag + "</head>\n" + html.substring(close + 1);
            }
        }
        // A fragment: wrap it so the tag has somewhere to live and the body has full width.
        return "<!DOCTYPE html>\n<html>\n<head><meta charset=\"utf-8\">" + tag + "</head>\n"
                + "<body style=\"margin:0\">\n" + html + "\n</body>\n</html>";
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(java.util.Locale.ROOT)
                .indexOf(needle.toLowerCase(java.util.Locale.ROOT));
    }

    /** The HTML handed to the WebView for this document. */
    public static String forDocument(String content, RenderKind kind, boolean dark) {
        if (content == null) {
            content = "";
        }
        if (kind == RenderKind.SVG) {
            return SvgWrapper.wrapSvgInResponsiveHtml(content, dark);
        }
        if (kind == RenderKind.HTML) {
            return forWebPage(content);
        }
        String escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "</head><body style=\"font-family:monospace;padding:16px;white-space:pre-wrap;margin:0;background:"
                + (dark ? "#121417;color:#F8FAFC;" : "#F8FAFC;color:#0F172A;") + "\">" + escaped
                + "</body></html>";
    }
}
