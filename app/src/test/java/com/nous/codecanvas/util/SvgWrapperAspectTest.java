package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SvgWrapperAspectTest {

    @Test
    public void replacesSliceWithMeetToPreventCropping() {
        String input = "<svg viewBox=\"0 0 1280 800\" preserveAspectRatio=\"xMidYMid slice\"><circle/></svg>";
        String wrapped = SvgWrapper.wrapSvgInResponsiveHtml(input, false);
        assertTrue("must contain meet", wrapped.contains("preserveAspectRatio=\"xMidYMid meet\""));
        assertFalse("must not contain slice", wrapped.contains("slice"));
    }

    @Test
    public void addsMeetWhenPreserveAspectRatioIsMissing() {
        String input = "<svg viewBox=\"0 0 1280 800\"><circle/></svg>";
        String wrapped = SvgWrapper.wrapSvgInResponsiveHtml(input, false);
        assertTrue("must supply meet", wrapped.contains("preserveAspectRatio=\"xMidYMid meet\""));
    }

    @Test
    public void neutralizesVwVhStylesOnRootSvg() {
        String input = "<svg viewBox=\"0 0 1280 800\" style=\"width:100vw;height:100vh;background:#fff\"><circle/></svg>";
        String wrapped = SvgWrapper.wrapSvgInResponsiveHtml(input, false);
        assertFalse("100vw must be removed or neutralized on root", wrapped.contains("width:100vw"));
        assertFalse("100vh must be removed or neutralized on root", wrapped.contains("height:100vh"));
    }

    @Test
    public void wrapperCssConstrainsBothWidthAndHeight() {
        String input = "<svg viewBox=\"0 0 1280 800\"><circle/></svg>";
        String wrapped = SvgWrapper.wrapSvgInResponsiveHtml(input, false);
        assertTrue("must constrain max-width", wrapped.contains("max-width: 100%"));
        assertTrue("must constrain max-height", wrapped.contains("max-height: 100%"));
        assertTrue("must contain object-fit contain or equivalent", wrapped.contains("object-fit: contain"));
    }
}
