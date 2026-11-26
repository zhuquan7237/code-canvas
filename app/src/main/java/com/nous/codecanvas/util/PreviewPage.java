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
        /** Whether the live vector renderer starts fitted to measured artwork. */
        public boolean fitSvgContent;
        /** A blob content URI for images and PDFs, or null. */
        public String blobUri;
        /** True when the document's own network access has been granted for this visit. */
        public boolean allowNetwork;
    }

    public static String build(Request request) {
        String kind = request.kind == null ? "TEXT" : request.kind.toUpperCase(Locale.ROOT);
        switch (kind) {
            case "HTML": return html(request);
            case "SVG": return SvgWrapper.wrapSvgInResponsiveHtml(request.content,
                    request.dark, null, request.allowScripts, request.fitSvgContent);
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
                + "th,td{border-bottom:1px solid var(--line);padding:10px 14px;text-align:left;vertical-align:top}"
                + "th{background:var(--code-bg);font-weight:700}"
                + "tbody tr:nth-child(even) td{background:var(--surface)}"
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
        return PreviewHtml.forWebPage(content);
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
        String extra = "body{padding:20px 18px 48px;max-width:42rem}"
                + "h1{font-size:1.65em;letter-spacing:-.01em;border-bottom:1px solid var(--line);padding-bottom:.25em}"
                + "h2{font-size:1.28em;border-bottom:1px solid var(--line);padding-bottom:.15em}"
                + "h3{font-size:1.08em}"
                + "ul,ol{padding-left:1.35em}li{margin:.28em 0}"
                + "blockquote{margin:1em 0;padding:8px 14px;background:var(--surface);"
                + "border-left:3px solid var(--accent);border-radius:0 10px 10px 0}"
                + "pre{border-radius:10px}"
                + "table{display:block;overflow-x:auto;border-radius:10px}"
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
        String extra = "body{padding:10px;display:flex;align-items:center;justify-content:center;min-height:100vh}"
                + "#cc-mermaid{width:100%;text-align:center}"
                + "#cc-mermaid svg{display:block;margin:auto;max-width:100%;max-height:calc(100vh - 20px);height:auto;width:auto}";
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
        JsonOutline.Result result = JsonOutline.inspect(request.content);
        String extra = "body{padding:12px 14px 32px}"
                + ".cc-tree{font-family:ui-monospace,'Cascadia Mono',Menlo,Consolas,monospace;"
                + "font-size:.875rem;line-height:1.5;overflow-wrap:anywhere}"
                + ".cc-node{margin:3px 0}.cc-node>summary{cursor:pointer;min-height:44px;"
                + "align-content:center;padding-inline:4px}.cc-node[open]>summary>.cc-fold{display:none}"
                + ".cc-children{padding-inline-start:min(8px,1.75vw);border-inline-start:1px solid var(--line)}"
                + ".cc-row,.cc-br{padding:2px 0}.cc-row code,.cc-raw{white-space:pre-wrap}"
                + ".cc-count,.cc-fold{color:var(--ink-2);font-size:.82em}"
                + ".cc-path{max-inline-size:100%;min-height:44px;border:1px solid var(--line);"
                + "background:var(--code-bg);color:var(--ink);border-radius:10px;padding:5px 9px;"
                + "font:inherit;font-size:.88em;text-align:start;overflow-wrap:anywhere;vertical-align:middle}"
                + ".cc-path:focus-visible,summary:focus-visible{outline:2px solid var(--accent);outline-offset:1px}"
                + ".cc-copy-msg{display:inline-block;padding:0 4px;color:var(--ink-2);font-size:.82em}"
                + ".cc-sr{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap}"
                + ".k{color:var(--accent)}"
                + ".cc-badline{white-space:pre-wrap;color:var(--bad);border-inline-start:2px solid var(--bad);padding:2px 6px}";
        StringBuilder body = new StringBuilder();
        String copy = null;
        if (result.limited) {
            body.append("<div class=\"cc-note\">文件较大或嵌套过深，预览仅显示源码开头。原文件不变，可另存查看全文。</div>")
                    .append("<pre class=\"cc-tree cc-raw\">").append(escape(jsonSourceExcerpt(request.content)))
                    .append("</pre>");
        } else if (result.errorOffset >= 0) {
            int[] position = JsonOutline.position(request.content, result.errorOffset);
            body.append("<div class=\"cc-err\">JSON 格式错误：第 ").append(position[0])
                    .append(" 行第 ").append(position[1])
                    .append(" 列 <a href=\"https://codecanvas.invalid/jump?line=")
                    .append(position[0]).append("&amp;offset=").append(result.errorOffset)
                    .append("\">在代码中定位</a></div>");
            body.append("<div class=\"cc-tree cc-raw\">")
                    .append(jsonErrorExcerpt(request.content, result.errorOffset)).append("</div>");
        } else {
            body.append("<div class=\"cc-note\">点路径复制，点分组展开或折叠。数组从 [0] 开始。</div>");
            if (result.ambiguous) body.append("<div class=\"cc-note\">重复键及其子项没有唯一路径。</div>");
            if (result.pathsOmitted) body.append("<div class=\"cc-note\">部分路径过长或无法安全表示，源码仍可查看。</div>");
            body.append("<div id=\"cc-copied\" class=\"cc-sr\" role=\"status\" aria-live=\"polite\"></div>")
                    .append("<div class=\"cc-tree\">").append(result.html).append("</div>");
            copy = copyPathScript();
        }
        return shell(request.dark, extra, body.toString(), copy);
    }

    private static String jsonErrorExcerpt(String text, int offset) {
        int start = Math.max(0, offset - 360);
        int end = Math.min(text.length(), offset + 360);
        if (start > 0 && Character.isLowSurrogate(text.charAt(start))) start--;
        if (end < text.length() && end > start && Character.isHighSurrogate(text.charAt(end - 1))) end++;
        String before = text.substring(start, offset);
        int previousLine = Math.max(before.lastIndexOf('\n'), before.lastIndexOf('\r'));
        return (start > 0 ? "…\n" : "")
                + escape(before.substring(0, previousLine + 1))
                + "<span class=\"cc-badline\">" + escape(before.substring(previousLine + 1))
                + "<span aria-label=\"错误位置\">▏</span></span>"
                + escape(text.substring(offset, end)) + (end < text.length() ? "\n…" : "");
    }

    private static String jsonSourceExcerpt(String text) {
        int end = Math.min(text.length(), 32 * 1024);
        if (end == text.length()) return text;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "\n…";
    }

    private static String copyPathScript() {
        return "(function(){var timer,feedback,status=document.getElementById('cc-copied');"
                + "document.querySelector('.cc-tree').addEventListener('click',function(ev){"
                + "var btn=ev.target.closest('.cc-path');if(!btn)return;"
                + "var box=document.createElement('textarea');box.value=btn.dataset.path;"
                + "box.readOnly=true;box.setAttribute('aria-hidden','true');"
                + "box.style.cssText='position:fixed;left:0;top:0;width:1px;height:1px;opacity:0';"
                + "document.body.appendChild(box);box.select();box.setSelectionRange(0,box.value.length);"
                + "var ok=false;try{ok=document.execCommand('copy')===true;}catch(ignore){}"
                + "box.remove();btn.focus({preventScroll:true});"
                + "clearTimeout(timer);if(feedback)feedback.remove();"
                + "feedback=document.createElement('span');feedback.className='cc-copy-msg';"
                + "feedback.setAttribute('aria-hidden','true');"
                + "feedback.textContent=ok?'已复制':'未能复制';btn.after(feedback);"
                + "status.textContent=feedback.textContent;"
                + "timer=setTimeout(function(){feedback.remove();status.textContent='';},2400);"
                + "});})();";
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
                body.append("<span class=\"cc-bad\">").append(escape(line.error)).append("</span> ")
                        .append("<a href=\"https://codecanvas.invalid/jump?line=").append(line.line)
                        .append("\">定位</a><br>")
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
        boolean tsv = request.fileName != null
                && request.fileName.toLowerCase(Locale.ROOT).endsWith(".tsv");
        char separator = CsvTable.guessSeparator(request.content, tsv ? '\t' : ',');
        CsvTable.Table table = CsvTable.parsePreview(request.content, separator);
        int rows = Math.min(120, table.rows.size());
        int columns = Math.min(32, table.columnCount);
        String extra = "body{padding:0;background:var(--bg)}"
                + ".cc-head{padding:12px 16px 8px;font-size:12px;color:var(--ink-2)}"
                + ".cc-wrap{overflow:auto;-webkit-overflow-scrolling:touch;"
                + "box-shadow:inset -18px 0 14px -14px rgba(15,23,42,.22)}"
                + "table{border-collapse:separate;border-spacing:0;font-size:14px;width:max-content;min-width:100%}"
                + "th,td{border:none;border-bottom:1px solid var(--line);padding:12px 16px;"
                + "white-space:pre-wrap;overflow-wrap:anywhere;vertical-align:top;min-width:7.5rem;max-width:min(21rem,calc(100vw - 3rem))}"
                + "th{position:sticky;top:0;background:var(--code-bg);font-weight:700;z-index:2;color:var(--ink)}"
                + "td.cc-rowno,th.cc-rowno{position:sticky;left:0;min-width:2.6rem;width:2.6rem;"
                + "padding-left:12px;padding-right:8px;text-align:right;color:var(--ink-2);"
                + "font-variant-numeric:tabular-nums;background:var(--bg);z-index:3}"
                + "th.cc-rowno{z-index:4;background:var(--code-bg)}"
                + "th button{font:inherit;font-weight:inherit;color:inherit;background:transparent;border:0;"
                + "padding:4px 0;min-height:32px;text-align:left;cursor:pointer;width:100%}"
                + "th button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}"
                + "th[aria-sort=ascending] button:after{content:' \u2191'}"
                + "th[aria-sort=descending] button:after{content:' \u2193'}"
                + "tbody tr:nth-child(odd) td{background:var(--bg)}"
                + "tbody tr:nth-child(even) td{background:var(--surface)}";
        StringBuilder body = new StringBuilder();
        if (rows == 0) {
            return shell(request.dark, extra,
                    "<div class=\"cc-head\">" + (table.inputClipped
                            ? "\u9996\u884c\u8d85\u51fa\u9884\u89c8\u8303\u56f4\uff0c\u53ef\u5728\u6e90\u6587\u67e5\u770b\u3002"
                            : "\u8868\u683c\u4e2d\u6ca1\u6709\u53ef\u9884\u89c8\u7684\u884c\u3002") + "</div>", null);
        }
        body.append("<div class=\"cc-head\"><input id=\"cc-q\" type=\"search\" aria-label=\"\u7b5b\u9009\u9884\u89c8\u884c\" placeholder=\"筛选行\" "
                + "style=\"width:100%;box-sizing:border-box;margin:0 0 8px;padding:8px 10px;"
                + "border:1px solid var(--line);min-height:44px;border-radius:10px;background:var(--surface);color:var(--ink);font:inherit\">");
        if (table.inputClipped) body.append("\u5df2\u8bfb\u53d6\u524d ");
        body.append(escape(CsvTable.describe(table, separator)));
        if (!table.errors.isEmpty()) {
            body.append(" · <span class=\"cc-warn\">").append(table.errors.size()).append(" 处提示</span>");
        }
        body.append(" · 点表头排序，横向滑动看其余列</div>");
        StringBuilder grid = new StringBuilder("<div class=\"cc-wrap\"><table><thead>");
        boolean clippedCell = false;
        int shownRows = 0;
        for (int r = 0; r < rows; r++) {
            CsvTable.Row row = table.rows.get(r);
            StringBuilder one = new StringBuilder("<tr>");
            one.append("<").append(r == 0 ? "th" : "td").append(" class=\"cc-rowno\">")
                    .append(row.line).append("</").append(r == 0 ? "th" : "td").append(">");
            for (int c = 0; c < columns; c++) {
                String value = c < row.cells.size() ? row.cells.get(c) : "";
                if (value.length() > 512) {
                    int end = Character.isHighSurrogate(value.charAt(511)) ? 511 : 512;
                    value = value.substring(0, end) + "\u2026";
                    clippedCell = true;
                }
                if (r == 0) {
                    one.append("<th scope=\"col\" aria-sort=\"none\"><button type=\"button\">")
                            .append(value.trim().isEmpty() ? "\u7b2c " + (c + 1) + " \u5217" : escape(value))
                            .append("</button></th>");
                } else {
                    one.append("<td>").append(escape(value)).append("</td>");
                }
            }
            one.append("</tr>");
            if (grid.length() + one.length() > 256 * 1024 && r > 0) break;
            grid.append(one);
            shownRows++;
            if (r == 0) grid.append("</thead><tbody>");
        }
        if (table.inputClipped || shownRows < table.rows.size() || columns < table.columnCount || clippedCell) {
            body.append("<div class=\"cc-head\">\u9884\u89c8\u9650\u524d ")
                    .append(shownRows).append(" \u884c\u3001").append(columns)
                    .append(" \u5217\u3002\u5b8c\u6574\u5185\u5bb9\u53ef\u5728\u6e90\u6587\u67e5\u770b\uff0c\u539f\u6587\u4ef6\u672a\u4fee\u6539\u3002</div>");
        }
        body.append(grid).append("</tbody></table></div>");
        if (!table.errors.isEmpty()) {
            body.append("<div class=\"cc-err\">");
            for (int i = 0; i < Math.min(6, table.errors.size()); i++) {
                body.append(escape(table.errors.get(i))).append("\n");
            }
            body.append("</div>");
        }
        String script = "(function(){var q=document.getElementById('cc-q');"
                + "var table=document.querySelector('table');if(!q||!table||!table.tBodies[0])return;"
                + "var rows=Array.from(table.tBodies[0].rows);"
                + "q.addEventListener('input',function(){var s=q.value.toLowerCase();"
                + "rows.forEach(function(r){r.hidden=!!s&&!Array.from(r.cells).slice(1)"
                + ".some(function(c){return c.textContent.toLowerCase().includes(s);});});});"
                + "var heads=Array.from(table.tHead.rows[0].cells),active=-1,dir=1;"
                + "var strictNum=/^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$/;"
                + "heads.slice(1).forEach(function(h,i){var col=i+1;"
                + "h.querySelector('button').addEventListener('click',function(){"
                + "dir=active===col?-dir:1;active=col;"
                + "heads.slice(1).forEach(function(k){k.setAttribute('aria-sort',k===h?"
                + "(dir===1?'ascending':'descending'):'none');});"
                + "var vals=rows.map(function(r,idx){return {r:r,idx:idx,v:r.cells[col].textContent.trim()};});"
                + "var numeric=vals.every(function(a){return !a.v||(strictNum.test(a.v)&&Number.isFinite(Number(a.v)));});"
                + "vals.sort(function(a,b){if(!a.v||!b.v)return (a.v?0:1)-(b.v?0:1)||a.idx-b.idx;"
                + "return dir*(numeric?Number(a.v)-Number(b.v):a.v.localeCompare(b.v))||a.idx-b.idx;});"
                + "vals.forEach(function(a){table.tBodies[0].appendChild(a.r);});});});})();";
        return shell(request.dark, extra, body.toString(), script);
    }

    // ------------------------------------------------------------------ XML

    /** Display-only. A line that holds several tags is split so the structure can fold. */
    private static String outlineXml(String raw) {
        String formatted = XmlFormatter.formatForDisplay(raw);
        if (formatted == null) return "";
        boolean crowded = false;
        for (String line : formatted.split("\n", -1)) {
            int tags = 0;
            for (int i = 0; i < line.length(); i++) if (line.charAt(i) == '<') tags++;
            if (tags > 1) { crowded = true; break; }
        }
        if (!crowded) return formatted;
        return formatted.replace(">", ">\n").replace("<", "\n<").replaceAll("\n{2,}", "\n").trim();
    }

    private static String xml(Request request) {
        XmlValidator.ValidationResult validation = XmlValidator.validateSecurely(request.content);
        int errorLine = validation.isValid() ? 0 : StructureView.xmlErrorLine(validation.getErrorMessage());
        String extra = "body{padding:12px 14px 32px}"
                + ".cc-xml{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:13px;"
                + "white-space:pre-wrap;word-break:break-word;padding:3px 0}"
                + ".cc-xml.bad{background:#FBE4E1;border-radius:6px}"
                + ".cc-kids{margin-left:14px}"
                + "summary{cursor:pointer}"
                + ".cc-ln{display:inline-block;min-width:2em;opacity:.55}";
        StringBuilder body = new StringBuilder();
        if (validation.isValid()) {
            body.append("<div class=\"cc-note cc-ok\">XML 结构完整。点箭头可折叠。</div>");
        } else {
            body.append("<div class=\"cc-err\">XML 解析异常：")
                    .append(escape(validation.getErrorMessage()));
            if (errorLine > 0) {
                body.append(" <a href=\"https://codecanvas.invalid/jump?line=")
                        .append(errorLine).append("\">定位</a>");
            }
            body.append("</div>");
        }
        body.append(StructureView.xmlHtml(outlineXml(request.content), errorLine));
        return shell(request.dark, extra, body.toString(), null);
    }

    // ------------------------------------------------------------------ YAML

    private static String yaml(Request request) {
        List<String> problems = StructuredText.checkYaml(request.content);
        String extra = "body{padding:16px 16px 32px}"
                + ".cc-yaml{margin:0 0 6px;padding:8px 12px;border-radius:12px;background:var(--surface)}"
                + ".cc-yaml.bad{background:#FBE4E1}"
                + ".cc-kids{margin-left:8px}"
                + "summary{cursor:pointer;list-style:none}"
                + ".cc-ln{display:inline-block;min-width:2.2em;opacity:.6;font-variant-numeric:tabular-nums}";
        StringBuilder body = new StringBuilder();
        String verdict = StructuredText.yamlVerdict(problems);
        body.append("<div class=\"cc-note ").append(problems.isEmpty() ? "cc-ok" : "cc-warn").append("\">")
                .append(escape(verdict)).append(" 有下级的行可以折叠。</div>");
        if (!problems.isEmpty()) {
            body.append("<div class=\"cc-err\">");
            for (String problem : problems) {
                body.append(escape(problem)).append("\n");
            }
            body.append("</div>");
        }
        body.append(StructureView.yamlHtml(StructuredText.yamlOutline(request.content)));
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
