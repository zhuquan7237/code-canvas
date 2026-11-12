package com.nous.codecanvas.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides how a document should be rendered.
 *
 * <p>The old version sampled the first 3000 characters and required a closing {@code </svg>} before
 * it would believe a document was SVG. A long path graphic — exactly the SVG a user gets out of an
 * AI chat — has its closing tag far past that window, so it fell through to XML or plain text and
 * could not be drawn at all. It also flipped XML to HTML whenever a child element happened to be
 * called {@code div} or {@code p}.</p>
 *
 * <p>So: find the root element and let that decide, with the file extension as the author's stated
 * intent. Nothing here needs the document's tail.</p>
 */
public final class RenderKindDetector {

    /** Root elements that mean "this is a web page", not "this is XML that happens to be tagged". */
    private static final Set<String> HTML_ROOTS = new HashSet<>(Arrays.asList(
            "html", "div", "p", "span", "button", "section", "article", "header", "footer", "nav",
            "main", "aside", "table", "ul", "ol", "li", "form", "input", "label", "textarea",
            "select", "h1", "h2", "h3", "h4", "h5", "h6", "a", "img", "canvas", "style", "script"));

    /** How far into the document we are willing to look for the root element. */
    private static final int HEAD_LIMIT = 65536;

    private RenderKindDetector() {
    }

    public static RenderKind detect(String fileName, String content) {
        String ext = FileUtils.getExtension(fileName).toLowerCase(Locale.ROOT);

        if (content == null || content.trim().isEmpty()) {
            if (isHtmlExt(ext)) return RenderKind.HTML;
            if ("svg".equals(ext)) return RenderKind.SVG;
            if ("xml".equals(ext)) return RenderKind.XML;
            return RenderKind.RAW;
        }

        String head = content.length() > HEAD_LIMIT ? content.substring(0, HEAD_LIMIT) : content;
        String lower = head.toLowerCase(Locale.ROOT);

        // A real HTML document announces itself, and that beats any extension — with one
        // exception that matters in practice: AI tools hand over artwork as a full HTML page whose
        // body is a single inline <svg>. Treating that as a web page meant it lost the vector
        // viewport entirely, so a wide drawing was cropped by its own preserveAspectRatio="slice"
        // and could not be zoomed at all ("放进代码画布里就是无法缩放，显示不全"). When the page is
        // just a frame around one drawing, the drawing is what the user came to see.
        if (lower.contains("<!doctype html")) {
            return isSingleInlineSvgDocument(head) ? RenderKind.SVG : RenderKind.HTML;
        }

        String root = rootElementName(head);
        // An <html> root is a page — unless the page is only a frame around one drawing. Checked
        // here as well as in the doctype branch above, because plenty of AI-generated files omit
        // the doctype while still being a single inline <svg>.
        if ("html".equals(root)) {
            return isSingleInlineSvgDocument(head) ? RenderKind.SVG : RenderKind.HTML;
        }
        if ("svg".equals(root)) return RenderKind.SVG;

        // Otherwise the author's extension is the strongest signal.
        if (isHtmlExt(ext)) return RenderKind.HTML;
        if ("svg".equals(ext)) return RenderKind.SVG;
        if ("xml".equals(ext)) return RenderKind.XML;

        if (root == null) {
            return RenderKind.RAW;
        }
        return HTML_ROOTS.contains(root) ? RenderKind.HTML : RenderKind.XML;
    }

    /**
     * True when an HTML page is nothing but a wrapper around one inline {@code <svg>}.
     *
     * <p>The test is "exactly one top-level drawing, and no other visible content". A page with a
     * heading, a caption, a button, or a layout container around the drawing is a web page and must
     * stay one — only the drawing-alone case is reclassified, so the vector viewport can be used
     * without changing what real HTML documents mean.</p>
     */
    static boolean isSingleInlineSvgDocument(String head) {
        if (head == null) return false;
        String lower = head.toLowerCase(Locale.ROOT);

        int open = lower.indexOf("<svg");
        if (open < 0) return false;

        // Count top-level drawings only: a nested <svg> is part of the same artwork, a sibling one
        // is a second artwork (a gallery), which is a page.
        int close = matchingSvgEnd(lower, open);
        if (close < 0) return false;
        if (lower.indexOf("<svg", close) >= 0) return false;

        String before = lower.substring(0, open);
        String after = lower.substring(close);
        return onlyScaffolding(before) && onlyScaffolding(after);
    }

    /**
     * The single inline {@code <svg>} element of an HTML page, or null when there is not exactly
     * one.
     *
     * <p>The SVG preview path wraps its input in a page of its own, so handing it a whole HTML
     * document would nest a page inside the drawing stage. Only the drawing element is extracted;
     * everything else in the wrapper page is scaffolding by definition.</p>
     */
    public static String extractInlineSvg(String content) {
        if (content == null) return null;
        String lower = content.toLowerCase(Locale.ROOT);
        int open = lower.indexOf("<svg");
        if (open < 0) return null;
        int end = matchingSvgEnd(lower, open);
        if (end < 0) return null;
        if (lower.indexOf("<svg", end) >= 0) return null;
        return content.substring(open, end);
    }

