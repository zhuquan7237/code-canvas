package com.nous.codecanvas.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The clipboard banner must be shy. This is the contract that keeps it that way: only genuine
 * renderable markup is offered, and everyday clipboard noise — chat messages, URLs, phone numbers,
 * file paths, plain prose — never raises the banner.
 */
public class ClipboardCodeOfferTest {

    private void offered(String label, String text) {
        assertTrue(label + " 应当被识别为可导入的代码", ClipboardCodeOffer.looksLikeCode(text));
    }

    private void silent(String label, String text) {
        assertFalse(label + " 不应该触发剪贴板提示", ClipboardCodeOffer.looksLikeCode(text));
    }

    @Test
    public void renderableMarkupIsOffered() {
        offered("完整 HTML", "<!DOCTYPE html><html><body><h1>hi</h1></body></html>");
        offered("自包含片段", "<div class=\"card\">内容</div>");
        offered("SVG", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>");
        offered("XML", "<?xml version=\"1.0\"?><config><name>demo</name></config>");
    }

    @Test
    public void fencedCodeIsOfferedEvenWhenInvisibleAtTheRoot() {
        offered("聊天里的代码块", "这是我生成的图表：\n```html\n<h1>前面没有尖括号的围栏内容</h1>\n```\n需要的话可以改颜色");
        offered("无语言围栏", "我生成的数据如下：\n```\n{\"name\":\"demo\",\"value\":1}\n```\n可以直接用");
    }

    @Test
    public void ordinaryClipboardNoiseStaysSilent() {
        silent("中文聊天", "这是一段普通的聊天记录，没有任何标记语言，只是几句话而已。");
        silent("英文句子", "Let us meet tomorrow at the usual place and talk it through.");
        silent("URL", "https://example.com/some/path?query=1&other=2");
        silent("手机号", "13800138000");
        silent("文件路径", "D:\\hermes_workspace\\code-canvas-design\\repo\\README.md");
        silent("短内容", "<h1>x</h1>");
        silent("空", "");
        silent("null", null);
    }

    @Test
    public void oversizedAndTinyClipsAreRefused() {
        StringBuilder huge = new StringBuilder("<html>");
        while (huge.length() < ClipboardCodeOffer.MAX_LENGTH + 10) huge.append("x");
        huge.append("</html>");
        silent("超过 2MB", huge.toString());
        silent("短于下限", "<svg/>");
    }
}
