package com.nous.codecanvas.util;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class RenderingAdvisorTest {

    @Test
    public void testDetectExternalCdnOrHttpsImports() {
        String htmlWithCdn = "<!DOCTYPE html><html><head><script src=\"https://cdn.jsdelivr.net/npm/vue@3\"></script><link rel=\"stylesheet\" href=\"http://example.com/style.css\"></head><body></body></html>";
        List<String> warnings = RenderingAdvisor.inspect(htmlWithCdn);
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("外部") || w.contains("CDN") || w.contains("网络") || w.contains("http")));
    }

    @Test
    public void testDetectRelativeImageScriptRefs() {
        String htmlWithRelative = "<div><img src=\"images/logo.png\"/><script src=\"./app.js\"></script><link href=\"styles/main.css\" rel=\"stylesheet\"/></div>";
        List<String> warnings = RenderingAdvisor.inspect(htmlWithRelative);
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("相对路径") || w.contains("本地文件") || w.contains("资源")));
    }

    @Test
    public void testDetectVueReactJsxTypeScriptNeedingCompilation() {
        String vueTemplate = "<template>\n  <div id=\"app\">{{ message }}</div>\n</template>\n<script lang=\"ts\">\nexport default defineComponent({});\n</script>";
        List<String> warningsVue = RenderingAdvisor.inspect(vueTemplate);
        assertFalse(warningsVue.isEmpty());
        assertTrue(warningsVue.stream().anyMatch(w -> w.contains("Vue") || w.contains("编译") || w.contains("构建")));

        String reactJsx = "import React, { useState } from 'react';\nexport default function App() {\n  return <div className=\"title\">Hello</div>;\n}";
        List<String> warningsReact = RenderingAdvisor.inspect(reactJsx);
        assertFalse(warningsReact.isEmpty());
        assertTrue(warningsReact.stream().anyMatch(w -> w.contains("React") || w.contains("JSX") || w.contains("编译")));

        String tsCode = "interface User {\n  id: number;\n  name: string;\n}\nconst u: User = { id: 1, name: 'Alice' };";
        List<String> warningsTs = RenderingAdvisor.inspect(tsCode);
        assertFalse(warningsTs.isEmpty());
        assertTrue(warningsTs.stream().anyMatch(w -> w.contains("TypeScript") || w.contains("编译")));
    }

    @Test
    public void testDetectStandaloneCssJsNeedingHtml() {
        String pureCss = "body { background-color: #000; color: #fff; }\n.card { border-radius: 8px; }";
        List<String> warningsCss = RenderingAdvisor.inspect(pureCss);
        assertFalse(warningsCss.isEmpty());
        assertTrue(warningsCss.stream().anyMatch(w -> w.contains("CSS") && (w.contains("HTML") || w.contains("宿主") || w.contains("载体"))));

        String pureJs = "function calculateSum(a, b) {\n  return a + b;\n}\nconsole.log(calculateSum(1, 2));";
        List<String> warningsJs = RenderingAdvisor.inspect(pureJs);
        assertFalse(warningsJs.isEmpty());
        assertTrue(warningsJs.stream().anyMatch(w -> w.contains("JavaScript") || w.contains("JS") && (w.contains("HTML") || w.contains("宿主"))));
    }

    @Test
    public void testAvoidBroadNoisyFalseWarningsNormalInlineHtmlSvg() {
        String selfContainedHtml = "<!DOCTYPE html>\n<html>\n<head>\n  <style>\n    body { font-family: sans-serif; }\n    .btn { color: red; }\n  </style>\n</head>\n<body>\n  <h1>Hello</h1>\n  <script>\n    console.log('inline ok');\n  </script>\n</body>\n</html>";
        List<String> warningsHtml = RenderingAdvisor.inspect(selfContainedHtml);
        assertTrue("Self-contained HTML with inline styles/scripts should have no warnings, got: " + warningsHtml, warningsHtml.isEmpty());

        String pureSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"40\" fill=\"#0D9488\"/></svg>";
        List<String> warningsSvg = RenderingAdvisor.inspect(pureSvg);
        assertTrue("Normal SVG with xmlns should not trigger external CDN warnings, got: " + warningsSvg, warningsSvg.isEmpty());
    }
}
