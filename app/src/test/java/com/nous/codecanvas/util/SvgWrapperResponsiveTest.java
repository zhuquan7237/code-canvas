package com.nous.codecanvas.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The SVG wrapper's job is narrow and completely user-visible: a wide artwork must arrive on screen
 * whole. The original defect was that an imported SVG showed only its middle band, because the
 * document's own {@code preserveAspectRatio="...slice..."} tells the renderer to crop whatever
 * overflows the viewport — which on a portrait phone is most of a wide drawing.
 *
 * <p>These cases pin down the sanitising rules that fixed it, including the attributes that are
 * easy to get wrong: single vs double quotes, case, and the stylesheet form.</p>
 */
public class SvgWrapperResponsiveTest {

    private String wrap(String svg) {
        return SvgWrapper.wrapSvgInResponsiveHtml(svg, false);
    }

    @Test
    public void sliceIsRewrittenToMeet() {
        String out = wrap("<svg xmlns=\"http://www.w3.org/2000/svg\" preserveAspectRatio=\"xMidYMid slice\" viewBox=\"0 0 1280 800\"><rect width=\"10\" height=\"10\"/></svg>");
        assertTrue("slice 必须被改写为 meet（否则宽幅图会被裁切）",
                out.contains("preserveAspectRatio=\"xMidYMid meet\""));
        assertFalse("输出中不得残留 slice", out.contains("slice"));
    }

    @Test
    public void sliceInUpperCaseIsAlsoRewritten() {
        // Generators emit SLICE in caps; a case-sensitive match would let the crop through.
        String out = wrap("<svg preserveAspectRatio=\"xMidYMid SLICE\"><rect/></svg>");
        assertTrue(out.contains("preserveAspectRatio=\"xMidYMid meet\""));
        assertFalse(out.contains("SLICE"));
    }

    @Test
    public void singleQuotedSliceIsRewritten() {
        String out = wrap("<svg preserveAspectRatio='xMidYMid slice'><rect/></svg>");
        assertTrue("单引号写法同样必须处理", out.contains("preserveAspectRatio=\"xMidYMid meet\""));
    }

    @Test
    public void meetIsLeftAlone() {
        // Already correct documents must not be rewritten, so diffs stay honest.
        String out = wrap("<svg preserveAspectRatio=\"xMidYMid meet\"><rect/></svg>");
        assertTrue(out.contains("preserveAspectRatio=\"xMidYMid meet\""));
        assertFalse("meet 不应被再次追加", out.contains("meet\" meet"));
    }

    @Test
    public void noneIsLeftAloneByDesign() {
        // "none" stretches to fill; it does not crop, so the artwork is still fully visible.
        String out = wrap("<svg preserveAspectRatio=\"none\"><rect/></svg>");
        assertTrue(out.contains("preserveAspectRatio=\"none\""));
    }

    @Test
    public void missingAttributeIsAddedExplicitly() {
        String out = wrap("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>");
        assertTrue("未声明时必须显式补上 meet", out.contains("preserveAspectRatio=\"xMidYMid meet\""));
    }

    @Test
    public void inlineViewportUnitsAreStripped() {
        // width:100vw on the root forces the drawing to the viewport and crops the overflow.
        String out = wrap("<svg style=\"width: 100vw; height: 100vh\"><rect/></svg>");
        assertFalse("根元素上的 100vw 必须被移除", out.contains("100vw"));
        assertFalse("根元素上的 100vh 必须被移除", out.contains("100vh"));
    }

    @Test
    public void inlineViewportUnitsAreStrippedCaseInsensitively() {
        String out = wrap("<svg style=\"WIDTH:100VW;HEIGHT:100VH\"><rect/></svg>");
        assertFalse(out.contains("100VW"));
        assertFalse(out.contains("100VH"));
    }

    @Test
    public void containmentCssIsAlwaysPresent() {
        String out = wrap("<svg><rect/></svg>");
        assertTrue("必须锁定最大宽度，否则宽幅图会被裁切", out.contains("max-width: 100% !important"));
        assertTrue("必须锁定最大高度", out.contains("max-height: 100% !important"));
        assertTrue("必须保留宽高比，不可拉伸变形", out.contains("object-fit: contain !important"));
    }

    @Test
    public void viewportMetaIsIncluded() {
        // Without a viewport the WebView lays out at ~980px and scales, so the same drawing appears
        // at a different size from one document to the next.
        String out = wrap("<svg><rect/></svg>");
        assertTrue(out.contains("name=\"viewport\""));
        assertTrue(out.contains("width=device-width"));
    }

    @Test
    public void wrapperIsACompleteHtmlDocument() {
        String out = wrap("<svg><rect/></svg>");
        assertTrue(out.startsWith("<!DOCTYPE html>"));
        assertTrue(out.trim().endsWith("</html>"));
        assertTrue("原始 SVG 必须原样保留在文档中", out.contains("<rect/>"));
    }

    @Test
    public void darkAndLightThemesProduceDifferentBackgrounds() {
        String light = SvgWrapper.wrapSvgInResponsiveHtml("<svg/>", false);
        String dark = SvgWrapper.wrapSvgInResponsiveHtml("<svg/>", true);
        assertFalse("浅色与深色的画布底必须不同", light.equals(dark));
        assertTrue(light.contains("#F8FAFC"));
        assertTrue(dark.contains("#121417"));
    }

    @Test
    public void contentWithoutAnSvgTagIsReturnedUnchangedInBody() {
        // Nothing to sanitise: the wrapper must not mangle a document it does not understand.
        String out = wrap("这段内容里没有 svg 标签");
        assertTrue(out.contains("这段内容里没有 svg 标签"));
    }

    @Test
    public void nullContentDoesNotThrow() {
        String out = SvgWrapper.wrapSvgInResponsiveHtml(null, false);
        assertTrue("null 输入必须安全降级为空文档", out.startsWith("<!DOCTYPE html>"));
    }

    @Test
    public void otherAttributesSurviveSanitising() {
        // The rewrite replaces one attribute's value; everything else on the root must be kept.
        String out = wrap("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1280 800\" width=\"1280\" preserveAspectRatio=\"xMidYMid slice\"><rect/></svg>");
        assertTrue("viewBox 必须保留，否则图形失去坐标系", out.contains("viewBox=\"0 0 1280 800\""));
        assertTrue("width 必须保留", out.contains("width=\"1280\""));
        assertTrue("xmlns 必须保留", out.contains("xmlns=\"http://www.w3.org/2000/svg\""));
    }

    @Test
    public void firstSvgTagIsTheOneSanitised() {
        // A nested or subsequent <svg> must not be mistaken for the root.
        String out = wrap("<svg preserveAspectRatio=\"xMidYMid slice\"><svg preserveAspectRatio=\"xMidYMid slice\"/></svg>");
        int first = out.indexOf("preserveAspectRatio=\"xMidYMid meet\"");
        assertTrue("根 svg 必须被改写", first > 0);
    }
}
