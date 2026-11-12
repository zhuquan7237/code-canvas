package com.nous.codecanvas.util;

import java.util.List;
import java.util.Locale;

/**
 * Builds the HTML a document is previewed in, for every format in the first stage.
 *
 * <p>Design rules this file follows, each one a promise in the spec:</p>
 * <ul>
 *   <li><b>Offline.</b> Markdown, formulas and Mermaid come from bundled assets served through
 *       {@link PreviewAssets}; nothing is fetched from a CDN, so flight mode still renders.</li>
 *   <li><b>Sandboxed.</b> No JavaScript bridge, no file access, no network. An imported document
 *       cannot reach the app or the filesystem.</li>
 *   <li><b>A render failure never blocks the source.</b> Every renderer reports its own error into
 *       the page and leaves the editor and the save button working.</li>
 *   <li><b>Escape first.</b> Anything interpolated into HTML is escaped; only the document's own
 *       markup is inserted raw, and only where that is the point (HTML/SVG preview).</li>
 * </ul>
 */
public final class PreviewPage {

    private PreviewPage() {
    }

    /** Everything the page builder needs to know about one preview. */
    public static final class Request {
        public String kind = "TEXT";
        public String content = "";
        public String fileName = "";
        public boolean dark;
        public boolean allowScripts = true;
        /** Preview-only viewBox for SVG "fit to content"; null keeps the document's own. */
        public String svgViewBoxOverride;
        /** A blob content URI for images and PDFs, or null. */
        public String blobUri;
        /** True when the document's own network access has been granted for this visit. */
        public boolean allowNetwork;
    }

    public static String build(Request request) {
        String kind = request.kind == null ? "TEXT" : request.kind.toUpperCase(Locale.ROOT);
        switch (kind) {
            case "HTML": return html(request);
            case "SVG": {
                // A .html file whose body is one inline <svg> is previewed as a drawing, so the
                // wrapper must receive the drawing element only. Wrapping the whole document would
                // nest a page — title, stylesheet and all — inside the SVG stage.
                String svg = RenderKindDetector.extractInlineSvg(request.content);
                return SvgWrapper.wrapSvgInResponsiveHtml(
                        svg != null ? svg : request.content,
                        request.dark, request.svgViewBoxOverride);
            }
            case "MARKDOWN": return markdown(request);
            case "MERMAID": return mermaid(request);
            case "JSON": return json(request);
            case "JSONL": return jsonl(request);
            case "CSV": return csv(request);
            case "XML": return xml(request);
            case "YAML": return yaml(request);
            case "IMAGE":
            case "GIF": return image(request);
            case "PDF": return pdf(request);
            default: return plainText(request);
        }
    }

    // ------------------------------------------------------------------ shared chrome

    private static String palette(boolean dark) {
        return dark
                ? "--bg:#121417;--surface:#181C21;--ink:#EDF1F8;--ink-2:#A7B2C4;--line:#2A323C;"
                + "--accent:#7FA5FF;--code-bg:#0E1116;--ok:#5FD3A0;--bad:#FF8A80;--warn:#F2C078;"
                : "--bg:#F8FAFC;--surface:#FFFFFF;--ink:#0F172A;--ink-2:#53627A;--line:#DCE3EC;"
                + "--accent:#2456C6;--code-bg:#F5F7FA;--ok:#187449;--bad:#B42318;--warn:#8A4A08;";
    }

