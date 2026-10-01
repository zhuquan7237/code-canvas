package com.nous.codecanvas.util;

/**
 * How source code becomes the HTML that the WebView renders.
 *
 * <p>This used to live inline in the editor, which meant the home-screen thumbnails (rendered
 * elsewhere) could disagree with what the editor showed. One place decides now.
 */
public final class PreviewHtml {

    private PreviewHtml() {}

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
            return ensureViewport(content);
        }
        String escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "</head><body style=\"font-family:monospace;padding:16px;white-space:pre-wrap;margin:0;background:"
                + (dark ? "#121417;color:#F8FAFC;" : "#F8FAFC;color:#0F172A;") + "\">" + escaped
                + "</body></html>";
    }
}
