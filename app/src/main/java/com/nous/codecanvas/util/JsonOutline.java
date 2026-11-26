package com.nous.codecanvas.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded, source-backed JSON tree. Paths use RFC 9535 normalized syntax. */
public final class JsonOutline {
    static final int MAX_TEXT = 192 * 1024;
    static final int MAX_DEPTH = 64;
    static final int MAX_NODES = 1500;
    private static final int MAX_PATH = 1024;
    private static final int MAX_PATH_TOTAL = 192 * 1024;

    private JsonOutline() {}

    public static final class Result {
        public final String html;
        public final boolean limited;
        public final boolean ambiguous;
        public final boolean pathsOmitted;
        public final int errorOffset;

        private Result(String html, boolean limited, boolean ambiguous, boolean pathsOmitted,
                       int errorOffset) {
            this.html = html;
            this.limited = limited;
            this.ambiguous = ambiguous;
            this.pathsOmitted = pathsOmitted;
            this.errorOffset = errorOffset;
        }
    }

    public static Result inspect(String content) {
        String text = content == null ? "" : content;
        if (text.length() > MAX_TEXT) return new Result("", true, false, false, -1);
        Parser parser = new Parser(text);
        try {
            // Ignore an optional UTF-8 BOM decoded at the beginning, without changing the file.
            if (!text.isEmpty() && text.charAt(0) == '\ufeff') parser.at++;
            Node root = parser.value(0);
            parser.space();
            if (parser.at != text.length()) throw parser.invalid();
            StringBuilder html = new StringBuilder();
            parser.render(html, root, "$", 0, false, false);
            return new Result(html.toString(), false, parser.ambiguous, parser.pathsOmitted, -1);
        } catch (ReadFailure e) {
            return new Result("", e.limited, false, false, e.limited ? -1 : parser.at);
        }
    }

