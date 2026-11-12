package com.nous.codecanvas.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validation and formatting for the structured-text formats.
 *
 * <p>Every one of these rules exists because the naive version is wrong in a way a user notices:</p>
 * <ul>
 *   <li><b>JSON</b> — formatting must never change a number. Re-serialising through a
 *       {@code double} turns {@code 1.0} into {@code 1} and {@code 12345678901234567890} into
 *       {@code 1.2345678901234567E19}, so the formatter re-indents the original text and leaves
 *       every literal exactly as written. Errors report a line and column.</li>
 *   <li><b>JSONL</b> — one broken line must not hide the other lines, so each is parsed on its own
 *       and the failures are listed by line number.</li>
 *   <li><b>YAML</b> — no YAML engine is shipped, so this is an honest indentation/tab/structure
 *       check that says what it can and cannot verify, rather than pretending to validate.</li>
 * </ul>
 */
public final class StructuredText {

    private StructuredText() {
    }

    // ------------------------------------------------------------------ JSON

    public static final class JsonResult {
        public final boolean valid;
        public final String formatted;
        public final String error;
        public final int errorLine;
        public final int errorColumn;

        JsonResult(boolean valid, String formatted, String error, int errorLine, int errorColumn) {
            this.valid = valid;
            this.formatted = formatted;
            this.error = error;
            this.errorLine = errorLine;
            this.errorColumn = errorColumn;
        }

        public String errorLabel() {
            if (valid) return "";
            if (errorLine > 0) return "第 " + errorLine + " 行第 " + errorColumn + " 列：" + error;
            return error;
        }
    }

    /**
     * Validate, then re-indent <b>without touching any literal</b>.
     *
     * <p>The re-indent walks the source and only inserts or removes whitespace between tokens;
     * strings are copied verbatim (including escapes) and numbers are copied as written. That is
     * the difference between "formatted" and "silently altered".</p>
     */
    public static JsonResult formatJson(String source) {
        String text = source == null ? "" : source;
        try {
            new JSONObject(text).toString();  // syntax check only; its output is not used
        } catch (Exception objectFailure) {
            try {
                new JSONArray(text).toString();
            } catch (Exception arrayFailure) {
                return failure(text, arrayFailure);
            }
        }
        String formatted = reindent(text);
        if (formatted == null) return failure(text, new Exception("无法格式化这段 JSON"));
        return new JsonResult(true, formatted, null, 0, 0);
    }

    private static JsonResult failure(String text, Exception e) {
        int[] position = locateError(text, e);
        String message = e.getMessage() == null ? "JSON 语法错误" : e.getMessage();
        return new JsonResult(false, null, message, position[0], position[1]);
    }

