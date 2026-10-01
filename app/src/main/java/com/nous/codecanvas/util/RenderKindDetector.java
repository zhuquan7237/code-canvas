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

        // A real HTML document announces itself, and that beats any extension.
        if (lower.contains("<!doctype html")) {
            return RenderKind.HTML;
        }

        String root = rootElementName(head);
        if ("html".equals(root)) return RenderKind.HTML;
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
