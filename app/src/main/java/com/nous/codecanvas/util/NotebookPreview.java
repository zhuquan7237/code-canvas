package com.nous.codecanvas.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Static, offline rendering for Jupyter Notebook v4. Never evaluates notebook content. */
public final class NotebookPreview {
    public static final int MAX_FILE = 8 * 1024 * 1024;
    private static final int MAX_CELLS = 250;
    private static final int MAX_OUTPUTS = 24;
    private static final int MAX_TOTAL_OUTPUTS = 420;
    private static final int MAX_TEXT = 32_000;
    private static final int MAX_PAGE_TEXT = 350_000;
    private static final int MAX_IMAGE = 2_000_000;
    private static final int MAX_IMAGE_TOTAL = 3_000_000;
    private static final int MAX_PIXELS_TOTAL = 6_000_000;

    private NotebookPreview() {}

    public static String fromFile(File file, boolean dark) {
        if (file.length() > MAX_FILE) return notice(dark, "文件超过 8 MB，无法预览。仍可导出原文件。");
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) return notice(dark, "预览已取消。");
                if (bytes.size() + read > MAX_FILE)
                    return notice(dark, "文件超过 8 MB，无法预览。仍可导出原文件。");
                bytes.write(buffer, 0, read);
            }
            return render(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString(), dark);
        } catch (IOException invalidFile) {
            return notice(dark, "无法读取 Notebook。原文件内容未修改。");
        }
    }

    public static String render(String json, boolean dark) {
        try {
            if (json.startsWith("\uFEFF")) json = json.substring(1);
            if (json.length() > MAX_FILE) return notice(dark, "文件超过 8 MB，无法预览。仍可导出原文件。");
            if (!withinDepth(json)) return notice(dark, "Notebook 的结构过深或格式无效，无法预览。");
            JSONTokener tokener = new JSONTokener(json);
            Object value = tokener.nextValue();
            if (!(value instanceof JSONObject) || tokener.nextClean() != 0)
                return notice(dark, "无法识别 Notebook 内容。");
            JSONObject root = (JSONObject) value;
            if (root.optInt("nbformat", -1) != 4)
                return notice(dark, "目前只支持 Jupyter Notebook v4 格式。");
            JSONArray cells = root.optJSONArray("cells");
            if (cells == null) return notice(dark, "Notebook 缺少单元列表。");
            Page page = new Page();
            page.out.append("<header><strong>Jupyter Notebook</strong><p>离线只读 · ")
                    .append(cells.length()).append(" 个单元 · 不执行代码</p></header>");
            if (cells.length() == 0) page.out.append("<p class=note>此 Notebook 没有单元。</p>");
            for (int i = 0; i < Math.min(cells.length(), MAX_CELLS); i++) {
                if (Thread.currentThread().isInterrupted()) return notice(dark, "预览已取消。");
                page.cell(i, cells.optJSONObject(i));
            }
            if (cells.length() > MAX_CELLS) page.out.append("<p class=note>仅预览前 250 个单元。</p>");
            return wrap(dark, page.out.toString());
        } catch (JSONException invalidJson) {
            return notice(dark, "Notebook 内容不完整或格式无效。原文件内容未修改。");
        }
    }

    private static boolean withinDepth(String json) {
        int depth = 0;
        int tokens = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quote == 0 && (c == '{' || c == '[' || c == ',') && ++tokens > 180_000) return false;
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '"') quote = c;
            else if (c == '[' || c == '{') {
                if (++depth > 48) return false;
            } else if (c == ']' || c == '}') {
                if (--depth < 0) return false;
            } else if (c == '/' || c == '#' || c == '=' || c == ';' || c == '\'') {
                // Android JSONTokener allows comments and other extensions. Reject those so that
                // its nesting cannot differ from this preflight scan.
                return false;
            }
        }
        return depth == 0 && quote == 0;
    }

    private static class Page {
        final StringBuilder out = new StringBuilder(8192);
        int textLeft = MAX_PAGE_TEXT;
        int imagesLeft = MAX_IMAGE_TOTAL;
        int pixelsLeft = MAX_PIXELS_TOTAL;
        int outputsLeft = MAX_TOTAL_OUTPUTS;

        void cell(int index, JSONObject cell) {
            out.append("<article aria-label=\"单元 ").append(index + 1).append("\"><div class=label>")
                    .append(index + 1).append(" · ");
            String type = cell == null ? "" : cell.optString("cell_type");
            String source = boundedText(cell == null ? null : cell.opt("source"));
            switch (type) {
                case "markdown":
                    out.append("Markdown</div><section class=md>");
                    markdown(source);
                    out.append("</section>");
                    break;
                case "code":
                    out.append("代码</div>");
                    pre(source);
                    JSONArray outputs = cell.optJSONArray("outputs");
                    if (outputs == null || outputs.length() == 0) {
                        out.append("<p class=note>没有已保存的输出</p>");
                    } else {
                        int count = Math.min(outputsLeft, Math.min(MAX_OUTPUTS, outputs.length()));
                        outputsLeft -= count;
                        for (int i = 0; i < count; i++) output(outputs.optJSONObject(i));
                        if (outputs.length() > count) out.append("<p class=note>其余输出已省略。</p>");
                    }
                    break;
                case "raw":
                    out.append("原始文本</div>");
                    pre(source);
                    break;
                default:
                    out.append("不支持的单元类型</div>");
            }
            out.append("</article>");
        }

        void markdown(String source) {
            boolean code = false;
            for (String line : source.split("\n", -1)) {
                if (line.startsWith("\u0060\u0060\u0060")) {
                    out.append(code ? "</pre>" : "<pre>");
                    code = !code;
                } else if (code) {
                    escape(out, line);
                    out.append('\n');
                } else {
                    int heading = 0;
                    while (heading < Math.min(6, line.length()) && line.charAt(heading) == '#') heading++;
                    if (heading > 0 && heading < line.length() && line.charAt(heading) == ' ') {
                        int level = Math.min(6, heading + 1);
                        out.append("<h").append(level).append(">");
                        escape(out, line.substring(heading + 1));
                        out.append("</h").append(level).append(">");
                    } else {
                        escape(out, line);
                        out.append('\n');
                    }
                }
            }
            if (code) out.append("</pre>");
        }

        String boundedText(Object value) {
            int allowance = Math.min(textLeft, MAX_TEXT);
            String result = joined(value, allowance);
            if (result == null) {
                result = prefix(value, allowance);
                textLeft -= result.length();
                return result + "\n[…其余文本未显示]";
            }
            textLeft -= result.length();
            return result;
        }

        void pre(String text) {
            out.append("<pre>");
            escape(out, text);
            out.append("</pre>");
        }

        void textOutput(String text) {
            boolean longText = text.length() > 1000 || text.chars().filter(c -> c == '\n').limit(12).count() >= 12;
            if (longText) out.append("<details><summary>展开较长输出</summary>");
            pre(text);
            if (longText) out.append("</details>");
        }

        void output(JSONObject output) {
            if (output == null) return;
            out.append("<div class=output><div class=label>已保存的输出</div>");
            switch (output.optString("output_type")) {
                case "stream":
                    textOutput(boundedText(output.opt("text")));
                    break;
                case "error":
                    Object trace = output.opt("traceback");
                    if (trace == null) trace = output.optString("ename") + ": " + output.optString("evalue");
                    textOutput(boundedText(trace).replaceAll("\u001b\\[[0-9;]{0,30}m", ""));
                    break;
                case "display_data":
                case "execute_result":
                    JSONObject data = output.optJSONObject("data");
                    if (data != null && (image(data, true) || image(data, false))) break;
                    if (data != null && data.has("text/plain")) {
                        textOutput(boundedText(data.opt("text/plain")));
                        break;
                    }
                    out.append("<p class=note>此输出无法安全预览。</p>");
                    break;
                default:
                    out.append("<p class=note>此输出类型暂不支持。</p>");
            }
            out.append("</div>");
        }

        boolean image(JSONObject data, boolean png) {
            if (imagesLeft <= 0 || pixelsLeft <= 0) return false;
            String type = png ? "image/png" : "image/jpeg";
            String raw = joined(data.opt(type), Math.min(MAX_IMAGE, imagesLeft));
            if (raw == null || raw.isEmpty()) return false;
            try {
                String canonical = raw.replace("\n", "").replace("\r", "").replace(" ", "").replace("\t", "");
                byte[] decoded = Base64.getDecoder().decode(canonical);
                int pixels = pixels(decoded, png, pixelsLeft);
                if (pixels <= 0) return false;
                imagesLeft -= raw.length();
                pixelsLeft -= pixels;
                out.append("<img alt=\"已保存的图表或图片\" src=\"data:").append(type)
                        .append(";base64,").append(canonical).append("\">");
                return true;
            } catch (IllegalArgumentException invalidBase64) {
                return false;
            }
        }
    }

    private static int pixels(byte[] bytes, boolean png, int maximum) {
        if (png) {
            if (bytes.length < 24 || ByteBuffer.wrap(bytes).getLong() != 0x89504E470D0A1A0AL
                    || ByteBuffer.wrap(bytes, 8, 8).getLong() != 0x0000000D49484452L) return 0;
            ByteBuffer dimensions = ByteBuffer.wrap(bytes, 16, 8);
            int area = pixelCount(dimensions.getInt(), dimensions.getInt(), maximum);
            if (area <= 0) return 0;
            ByteBuffer chunks = ByteBuffer.wrap(bytes);
            for (int pos = 8; pos <= bytes.length - 12;) {
                int length = chunks.getInt(pos);
                int type = chunks.getInt(pos + 4);
                if (length < 0 || length > bytes.length - pos - 12) return 0;
                if (type == 0x6163544C) return 0; // APNG animation, not a static chart
                if (type == 0x49454E44) return length == 0 ? area : 0;
                pos += length + 12;
            }
            return 0;
        }
        if (bytes.length < 4 || u8(bytes, 0) != 255 || u8(bytes, 1) != 216) return 0;
        for (int pos = 2; pos + 4 <= bytes.length;) {
            if (u8(bytes, pos++) != 255) return 0;
            while (pos < bytes.length && u8(bytes, pos) == 255) pos++;
            if (pos + 2 >= bytes.length) return 0;
            int marker = u8(bytes, pos++);
            if (marker == 218 || marker == 217) return 0;
            int len = u16(bytes, pos);
            if (len < 2 || len > bytes.length - pos) return 0;
            if (marker >= 192 && marker <= 207 && marker != 196 && marker != 200 && marker != 204)
                return len < 8 ? 0 : pixelCount(u16(bytes, pos + 3), u16(bytes, pos + 5), maximum);
            pos += len;
        }
        return 0;
    }

    private static int u8(byte[] bytes, int offset) { return bytes[offset] & 255; }
    private static int u16(byte[] bytes, int offset) { return u8(bytes, offset) * 256 + u8(bytes, offset + 1); }

    private static int pixelCount(int width, int height, int maximum) {
        long area = (long) width * height;
        return width > 0 && height > 0 && width <= 8192 && height <= 8192 && area <= maximum ? (int) area : 0;
    }

    private static String joined(Object value, int max) {
        if (value instanceof String) return ((String) value).length() <= max ? (String) value : null;
        if (!(value instanceof JSONArray)) return "";
        JSONArray array = (JSONArray) value;
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (!(item instanceof String)) continue;
            String s = (String) item;
            if (s.length() > max - text.length()) return null;
            text.append(s);
        }
        return text.toString();
    }

    private static String prefix(Object value, int max) {
        StringBuilder result = new StringBuilder();
        if (value instanceof String) result.append((String) value, 0, max);
        else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length() && result.length() < max; i++) {
                Object item = array.opt(i);
                if (item instanceof String) result.append((String) item, 0,
                        Math.min(((String) item).length(), max - result.length()));
            }
        }
        if (result.length() > 0 && Character.isHighSurrogate(result.charAt(result.length() - 1)))
            result.setLength(result.length() - 1);
        return result.toString();
    }

    private static void escape(StringBuilder out, String text) {
        for (int i = 0; i < text.length(); i++) {
            switch (text.charAt(i)) {
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '&': out.append("&amp;"); break;
                default: out.append(text.charAt(i));
            }
        }
    }

    private static String notice(boolean dark, String text) {
        return wrap(dark, "<header><strong>Jupyter Notebook</strong></header><p class=note>" + text + "</p>");
    }

    private static String wrap(boolean dark, String body) {
        return "<!doctype html><html lang=zh-CN><meta charset=utf-8>"
                + "<meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<meta http-equiv=Content-Security-Policy content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'\">"
                + "<style>:root{color-scheme:" + (dark ? "dark;--bg:#15131a;--fg:#f1edf8;--surface:#211e29;--line:#4c435a;--muted:#c3b9d1;--accent:#ffc2b5"
                : "light;--bg:#fffaf8;--fg:#28202c;--surface:#fff;--line:#ded0d7;--muted:#655866;--accent:#8c3a2b")
                + "}body{background:var(--bg);color:var(--fg);margin:0;font:16px/1.55 system-ui,sans-serif;overflow-wrap:anywhere}"
                + "main{max-width:860px;margin:auto;padding:14px}header{padding:3px 2px 14px}header strong{font-size:1.23em}"
                + "header p,.note{color:var(--muted);font-size:.87em;margin:.25em 0}"
                + "article{background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:12px;margin-bottom:12px}"
                + ".label{font-weight:650;font-size:.83em;color:var(--accent);margin-bottom:5px}"
                + "pre{white-space:pre-wrap;overflow-wrap:anywhere;margin:.35em 0;font:0.84em/1.55 monospace}"
                + ".md{white-space:pre-wrap}.md h2,.md h3,.md h4,.md h5,.md h6{line-height:1.25;margin:.45em 0 .15em}"
                + ".output{margin-top:10px;border-top:1px solid var(--line);padding-top:8px}summary{min-height:44px;line-height:44px;color:var(--accent)}"
                + "img{display:block;max-width:100%;height:auto;max-height:80vh;object-fit:contain;margin:.55em auto}</style>"
                + "<body><main>" + body + "</main></body></html>";
    }
}