    /**
     * Best-effort position for a JSON error. {@code org.json} messages sometimes carry a character
     * index; when they do not, the first structural imbalance is reported instead of a made-up
     * number.
     */
    static int[] locateError(String text, Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:at character|character)\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(message);
        int index = -1;
        if (m.find()) {
            try {
                index = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        if (index < 0) index = firstStructuralProblem(text);
        if (index < 0) return new int[]{0, 0};
        int line = 1;
        int column = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new int[]{line, column};
    }

    /** Index of the first unbalanced brace/bracket outside a string, or -1. */
    static int firstStructuralProblem(String text) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth < 0) return i;
            }
        }
        return inString || depth != 0 ? Math.max(0, text.length() - 1) : -1;
    }

    /**
     * Re-indent JSON by walking its tokens. Strings and numbers are copied byte for byte.
     * Returns null when the text is not tokenisable (the caller then reports the parse error).
     */
    static String reindent(String text) {
        StringBuilder out = new StringBuilder(text.length() + 64);
        int indent = 0;
        int i = 0;
        int n = text.length();
        boolean lineStart = true;
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            switch (c) {
                case '{':
                case '[':
                    out.append(c);
                    indent++;
                    out.append('\n');
                    appendIndent(out, indent);
                    lineStart = true;
                    i++;
                    break;
                case '}':
                case ']':
                    indent = Math.max(0, indent - 1);
                    trimTrailingNewline(out);
                    out.append('\n');
                    appendIndent(out, indent);
                    out.append(c);
                    lineStart = false;
                    i++;
                    break;
                case ',':
                    out.append(',');
                    out.append('\n');
                    appendIndent(out, indent);
                    lineStart = true;
                    i++;
                    break;
                case ':':
                    out.append(": ");
                    lineStart = false;
                    i++;
                    break;
                case '"': {
                    int end = endOfString(text, i);
                    if (end < 0) return null;
                    out.append(text, i, end);
                    lineStart = false;
                    i = end;
                    break;
                }
                default: {
                    int end = i;
                    while (end < n) {
                        char t = text.charAt(end);
                        if (t == ',' || t == '}' || t == ']' || t == ':' || Character.isWhitespace(t)) break;
                        end++;
                    }
                    if (end == i) return null;
                    out.append(text, i, end);
                    lineStart = false;
                    i = end;
                    break;
                }
            }
        }
        return out.toString().trim();
    }

    private static void appendIndent(StringBuilder out, int indent) {
        for (int i = 0; i < indent; i++) out.append("  ");
    }

    private static void trimTrailingNewline(StringBuilder out) {
        while (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last == '\n' || last == ' ') {
                out.setLength(out.length() - 1);
            } else {
                break;
            }
        }
    }

    /** Index just past the closing quote of the string starting at {@code start}, or -1. */
    private static int endOfString(String text, int start) {
        boolean escaped = false;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i + 1;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ JSONL

    public static final class JsonlLine {
        public final int line;
        public final boolean valid;
        public final String preview;
        public final String error;

        JsonlLine(int line, boolean valid, String preview, String error) {
            this.line = line;
            this.valid = valid;
            this.preview = preview;
            this.error = error;
        }
    }

    public static final class JsonlResult {
        public final List<JsonlLine> lines = new ArrayList<>();
        public int validCount;
        public int invalidCount;

        public String summary() {
            if (lines.isEmpty()) return "空文件";
            if (invalidCount == 0) return lines.size() + " 行全部有效";
            return validCount + " 行有效 · " + invalidCount + " 行有错误";
        }
    }

    /** Parse each line independently, so one broken line never hides the rest. */
    public static JsonlResult parseJsonl(String source) {
        JsonlResult result = new JsonlResult();
        if (source == null) return result;
        String[] raw = source.split("\r\n|\r|\n", -1);
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i];
            if (line.trim().isEmpty()) continue;
            try {
                String trimmed = line.trim();
                if (trimmed.startsWith("{")) {
                    new JSONObject(trimmed);
                } else if (trimmed.startsWith("[")) {
                    new JSONArray(trimmed);
                } else {
                    throw new Exception("每行必须是一个 JSON 对象或数组");
                }
                result.lines.add(new JsonlLine(i + 1, true, abbreviate(trimmed), null));
                result.validCount++;
            } catch (Exception e) {
                String message = e.getMessage() == null ? "JSON 语法错误" : e.getMessage();
                result.lines.add(new JsonlLine(i + 1, false, abbreviate(line.trim()), message));
                result.invalidCount++;
            }
        }
        return result;
    }

    private static String abbreviate(String text) {
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }

    // ------------------------------------------------------------------ YAML

    /**
     * What can honestly be checked without a YAML engine: tabs used for indentation (invalid in
     * YAML), inconsistent indentation steps, and unbalanced flow brackets. Anything deeper — anchors,
     * tags, multi-document streams — is reported as "not verified" rather than guessed at.
     */
    public static List<String> checkYaml(String source) {
        List<String> problems = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) {
            problems.add("文件是空的");
            return problems;
        }
        String[] lines = source.split("\r\n|\r|\n", -1);
        int previousIndent = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            if (line.startsWith("---") || line.startsWith("...")) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            if (indent < line.length() && line.charAt(indent) == '\t') {
                problems.add("第 " + (i + 1) + " 行用制表符缩进；YAML 只接受空格");
            }
            if (previousIndent >= 0 && indent > previousIndent && indent - previousIndent < 2) {
                problems.add("第 " + (i + 1) + " 行缩进只多了 " + (indent - previousIndent)
                        + " 个空格，层级可能不清晰");
            }
            previousIndent = indent;
            if (problems.size() >= 8) break;
        }
        int flow = flowBalance(source);
        if (flow != 0) {
            problems.add(flow > 0 ? "有 " + flow + " 个未闭合的 [ 或 {" : "多出 " + (-flow) + " 个 ] 或 }");
        }
        return problems;
    }

    private static int flowBalance(String source) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inSingle) {
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (c == '\'') inSingle = true;
            else if (c == '"') inDouble = true;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
        }
        return depth;
    }

    /**
     * A one-line verdict for the preview badge.
     *
     * <p>Always states what was <b>not</b> checked. No YAML engine ships with the app, so claiming
     * "valid YAML" from an indentation check would be a lie the user could act on.</p>
     */
    public static String yamlVerdict(List<String> problems) {
        String limit = "（只检查缩进与括号，未做完整 YAML 语义校验）";
        if (problems.isEmpty()) return "缩进与括号检查通过" + limit;
        return problems.size() + " 处需要注意" + limit;
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