    private static String shell(boolean dark, String extraStyle, String body, String script) {
        return "<!DOCTYPE html><html data-theme=\"" + (dark ? "dark" : "light") + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>"
                + ":root{" + palette(dark) + "}"
                + "*{box-sizing:border-box}"
                + "html,body{margin:0;padding:0;background:var(--bg);color:var(--ink);"
                + "font:15px/1.65 -apple-system,'Noto Sans SC','PingFang SC',sans-serif;"
                + "-webkit-text-size-adjust:100%;}"
                + "body{padding:14px 16px 28px;overflow-wrap:break-word;}"
                + "h1,h2,h3,h4{line-height:1.3;margin:1.1em 0 .5em;font-weight:700}"
                + "h1{font-size:1.5em}h2{font-size:1.28em}h3{font-size:1.12em}"
                + "p{margin:.6em 0}ul,ol{padding-left:1.4em;margin:.6em 0}li{margin:.22em 0}"
                + "a{color:var(--accent)}"
                + "code{font-family:ui-monospace,'Cascadia Mono',Menlo,Consolas,monospace;font-size:.9em;"
                + "background:var(--code-bg);padding:.12em .35em;border-radius:4px}"
                + "pre{background:var(--code-bg);padding:12px 14px;border-radius:10px;overflow:auto;"
                + "font-family:ui-monospace,'Cascadia Mono',Menlo,Consolas,monospace;font-size:13px;line-height:1.5}"
                + "pre code{background:none;padding:0}"
                + "table{border-collapse:collapse;width:100%;margin:.8em 0;font-size:14px}"
                + "th,td{border:1px solid var(--line);padding:7px 10px;text-align:left;vertical-align:top}"
                + "th{background:var(--code-bg);font-weight:700}"
                + "blockquote{margin:.8em 0;padding:.2em 0 .2em 12px;border-left:3px solid var(--line);color:var(--ink-2)}"
                + "hr{border:none;border-top:1px solid var(--line);margin:1.2em 0}"
                + "img{max-width:100%;height:auto}"
                + ".cc-badge{display:inline-block;padding:2px 8px;border-radius:999px;background:var(--code-bg);"
                + "color:var(--ink-2);font-size:11px;font-weight:600;letter-spacing:.04em}"
                + ".cc-note{margin:0 0 12px;padding:9px 12px;border-radius:10px;background:var(--code-bg);"
                + "color:var(--ink-2);font-size:12.5px;line-height:1.55}"
                + ".cc-err{margin:10px 0;padding:10px 12px;border-radius:10px;border:1px solid var(--bad);"
                + "color:var(--bad);font-size:13px;white-space:pre-wrap}"
                + ".cc-ok{color:var(--ok)}.cc-bad{color:var(--bad)}.cc-warn{color:var(--warn)}"
                + "details{margin:.6em 0}summary{cursor:pointer;color:var(--ink-2);font-size:13px}"
                + extraStyle
                + "</style></head><body>" + body
                + (script == null ? "" : "<script>" + script + "</script>")
                + "</body></html>";
    }

