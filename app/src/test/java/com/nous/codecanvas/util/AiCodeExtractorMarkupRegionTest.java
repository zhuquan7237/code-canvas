package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The quick-paste path trusts fenced code. Without a fence the only honest move is to offer the
 * guess and let the user reject it, so the guess has to be predictable: the region from the first
 * line that starts with "&lt;" to the last line that ends in it, and nothing at all when the text
 * already is markup.
 */
public class AiCodeExtractorMarkupRegionTest {

    @Test
    public void plainMarkupIsNotAGuess() {
        assertNull(AiCodeExtractor.markupRegion("<html><body>hi</body></html>"));
        assertNull(AiCodeExtractor.markupRegion("  \n<svg viewBox=\"0 0 10 10\"><rect/></svg>"));
    }

    @Test
    public void proseAroundMarkupYieldsJustTheMarkup() {
        String reply = "这是你要的图表，直接复制到画布就能看：\n"
                + "<svg viewBox=\"0 0 100 100\">\n"
                + "  <circle cx=\"50\" cy=\"50\" r=\"40\" />\n"
                + "</svg>\n"
                + "颜色想改的话告诉我。";
        String region = AiCodeExtractor.markupRegion(reply);
        assertEquals("<svg viewBox=\"0 0 100 100\">\n  <circle cx=\"50\" cy=\"50\" r=\"40\" />\n</svg>", region);
    }

    @Test
    public void proseWithoutMarkupIsNotGuessed() {
        assertNull(AiCodeExtractor.markupRegion("今天天气不错，代码我明天再写。"));
        assertNull(AiCodeExtractor.markupRegion("用 a < b 判断，然后继续。"));
    }

    @Test
    public void singleMarkupLineIsTooThinToOffer() {
        assertNull(AiCodeExtractor.markupRegion("看这里 <br> 就好了"));
    }
}
