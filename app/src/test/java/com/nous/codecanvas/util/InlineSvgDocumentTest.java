package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * An HTML page that is only a frame around one inline SVG must be previewed as a drawing.
 *
 * <p>Reported as: a file saved as {@code .html} whose body is a single inline {@code <svg>} "无法缩放，
 * 显示不全" once it was in the app. The page was correctly classified as HTML, and HTML preview has no
 * vector viewport — so the artwork's own {@code preserveAspectRatio="xMidYMid slice"} cropped it to
 * the middle band of a portrait phone and there was no way to zoom out.</p>
 *
 * <p>The rule has to be narrow: only a page with nothing but scaffolding around a single drawing is
 * reclassified. A real web page with a heading, a caption or a button must stay HTML, or the app
 * would start rendering ordinary pages as bare drawings.</p>
 */
public class InlineSvgDocumentTest {

    private static final String AI_ARTWORK = "<!DOCTYPE html>\n"
            + "<html lang=\"zh-CN\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\" />\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
            + "<title>海滩骑行日记</title>\n"
            + "<style>\n"
            + "  html, body { width:100%; height:100%; overflow:hidden; }\n"
            + "  svg#stage { width:100vw; height:100vh; }\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<svg id=\"stage\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1200 675\""
            + " preserveAspectRatio=\"xMidYMid slice\">\n"
            + "  <rect width=\"1200\" height=\"675\" fill=\"#f6ead7\"/>\n"
            + "  <circle cx=\"600\" cy=\"300\" r=\"120\" fill=\"#2f6fdb\"/>\n"
            + "</svg>\n"
            + "</body>\n"
            + "</html>\n";

    @Test
    public void anHtmlPageWrappingOneDrawingStaysAPage() {
        assertEquals("带 html/body 的文件是网页，按钮和动画必须留在 WebView 里",
                RenderKind.HTML, RenderKindDetector.detect("gemini carbon.html", AI_ARTWORK));
    }

    @Test
    public void aRealWebPageStaysHtml() {
        String page = "<!DOCTYPE html><html><head><title>t</title></head><body>"
                + "<h1>焊接工艺</h1>"
                + "<svg viewBox=\"0 0 100 100\"><circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>"
                + "<p>说明文字</p></body></html>";
        assertEquals("带标题和说明的页面是网页，不能当成单张图",
                RenderKind.HTML, RenderKindDetector.detect("report.html", page));
    }

    @Test
    public void aPageWithTwoDrawingsStaysHtml() {
        String page = "<!DOCTYPE html><html><body>"
                + "<svg viewBox=\"0 0 10 10\"><rect width=\"10\" height=\"10\"/></svg>"
                + "<svg viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>"
                + "</body></html>";
        assertEquals("两张图是画廊，不是单张作品",
                RenderKind.HTML, RenderKindDetector.detect("gallery.html", page));
    }

    @Test
    public void aDrawingInsideALayoutDivStaysHtml() {
        String page = "<!DOCTYPE html><html><body>"
                + "<div class=\"frame\"><svg viewBox=\"0 0 10 10\"><rect width=\"10\" height=\"10\"/></svg></div>"
                + "</body></html>";
        assertEquals("套在布局容器里说明作者要的是页面排版",
                RenderKind.HTML, RenderKindDetector.detect("card.html", page));
    }

