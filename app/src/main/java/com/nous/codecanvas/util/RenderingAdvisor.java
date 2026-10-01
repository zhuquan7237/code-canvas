package com.nous.codecanvas.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RenderingAdvisor {

    // Relative asset references in HTML tags like src="..." or href="..."
    // matches src="foo.png", href="./bar.css", href="../baz.js", etc.
    // Exclude: http://, https://, data:, blob:, #, javascript:, about:
    private static final Pattern RELATIVE_REF_PATTERN = Pattern.compile(
            "<(?:img|script|link|source|audio|video|iframe)\\b[^>]*?\\b(?:src|href)\\s*=\\s*[\"'](?!https?:|data:|blob:|#|javascript:|about:)([^\"'>]+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    // External CDN / http / https imports in <script src="http...">, <link href="http...">, @import url("http...")
    private static final Pattern EXTERNAL_IMPORT_PATTERN = Pattern.compile(
            "(?:<(?:script|link)\\b[^>]*?\\b(?:src|href)\\s*=\\s*[\"']https?://[^\"'>]+[\"'])|(?:@import\\s+(?:url\\()?[\"']?https?://[^\"')]+[\"']?\\)?)",
            Pattern.CASE_INSENSITIVE
    );

    // Vue component detection: <template> or Vue.createApp or defineComponent
    private static final Pattern VUE_PATTERN = Pattern.compile(
            "(?:<template[\\s>])|(?:\\bVue\\.createApp\\b)|(?:\\bdefineComponent\\b)",
            Pattern.CASE_INSENSITIVE
    );

    // React JSX detection: import React, from 'react', useState, ReactDOM, or JSX syntax like <Foo /> in non-HTML context
    private static final Pattern REACT_JSX_PATTERN = Pattern.compile(
            "(?:\\bimport\\s+.*?\\bfrom\\s+['\"](?:react|react-dom)['\"])|(?:\\bexport\\s+default\\s+function\\b.*?(?:return\\s*<|=>\\s*<))|(?:\\bReactDOM\\.render\\b)|(?:\\buseState\\s*\\()|(?:\\buseEffect\\s*\\()",
            Pattern.DOTALL
    );

    // TypeScript detection: type annotations like interface Foo, type Foo =, : string, : number, as const
    private static final Pattern TS_PATTERN = Pattern.compile(
            "(?:\\binterface\\s+[A-Z][a-zA-Z0-9_]*\\s*\\{)|(?:\\btype\\s+[A-Z][a-zA-Z0-9_]*\\s*=)|(?:<script\\s+[^>]*lang=[\"']ts[\"'])|(?:\\b(?:const|let|var)\\s+[a-zA-Z0-9_]+\\s*:\\s*(?:string|number|boolean|any|void|never)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    public static List<String> inspect(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> warnings = new ArrayList<>();
        String trimmed = content.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // Check if content is SVG
        boolean isSvg = (lower.startsWith("<svg") || (lower.startsWith("<?xml") && lower.contains("<svg"))) && lower.contains("</svg>");

        // 1. Check external CDN / http / https imports
        if (EXTERNAL_IMPORT_PATTERN.matcher(trimmed).find()) {
            warnings.add("检测到引用了外部 CDN / 网络资源，在无网络环境或受限沙箱中可能无法加载或渲染受阻。");
        }

        // 2. Relative image/style/script references
        if (!isSvg) {
            Matcher relMatcher = RELATIVE_REF_PATTERN.matcher(trimmed);
            if (relMatcher.find()) {
                warnings.add("检测到使用了本地相对路径资源（如本地图片或外部脚本），WebView 独立预览时这些文件可能无法直接访问。");
            }
        }

        // 3. Vue / React JSX / TypeScript compilation check
        if (VUE_PATTERN.matcher(trimmed).find()) {
            warnings.add("检测到 Vue 单文件组件或模板代码，需要前端构建工具编译后方可在浏览器中完整运行。");
        } else if (REACT_JSX_PATTERN.matcher(trimmed).find()) {
            warnings.add("检测到 React / JSX 语法，需要经 Babel 或构建工具转译后才可在浏览器中直接执行。");
        } else if (TS_PATTERN.matcher(trimmed).find()) {
            warnings.add("检测到 TypeScript 类型标注代码，需要编译为原生 JavaScript 后才能正常在页面中执行。");
        }

        // 4. Standalone CSS / JS needing HTML host
        // Only trigger if NOT already HTML or SVG
        boolean hasHtmlTags = lower.contains("<html") || lower.contains("<body") || lower.contains("<!doctype") || (lower.contains("<div") && lower.contains("</div>"));
        if (!hasHtmlTags && !isSvg) {
            if (isPureCss(trimmed)) {
                warnings.add("当前内容为独立 CSS 样式代码，缺少 HTML 宿主骨架，建议将其嵌入 HTML 文件的 <style> 标签中进行预览。");
            } else if (isPureJs(trimmed)) {
                warnings.add("当前内容为独立 JavaScript 脚本代码，缺少 HTML 宿主容器，建议将其放入 HTML 文件的 <script> 标签或绑定 DOM 进行预览。");
            }
        }

        return warnings;
    }

    private static boolean isPureCss(String text) {
        // Simple heuristic: contains CSS selector blocks like selector { ... } and no tags
        if (text.startsWith("<")) return false;
        return text.matches("(?s)^[^{]*?\\{[^}]*\\}.*") &&
                (text.contains(":") && text.contains(";")) &&
                !text.contains("function ") && !text.contains("console.") && !text.contains("var ") && !text.contains("let ");
    }

    private static boolean isPureJs(String text) {
        if (text.startsWith("<")) return false;
        return text.contains("function ") || text.contains("console.") ||
                text.contains("var ") || text.contains("let ") || text.contains("const ") ||
                text.matches("(?s).*=>\\s*\\{.*");
    }
}