    /** Escapes text for HTML. Used for every value this class interpolates. */
    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Escapes text for a JavaScript string literal inside a script block. */
    static String jsString(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '\'': out.append("\\'"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '<': out.append("\\u003c"); break;
                case '>': out.append("\\u003e"); break;
                case '&': out.append("\\u0026"); break;
                case '\u2028': out.append("\\u2028"); break;
                case '\u2029': out.append("\\u2029"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ HTML

    private static String html(Request request) {
        String content = request.content == null ? "" : request.content;
        // A page without a viewport is laid out at ~980px and then zoomed, so the same code renders
        // at wildly different sizes. Supply one when the author did not.
        return PreviewHtml.ensureViewport(content);
    }

    // ------------------------------------------------------------------ Markdown

    /** A holder that carries the document source as text, never as a JS string literal. */
    private static String sourceHolder(String content) {
        // text/plain is inert: the browser does not execute it, and </script> inside the content
        // cannot close the holder early because it is not a script element at all.
        return "<script type=\"text/plain\" id=\"cc-source\">"
                + (content == null ? "" : content.replace("</script", "<\\/script"))
                + "</script>";
    }

    private static String markdown(Request request) {
        String extra = "body{padding:16px 16px 40px}"
                + ".task-list-item{list-style:none;margin-left:-1.2em}"
                + ".task-list-item input{margin-right:.5em}"
                + ".katex-display{overflow-x:auto;overflow-y:hidden;padding:2px 0}";
        String body = sourceHolder(request.content)
                + "<div id=\"cc-md\"></div><div id=\"cc-md-err\" class=\"cc-err\" style=\"display:none\"></div>";
        return shell(request.dark, extra, body, null)
                .replace("<head>", "<head>"
                        + "<link rel=\"stylesheet\" href=\"" + PreviewAssets.url("katex.min.css") + "\">"
                        + "<script defer src=\"" + PreviewAssets.url("marked.min.js") + "\"></script>"
                        + "<script defer src=\"" + PreviewAssets.url("katex.min.js") + "\"></script>"
                        + "<script defer src=\"" + PreviewAssets.url("mermaid.min.js") + "\"></script>"
                        + "<script defer src=\"" + PreviewAssets.url("markdown.js") + "\"></script>");
    }

    // ------------------------------------------------------------------ Mermaid

    private static String mermaid(Request request) {
        String extra = "body{padding:10px;display:flex;align-items:center;justify-content:center;min-height:100%}"
                + "#cc-mermaid{width:100%;text-align:center}"
                + "#cc-mermaid svg{max-width:100%;height:auto}";
        String body = sourceHolder(request.content)
                + "<div id=\"cc-mermaid\" class=\"mermaid\"></div>"
                + "<div id=\"cc-mermaid-err\" class=\"cc-err\" style=\"display:none\"></div>";
        return shell(request.dark, extra, body, null)
                .replace("<head>", "<head>"
                        + "<script defer src=\"" + PreviewAssets.url("mermaid.min.js") + "\"></script>"
                        + "<script defer src=\"" + PreviewAssets.url("mermaid.js") + "\"></script>");
    }

    // ------------------------------------------------------------------ JSON

    private static String json(Request request) {
        StructuredText.JsonResult result = StructuredText.formatJson(request.content);
        String extra = "body{padding:12px 14px 32px}"
                + ".cc-tree{font-family:ui-monospace,'Cascadia Mono',Menlo,Consolas,monospace;font-size:13px;"
                + "line-height:1.6;white-space:pre;overflow-x:auto}"
                + ".k{color:var(--accent)}.s{color:var(--ok)}.n{color:var(--warn)}.b{color:var(--bad)}";
        StringBuilder body = new StringBuilder();
        if (result.valid) {
            body.append("<div class=\"cc-note\">JSON 结构有效 · 已格式化显示（源码里的数字与字符串保持原样）</div>");
            body.append("<div class=\"cc-tree\">").append(highlightJson(result.formatted)).append("</div>");
        } else {
            body.append("<div class=\"cc-err\">JSON 格式错误：")
                    .append(escape(result.errorLabel()))
                    .append("\n\n源码已原样保留，可切到代码继续修改。</div>");
            body.append("<div class=\"cc-tree\">").append(highlightJson(request.content)).append("</div>");
        }
        return shell(request.dark, extra, body.toString(), null);
    }

    /** Colours JSON tokens without altering them. */
    static String highlightJson(String json) {
        if (json == null) return "";
        StringBuilder out = new StringBuilder(json.length() + 64);
        int i = 0;
        int n = json.length();
        while (i < n) {
            char c = json.charAt(i);
            if (c == '"') {
                int end = i + 1;
                boolean escaped = false;
                while (end < n) {
                    char t = json.charAt(end);
                    if (escaped) {
                        escaped = false;
                    } else if (t == '\\') {
                        escaped = true;
                    } else if (t == '"') {
                        end++;
                        break;
                    }
                    end++;
                }
                // A key is a string followed by a colon.
                int probe = end;
                while (probe < n && Character.isWhitespace(json.charAt(probe))) probe++;
                boolean key = probe < n && json.charAt(probe) == ':';
                out.append("<span class=\"").append(key ? "k" : "s").append("\">")
                        .append(escape(json.substring(i, end))).append("</span>");
                i = end;
                continue;
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                int end = i;
                while (end < n && "+-.eE0123456789".indexOf(json.charAt(end)) >= 0) end++;
                out.append("<span class=\"n\">").append(escape(json.substring(i, end))).append("</span>");
                i = end;
                continue;
            }
            if (json.startsWith("true", i) || json.startsWith("false", i) || json.startsWith("null", i)) {
                int len = json.startsWith("true", i) ? 4 : json.startsWith("null", i) ? 4 : 5;
                out.append("<span class=\"b\">").append(json, i, i + len).append("</span>");
                i += len;
                continue;
            }
            out.append(escape(String.valueOf(c)));
            i++;
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ JSONL

    private static String jsonl(Request request) {
        StructuredText.JsonlResult result = StructuredText.parseJsonl(request.content);
        String extra = "body{padding:12px 14px 32px}"
                + ".cc-line{display:flex;gap:10px;padding:6px 0;border-bottom:1px solid var(--line);"
                + "font-family:ui-monospace,'Cascadia Mono',Menlo,Consolas,monospace;font-size:12.5px}"
                + ".cc-no{color:var(--ink-2);min-width:3em;text-align:right;flex:none}"
                + ".cc-body{flex:1;overflow-x:auto;white-space:pre-wrap;word-break:break-all}";
        StringBuilder body = new StringBuilder();
        String summaryClass = result.invalidCount == 0 ? "cc-ok" : "cc-bad";
        body.append("<div class=\"cc-note\"><span class=\"").append(summaryClass).append("\">")
                .append(escape(result.summary())).append("</span>")
                .append(" · 逐行解析，一行出错不影响其他行</div>");
        for (StructuredText.JsonlLine line : result.lines) {
            body.append("<div class=\"cc-line\"><span class=\"cc-no\">").append(line.line).append("</span>");
            body.append("<span class=\"cc-body\">");
            if (line.valid) {
                body.append(escape(line.preview));
            } else {
                body.append("<span class=\"cc-bad\">").append(escape(line.error)).append("</span><br>")
                        .append("<span style=\"color:var(--ink-2)\">").append(escape(line.preview)).append("</span>");
            }
            body.append("</span></div>");
        }
        if (result.lines.isEmpty()) {
            body.append("<div class=\"cc-note\">文件里没有非空行。</div>");
        }
        return shell(request.dark, extra, body.toString(), null);
    }

    // ------------------------------------------------------------------ CSV

    private static String csv(Request request) {
        char separator = CsvTable.guessSeparator(request.content);
        CsvTable.Table table = CsvTable.parse(request.content, separator);
        String extra = "body{padding:0}"
                + ".cc-wrap{overflow:auto;-webkit-overflow-scrolling:touch}"
                + "table{border-collapse:separate;border-spacing:0;font-size:13px;width:max-content;min-width:100%}"
                + "th,td{border-right:1px solid var(--line);border-bottom:1px solid var(--line);"
                + "padding:7px 11px;white-space:pre;vertical-align:top;max-width:60vw;overflow:hidden;text-overflow:ellipsis}"
                + "th{position:sticky;top:0;background:var(--surface);font-weight:700;z-index:1}"
                + "td.cc-rowno,th.cc-rowno{position:sticky;left:0;background:var(--surface);"
                + "color:var(--ink-2);text-align:right;font-variant-numeric:tabular-nums;z-index:2}"
                + "tr:nth-child(even) td{background:var(--code-bg)}"
                + ".cc-head{padding:10px 14px;font-size:12.5px;color:var(--ink-2);border-bottom:1px solid var(--line)}";
        StringBuilder body = new StringBuilder();
        body.append("<div class=\"cc-head\">").append(escape(CsvTable.describe(table, separator)));
        if (!table.errors.isEmpty()) {
            body.append(" · <span class=\"cc-warn\">").append(table.errors.size()).append(" 处提示</span>");
        }
        body.append(" · 横向滑动查看全部列</div>");
        body.append("<div class=\"cc-wrap\"><table>");
        int rows = table.rows.size();
        for (int r = 0; r < rows; r++) {
            CsvTable.Row row = table.rows.get(r);
            body.append("<tr>");
            body.append("<").append(r == 0 ? "th" : "td").append(" class=\"cc-rowno\">")
                    .append(row.line).append("</").append(r == 0 ? "th" : "td").append(">");
            for (int c = 0; c < table.columnCount; c++) {
                String value = c < row.cells.size() ? row.cells.get(c) : "";
                body.append("<").append(r == 0 ? "th" : "td").append(">")
                        .append(escape(value)).append("</").append(r == 0 ? "th" : "td").append(">");
            }
            body.append("</tr>");
        }
        body.append("</table></div>");
        if (!table.errors.isEmpty()) {
            body.append("<div class=\"cc-err\">");
            for (int i = 0; i < Math.min(6, table.errors.size()); i++) {
                body.append(escape(table.errors.get(i))).append("\n");
            }
            body.append("</div>");
        }
        return shell(request.dark, extra, body.toString(), null);
    }

    // ------------------------------------------------------------------ XML

    private static String xml(Request request) {
        XmlValidator.ValidationResult validation = XmlValidator.validateSecurely(request.content);
        String extra = "body{padding:12px 14px 32px}"
                + "pre{white-space:pre;overflow-x:auto}";
        StringBuilder body = new StringBuilder();
        if (validation.isValid()) {
            body.append("<div class=\"cc-note cc-ok\">XML 结构完整有效（已启用防 XXE 安全沙箱）</div>");
        } else {
            body.append("<div class=\"cc-err\">XML 解析异常：")
                    .append(escape(validation.getErrorMessage()))
                    .append("\n源码已保留，可切到代码继续修改。</div>");
        }
        body.append("<pre>").append(escape(XmlFormatter.formatForDisplay(request.content))).append("</pre>");
        return shell(request.dark, extra, body.toString(), null);
    }

    // ------------------------------------------------------------------ YAML

    private static String yaml(Request request) {
        List<String> problems = StructuredText.checkYaml(request.content);
        String extra = "body{padding:12px 14px 32px}"
                + "pre{white-space:pre;overflow-x:auto}"
                + ".cc-line{display:flex}";
        StringBuilder body = new StringBuilder();
        String verdict = StructuredText.yamlVerdict(problems);
        body.append("<div class=\"cc-note ").append(problems.isEmpty() ? "cc-ok" : "cc-warn").append("\">")
                .append(escape(verdict)).append("</div>");
        if (!problems.isEmpty()) {
            body.append("<div class=\"cc-err\">");
            for (String problem : problems) {
                body.append(escape(problem)).append("\n");
            }
            body.append("</div>");
        }
        body.append("<pre>").append(escape(request.content)).append("</pre>");
        return shell(request.dark, extra, body.toString(), null);
    }

    // ------------------------------------------------------------------ image / gif / pdf

    private static String image(Request request) {
        // The viewport owns zoom and pan, so the page only has to show the image whole.
        String extra = "html,body{height:100%;overflow:hidden}"
                + "body{padding:0;display:flex;align-items:center;justify-content:center;background:transparent}"
                + "img{max-width:100%;max-height:100%;object-fit:contain;display:block;"
                + "-webkit-user-drag:none;user-select:none}";
        String src = request.blobUri == null ? "" : escape(request.blobUri);
        String body = "<img id=\"cc-img\" alt=\"图片预览\" src=\"" + src + "\">"
                + "<div id=\"cc-img-err\" class=\"cc-err\" style=\"display:none\"></div>";
        String script = "(function(){var i=document.getElementById('cc-img');"
                + "i.addEventListener('error',function(){var b=document.getElementById('cc-img-err');"
                + "b.textContent='图片无法解码，文件可能已损坏。原始字节仍然保留，可以导出或分享。';"
                + "b.style.display='block';});})();";
        return shell(request.dark, extra, body, script);
    }

    private static String pdf(Request request) {
        // Android's WebView cannot render PDFs, and shipping a PDF.js bundle is out of scope for
        // this stage. The honest answer is a clear message plus the actions that do work: the
        // original bytes are intact, so the file can be opened in a PDF reader or shared.
        String extra = "html,body{height:100%}body{display:flex;align-items:center;justify-content:center;"
                + "padding:24px;text-align:center}"
                + ".cc-card{max-width:420px;background:var(--surface);border:1px solid var(--line);"
                + "border-radius:14px;padding:22px 20px}"
                + ".cc-title{font-size:16px;font-weight:700;margin:0 0 8px}"
                + ".cc-sub{font-size:13px;color:var(--ink-2);line-height:1.6;margin:0}";
        String body = "<div class=\"cc-card\">"
                + "<p class=\"cc-title\">PDF 已导入，原始文件完整保留</p>"
                + "<p class=\"cc-sub\">这一版还不能在应用内翻页预览 PDF。"
                + "用工具栏的「用其他应用打开」交给系统 PDF 阅读器，或直接分享——"
                + "导出的文件与原始字节完全一致，哈希不会变。</p>"
                + "</div>";
        return shell(request.dark, extra, body, null);
    }

    // ------------------------------------------------------------------ plain text / code

    private static String plainText(Request request) {
        String extra = "body{padding:0}"
                + "pre{margin:0;padding:14px 16px 32px;background:transparent;border-radius:0;"
                + "white-space:pre;overflow-x:auto;font-size:13px;line-height:1.55}";
        String body = "<pre>" + escape(request.content) + "</pre>";
        return shell(request.dark, extra, body, null);
    }
}