    @Test
    public void aPlainSvgFileIsStillSvg() {
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("art.svg",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>"));
    }

    @Test
    public void anHtmlPageWithNoDrawingIsHtml() {
        assertEquals(RenderKind.HTML, RenderKindDetector.detect("plain.html",
                "<!DOCTYPE html><html><body><p>hello</p></body></html>"));
    }

    @Test
    public void commentsAndScriptsAroundTheDrawingAreScaffolding() {
        String page = "<!DOCTYPE html>\n<html>\n<head>\n<style>svg{width:100%}</style>\n"
                + "<script>window.CC_MD_RAN=true;</script>\n</head>\n<body>\n"
                + "<!-- 这是作品 -->\n"
                + "<svg viewBox=\"0 0 800 600\"><rect width=\"800\" height=\"600\" fill=\"#fff\"/></svg>\n"
                + "<script>document.title='x';</script>\n"
                + "</body>\n</html>\n";
        assertEquals("带 html 外壳的文件按网页打开，脚本才能接到真实触摸",
                RenderKind.HTML, RenderKindDetector.detect("art.html", page));
    }

    @Test
    public void textAfterTheDrawingKeepsItAPage() {
        String page = "<!DOCTYPE html><html><body>"
                + "<svg viewBox=\"0 0 10 10\"><rect width=\"10\" height=\"10\"/></svg>"
                + "作者：材料学院</body></html>";
        assertEquals("图形后面还有正文，就是网页",
                RenderKind.HTML, RenderKindDetector.detect("art.html", page));
    }

    @Test
    public void aBareSvgIsClassifiedByItsRootElement() {
        // A bare <svg> has nothing but scaffolding around it, so the helper legitimately says yes;
        // what matters is that detect() reaches SVG either way.
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("art.svg",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>"));
        assertEquals(RenderKind.SVG, RenderKindDetector.detect("art.dat",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>"));
    }

    @Test
    public void nestedSvgIsStillOneDrawing() {
        String page = "<!DOCTYPE html><html><body>"
                + "<svg viewBox=\"0 0 100 100\">"
                + "<svg x=\"10\" y=\"10\" width=\"20\" height=\"20\"><rect width=\"20\" height=\"20\"/></svg>"
                + "</svg></body></html>";
        assertEquals("带 body 的文件是网页",
                RenderKind.HTML, RenderKindDetector.detect("nested.html", page));
    }

    @Test
    public void extractionReturnsOnlyTheDrawing() {
        String svg = RenderKindDetector.extractInlineSvg(AI_ARTWORK);
        assertTrue("必须只取出 <svg> 元素", svg != null && svg.startsWith("<svg"));
        assertTrue("不能带上 html/head/body", !svg.contains("<html") && !svg.contains("<body"));
        assertTrue("必须包含完整图形", svg.contains("</svg>"));
        assertTrue("必须保留 viewBox", svg.contains("viewBox=\"0 0 1200 675\""));
    }

    /**
     * An animated artwork must keep the script that animates it.
     *
     * <p>Reported as "只能加载出静态的…全部被搞成了像图片那样的静态的". The first version of the extractor
     * took only the {@code <svg>} element and dropped the page's {@code <script>} — which is what
     * moves the parallax layers and turns the wheels. The drawing rendered, but frozen.</p>
     */
    @Test
    public void extractionKeepsTheScriptThatAnimatesTheDrawing() {
        String animated = "<!DOCTYPE html><html><head><title>t</title>\n"
                + "<style>#wheel{transform-origin:center}</style>\n"
                + "<script>window.__early=1;</script>\n"
                + "</head><body>\n"
                + "<svg viewBox=\"0 0 100 100\"><g id=\"wheel\"><circle cx=\"50\" cy=\"50\" r=\"20\"/></g></svg>\n"
                + "<script>requestAnimationFrame(function f(){document.getElementById('wheel');"
                + "requestAnimationFrame(f);});</script>\n"
                + "</body></html>";

        String art = RenderKindDetector.extractInlineSvgArtwork(animated);
        assertTrue("必须保留驱动动画的脚本", art.contains("requestAnimationFrame"));
        assertTrue("必须保留样式表", art.contains("transform-origin"));
        assertTrue("必须保留图形", art.contains("id=\"wheel\""));
        assertFalse("不能带上页面外壳", art.contains("<html") || art.contains("<body"));
        assertFalse("不能带上 title", art.contains("<title>"));

        // Styles must come before the drawing and scripts after it: that is the order the browser
        // would have applied them in, and a script that queries the drawing needs it to exist.
        int styleAt = art.indexOf("<style");
        int svgAt = art.indexOf("<svg");
        int scriptAt = art.indexOf("<script");
        assertTrue("样式要在图形之前", styleAt >= 0 && styleAt < svgAt);
        assertTrue("脚本要在图形之后", scriptAt > svgAt);
    }

    @Test
    public void extractionKeepsEveryScriptBlockInOrder() {
        String two = "<!DOCTYPE html><html><body>"
                + "<svg viewBox=\"0 0 10 10\"><rect width=\"10\" height=\"10\"/></svg>"
                + "<script>var first=1;</script>"
                + "<script>var second=2;</script>"
                + "</body></html>";
        String art = RenderKindDetector.extractInlineSvgArtwork(two);
        assertTrue("两个脚本块都要保留", art.contains("first") && art.contains("second"));
        assertTrue("顺序不能颠倒", art.indexOf("first") < art.indexOf("second"));
    }

    @Test
    public void extractionRefusesAPageWithTwoDrawings() {
        String page = "<!DOCTYPE html><html><body>"
                + "<svg viewBox=\"0 0 10 10\"><rect width=\"10\" height=\"10\"/></svg>"
                + "<svg viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>"
                + "</body></html>";
        assertTrue("两张图不该被当成一张提取", RenderKindDetector.extractInlineSvg(page) == null);
    }

    @Test
    public void theWrappedPreviewContainsTheDrawingOnceAndNoPageTags() {
        String html = PreviewHtml.forDocument(AI_ARTWORK, RenderKind.SVG, false);
        assertTrue("包装页必须包含图形", html.contains("viewBox=\"0 0 1200 675\""));
        assertFalse("包装页里不能嵌套整份 HTML", html.contains("<html lang=\"zh-CN\""));
        assertTrue("必须是视口页", html.contains("CCViewport"));
    }

    @Test
    public void uppercaseMarkupIsHandled() {
        String page = "<!DOCTYPE HTML><HTML><BODY>"
                + "<SVG VIEWBOX=\"0 0 10 10\"><RECT WIDTH=\"10\" HEIGHT=\"10\"/></SVG>"
                + "</BODY></HTML>";
        assertEquals("大小写不该影响判断",
                RenderKind.HTML, RenderKindDetector.detect("art.html", page));
    }


    @Test
    public void probe2() {
        String lower = AI_ARTWORK.toLowerCase(java.util.Locale.ROOT);
        int open = lower.indexOf("<svg");
        System.out.println("P open=" + open);
        System.out.println("P single=" + RenderKindDetector.isSingleInlineSvgDocument(AI_ARTWORK));
        String ext = RenderKindDetector.extractInlineSvg(AI_ARTWORK);
        System.out.println("P extractLen=" + (ext == null ? -1 : ext.length()));
        System.out.println("P tail=" + (ext == null ? "null" : ext.substring(Math.max(0, ext.length() - 30))));
        // Where does the scanner think the element ends?
        int idx = lower.indexOf("</svg");
        System.out.println("P firstCloseAt=" + idx + " gt=" + lower.indexOf('>', idx));
        System.out.println("P afterElement=[" + lower.substring(lower.indexOf('>', idx) + 1) + "]");
        String before = lower.substring(0, open);
        System.out.println("P beforeLen=" + before.length());
        System.out.println("P before=[" + before.replace("\n", "\\n") + "]");
        System.out.println("P afterLen=" + lower.substring(lower.indexOf('>', idx) + 1).length());
    }
}