    /**
     * Index just past the {@code </svg>} that closes the element opened at {@code open}, or -1.
     *
     * <p>{@code depth} starts at 1 because the caller has already found the opening tag. Starting
     * at 0 made the first close tag take it to -1 and return a position inside {@code </svg>},
     * which truncated the extracted element.</p>
     */
    private static int matchingSvgEnd(String lower, int open) {
        int depth = 1;
        int i = open + 4;
        int n = lower.length();
        while (i < n) {
            int nextOpen = lower.indexOf("<svg", i);
            int nextClose = lower.indexOf("</svg", i);
            if (nextClose < 0) return -1;
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                i = nextOpen + 4;
                continue;
            }
            depth--;
            // "</svg" is five characters and the closing '>' is the sixth; advancing by five left
            // the pointer sitting on '>', which then looked like a stray tag to the scaffolding
            // scan and truncated the extracted element.
            int gt = lower.indexOf('>', nextClose);
            i = gt >= 0 ? gt + 1 : nextClose + 5;
            if (depth == 0) return Math.min(i, n);
        }
        return -1;
    }

    /** Elements that produce no visible content of their own. */
    private static final Set<String> SCAFFOLDING = new HashSet<>(Arrays.asList(
            "!doctype", "html", "head", "body", "meta", "title", "style", "script", "link",
            "base", "noscript", "template"));

    /**
     * True when a fragment contains only scaffolding tags, comments and whitespace.
     *
     * <p>Text between tags counts as content: a caption after the drawing means the author wanted a
     * page. The first version of this method only looked at tag names and silently ignored text
     * that sat before a tag, which let a captioned page through as a bare drawing.</p>
     */
    private static boolean onlyScaffolding(String fragment) {
        int i = 0;
        int n = fragment.length();
        while (i < n) {
            int lt = fragment.indexOf('<', i);
            if (lt < 0) {
                if (!fragment.substring(i).trim().isEmpty()) return false;
                return true;
            }
            // Anything between the previous tag and this one must be whitespace.
            if (!fragment.substring(i, lt).trim().isEmpty()) return false;

            if (fragment.startsWith("<!--", lt)) {
                int end = fragment.indexOf("-->", lt + 4);
                if (end < 0) return false;
                i = end + 3;
                continue;
            }
            int j = lt + 1;
            if (j < n && fragment.charAt(j) == '/') j++;
            if (j < n && fragment.charAt(j) == '!') {
                int end = fragment.indexOf('>', j);
                if (end < 0) return false;
                i = end + 1;
                continue;
            }
            int start = j;
            while (j < n) {
                char c = fragment.charAt(j);
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') j++;
                else break;
            }
            String name = fragment.substring(start, j);
            if (!SCAFFOLDING.contains(name)) return false;
            int end = fragment.indexOf('>', j);
            if (end < 0) return false;
            i = end + 1;
            // <title>, <style> and <script> hold text that is metadata or code, never page
            // content: skip to their close tag instead of treating the contents as visible text or
            // as markup. A <title> is the document's name, a stylesheet contains "svg#stage { … }"
            // and a script can contain "<".
            if ("title".equals(name) || "style".equals(name) || "script".equals(name)) {
                int closeTag = fragment.indexOf("</" + name, i);
                if (closeTag < 0) return false;
                int closeGt = fragment.indexOf('>', closeTag);
                if (closeGt < 0) return false;
                i = closeGt + 1;
            }
        }
        return true;
    }

    private static boolean isHtmlExt(String ext) {
        return "html".equals(ext) || "htm".equals(ext) || "xhtml".equals(ext);
    }

    /**
     * Name of the first real element in the document, lower-cased, or null when the text does not
     * start with one. Skips the byte-order mark, comments, the XML declaration, a doctype and CDATA.
     */
    static String rootElementName(String text) {
        int i = 0;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            i = 1;
        }
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '<') {
                if (text.startsWith("<!--", i)) {
                    int end = text.indexOf("-->", i + 4);
                    if (end < 0) return null;
                    i = end + 3;
                    continue;
                }
                if (text.startsWith("<![CDATA[", i)) {
                    int end = text.indexOf("]]>", i + 9);
                    if (end < 0) return null;
                    i = end + 3;
                    continue;
                }
                if (text.startsWith("<?", i)) {          // <?xml ... ?> and friends
                    int end = text.indexOf("?>", i + 2);
                    if (end < 0) return null;
                    i = end + 2;
                    continue;
                }
                if (text.startsWith("<!", i)) {          // doctype or other declaration
                    int end = text.indexOf('>', i + 2);
                    if (end < 0) return null;
                    i = end + 1;
                    continue;
                }
                int j = i + 1;
                if (j < n && (text.charAt(j) == '/' || text.charAt(j) == '!' || text.charAt(j) == '?')) {
                    i = j + 1;
                    continue;
                }
                int start = j;
                while (j < n) {
                    char name = text.charAt(j);
                    if ((name >= 'a' && name <= 'z') || (name >= 'A' && name <= 'Z')
                            || (name >= '0' && name <= '9') || name == ':' || name == '-' || name == '_') {
                        j++;
                    } else {
                        break;
                    }
                }
                if (j > start) {
                    return text.substring(start, j).toLowerCase(Locale.ROOT);
                }
                i++;
                continue;
            }
            if (!Character.isWhitespace(c)) {
                // Leading text or punctuation before any element: not a markup document we can
                // classify from the root.
                return null;
            }
            i++;
        }
        return null;
    }
}
