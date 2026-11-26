package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SvgWrapperAspectTest {

    @Test
    public void replacesSliceWithMeetToPreventCropping() {
        String input = "<svg viewBox=\"0 0 1280 800\" preserveAspectRatio=\"xMidYMid slice\"><circle/></svg>";
        String wrapped = SvgWrapper.nativeSvg(input, null);
        assertTrue("must contain meet", wrapped.contains("preserveAspectRatio=\"xMidYMid meet\""));
        assertFalse("must not contain slice", wrapped.contains("slice"));
    }

    @Test
    public void addsMeetWhenPreserveAspectRatioIsMissing() {
        String input = "<svg viewBox=\"0 0 1280 800\"><circle/></svg>";
        String wrapped = SvgWrapper.nativeSvg(input, null);
        assertTrue("must supply meet", wrapped.contains("preserveAspectRatio=\"xMidYMid meet\""));
    }

    @Test
    public void widthAndHeightBecomeAnIntrinsicViewBoxWhenAbsent() {
        String raw = SvgWrapper.nativeSvg("<svg width='1280' height='800'><circle/></svg>", null);
        assertTrue(raw.contains("viewBox=\"0 0 1280.0 800.0\""));
        assertTrue(raw.contains("xmlns=\"http://www.w3.org/2000/svg\""));
    }
}