    static int[] position(String text, int offset) {
        int line = 1, column = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            char c = text.charAt(i);
            if (i == 0 && c == '\ufeff') continue;
            if (c == '\r' || c == '\n') {
                if (c == '\r' || i == 0 || text.charAt(i - 1) != '\r') line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new int[]{line, column};
    }

    private static final class ReadFailure extends RuntimeException {
        final boolean limited;
        ReadFailure(boolean limited) { this.limited = limited; }
    }

    private static final class Node {
        int start, end, keyStart = -1, keyEnd;
        String name;
        boolean duplicate;
        List<Node> children;
    }

    private static final class Parser {
        final String text;
        int at, count, pathChars;
        boolean ambiguous, pathsOmitted;

        Parser(String text) { this.text = text; }

        ReadFailure invalid() { return new ReadFailure(false); }

        char peek() { return at < text.length() ? text.charAt(at) : '\0'; }

        void space() {
            while (at < text.length()) {
                char c = peek();
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
                at++;
            }
        }

        boolean eat(char c) {
            if (at == text.length() || peek() != c) return false;
            at++;
            return true;
        }

        void expect(char c) { if (!eat(c)) throw invalid(); }

        Node value(int depth) {
            if (depth > MAX_DEPTH || ++count > MAX_NODES) throw new ReadFailure(true);
            space();
            Node node = new Node();
            node.start = at;
            char c = peek();
            switch (c) {
                case '{':
                case '[': {
                    at++;
                    boolean object = c == '{';
                    char end = object ? '}' : ']';
                    node.children = new ArrayList<>();
                    Map<String, Node> names = object ? new HashMap<>() : null;
                    space();
                    if (!eat(end)) {
                        do {
                            space();
                            int keyStart = at;
                            int keyEnd = keyStart;
                            String name = null;
                            if (object) {
                                name = string(true);
                                keyEnd = at;
                                space();
                                expect(':');
                            }
                            Node child = value(depth + 1);
                            if (object) {
                                child.keyStart = keyStart;
                                child.keyEnd = keyEnd;
                                child.name = name;
                                Node earlier = names.put(name, child);
                                if (earlier != null) {
                                    earlier.duplicate = child.duplicate = true;
                                    ambiguous = true;
                                }
                            }
                            node.children.add(child);
                            space();
                        } while (eat(','));
                        expect(end);
                    }
                    break;
                }
                case '"': string(false); break;
                case 't': literal("true"); break;
                case 'f': literal("false"); break;
                case 'n': literal("null"); break;
                default: number(); break;
            }
            node.end = at;
            return node;
        }

        void literal(String token) {
            for (int i = 0; i < token.length(); i++) expect(token.charAt(i));
        }

        static boolean digit(char c) { return c >= '0' && c <= '9'; }

        void digits() {
            if (!digit(peek())) throw invalid();
            do { at++; } while (digit(peek()));
        }

        void number() {
            eat('-');
            if (!eat('0')) digits();
            if (eat('.')) digits();
            if (eat('e') || eat('E')) {
                if (!eat('+')) eat('-');
                digits();
            }
        }

        String string(boolean decode) {
            expect('"');
            StringBuilder value = decode ? new StringBuilder() : null;
            while (at < text.length()) {
                char c = peek();
                if (c == '"') {
                    at++;
                    return decode ? value.toString() : null;
                }
                if (c < 0x20) throw invalid();
                at++;
                if (c == '\\') {
                    c = peek();
                    switch (c) {
                        case '"': case '\\': case '/': at++; break;
                        case 'b': c = '\b'; at++; break;
                        case 'f': c = '\f'; at++; break;
                        case 'n': c = '\n'; at++; break;
                        case 'r': c = '\r'; at++; break;
                        case 't': c = '\t'; at++; break;
                        case 'u':
                            at++;
                            int code = 0;
                            for (int j = 0; j < 4; j++) {
                                char h = peek();
                                int n = digit(h) ? h - '0'
                                        : h >= 'a' && h <= 'f' ? h - 'a' + 10
                                        : h >= 'A' && h <= 'F' ? h - 'A' + 10 : -1;
                                if (n < 0) throw invalid();
                                code = (code << 4) | n;
                                at++;
                            }
                            c = (char) code;
                            break;
                        default: throw invalid();
                    }
                }
                if (decode) value.append(c);
            }
            throw invalid();
        }

        void render(StringBuilder out, Node node, String proposedPath, int depth,
                    boolean inheritedDuplicate, boolean comma) {
            boolean duplicate = inheritedDuplicate || node.duplicate;
            String path = duplicate ? null : proposedPath;
            if (path != null) {
                if (pathChars + path.length() <= MAX_PATH_TOTAL) pathChars += path.length();
                else path = null;
            }
            if (!duplicate && path == null) pathsOmitted = true;
            if (node.children != null && !node.children.isEmpty()) {
                out.append("<details class=\"cc-node\"");
                if (depth <= 2) out.append(" open");
                out.append("><summary>");
                label(out, node);
                out.append(PreviewPage.escape(text.substring(node.start, node.start + 1)))
                        .append(" <span class=\"cc-count\">").append(node.children.size())
                        .append("</span><span class=\"cc-fold\"> … ")
                        .append(text.charAt(node.end - 1)).append("</span></summary>");
                copy(out, node, path);
                out.append(depth < 9 ? "<div class=\"cc-children\">" : "<div>");
                for (int i = 0; i < node.children.size(); i++) {
                    Node child = node.children.get(i);
                    String subpath = path == null || child.duplicate ? null
                            : child.keyStart == -1 ? path + "[" + i + "]"
                            : memberPath(path, child.name);
                    if (subpath != null && subpath.length() > MAX_PATH) subpath = null;
                    render(out, child, subpath, depth + 1, duplicate, i + 1 < node.children.size());
                }
                out.append("</div><div class=\"cc-br\">").append(text.charAt(node.end - 1));
                if (comma) out.append(',');
                out.append("</div></details>");
            } else {
                out.append("<div class=\"cc-row\"><code>");
                label(out, node);
                if (node.children != null) {
                    out.append(text.charAt(node.start)).append(text.charAt(node.end - 1));
                } else {
                    out.append(PreviewPage.escape(text.substring(node.start, node.end)));
                }
                if (comma) out.append(',');
                out.append("</code>");
                copy(out, node, path);
                out.append("</div>");
            }
        }

        void label(StringBuilder out, Node node) {
            if (node.keyStart >= 0) out.append("<span class=\"k\">")
                    .append(PreviewPage.escape(text.substring(node.keyStart, node.keyEnd)))
                    .append("</span>: ");
        }

        void copy(StringBuilder out, Node node, String path) {
            if (node.duplicate) {
                out.append(" <span class=\"cc-count\">重复键，无唯一路径</span>");
            } else if (path != null) {
                String safe = PreviewPage.escape(path);
                out.append(" <button type=\"button\" class=\"cc-path\" dir=\"ltr\" aria-label=\"复制路径：")
                        .append(safe).append("\" data-path=\"").append(safe)
                        .append("\">").append(safe).append("</button>");
            }
        }
    }

    private static String memberPath(String prefix, String key) {
        if (key.length() + prefix.length() + 4 > MAX_PATH) return null;
        StringBuilder result = new StringBuilder(prefix).append("['");
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            switch (c) {
                case '\'': result.append("\\'"); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\t': result.append("\\t"); break;
                case '\n': result.append("\\n"); break;
                case '\f': result.append("\\f"); break;
                case '\r': result.append("\\r"); break;
                default:
                    if (c < 0x20) {
                        String hex = "0123456789abcdef";
                        result.append("\\u00").append(hex.charAt(c >> 4)).append(hex.charAt(c & 15));
                    } else if (Character.isHighSurrogate(c)) {
                        if (i + 1 >= key.length() || !Character.isLowSurrogate(key.charAt(i + 1))) {
                            return null;
                        }
                        result.append(c).append(key.charAt(++i));
                    } else if (Character.isLowSurrogate(c)) {
                        return null;
                    } else {
                        result.append(c);
                    }
            }
        }
        return result.append("']").toString();
    }
}
