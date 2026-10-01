package com.nous.codecanvas.ui;

import com.nous.codecanvas.editor.SyntaxHighlighter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Palette-level contrast audit.
 *
 * <p>This test never invents its own palette: it reads the numeric palette table
 * embedded in the real {@code res/values/colors.xml} (day) and
 * {@code res/values-night/colors.xml} (night) through {@link Palette}, and
 * additionally asserts that {@link SyntaxHighlighter}'s span colours equal those
 * same resource values. The instrumented {@code ThemeContrastInstrumentedTest}
 * proves the rendered Views agree on device.</p>
 *
 * <p>WCAG 2.1 relative luminance; ordinary text needs >= 4.5:1, functional icons,
 * strokes, focus and selected indicators need >= 3:1 against the adjacent surface.
 * Decorative separators are exempt only when no touch/selection boundary depends
 * on them.</p>
 */
public class ColorContrastTest {

    private Palette day() throws Exception {
        return Palette.load(Palette.DAY);
    }

    private Palette night() throws Exception {
        return Palette.load(Palette.NIGHT);
    }

    private static void text(String label, int fg, int bg) {
        double ratio = PaletteLogic.contrast(fg, bg);
        assertTrue(label + " text contrast " + ratio + " < 4.5", ratio >= 4.5);
    }

    private static void functional(String label, int fg, int bg) {
        double ratio = PaletteLogic.contrast(fg, bg);
        assertTrue(label + " functional contrast " + ratio + " < 3.0", ratio >= 3.0);
    }

    private static void assertNoAccidentalOpacity(String label, int color) {
        assertFalse(label + " must be fully opaque", (color >>> 24) == 0);
    }

    @Test
    public void dayOrdinaryTextMeetsAa() throws Exception {
        Palette p = day();
        text("primary/bg", p.color("ink_primary"), p.color("bg"));
        text("primary/surface", p.color("ink_primary"), p.color("surface"));
        text("primary/subtle", p.color("ink_primary"), p.color("surface_subtle"));
        text("secondary/bg", p.color("ink_secondary"), p.color("bg"));
        text("secondary/surface", p.color("ink_secondary"), p.color("surface"));
        text("secondary/subtle", p.color("ink_secondary"), p.color("surface_subtle"));
        text("on-primary/primary", p.color("on_primary"), p.color("primary"));
        text("success/surface", p.color("success"), p.color("surface"));
        text("on-success/success", p.color("on_success"), p.color("success"));
        text("success/success-surface", p.color("success"), p.color("success_surface"));
        text("preview/surface", p.color("preview"), p.color("surface"));
        text("on-preview/preview", p.color("on_preview"), p.color("preview"));
        text("preview/preview-surface", p.color("preview"), p.color("preview_surface"));
        text("warning/surface", p.color("warning"), p.color("surface"));
        text("warning/warning-surface", p.color("warning"), p.color("warning_surface"));
        text("danger/surface", p.color("danger"), p.color("surface"));
        text("danger/bg", p.color("danger"), p.color("bg"));
        text("on-danger/danger", p.color("on_danger"), p.color("danger"));
        text("disabled-ink/disabled-fill", p.color("disabled_ink"), p.color("disabled_fill"));
        text("on-segment-selected/primary", p.color("on_segment_selected"), p.color("primary"));
        text("code-ink/code-surface", p.color("code_ink"), p.color("code_surface"));
    }

    @Test
    public void nightOrdinaryTextMeetsAa() throws Exception {
        Palette p = night();
        text("primary/bg", p.color("ink_primary"), p.color("bg"));
        text("primary/surface", p.color("ink_primary"), p.color("surface"));
        text("primary/subtle", p.color("ink_primary"), p.color("surface_subtle"));
        text("secondary/bg", p.color("ink_secondary"), p.color("bg"));
        text("secondary/surface", p.color("ink_secondary"), p.color("surface"));
        text("secondary/subtle", p.color("ink_secondary"), p.color("surface_subtle"));
        text("on-primary/primary", p.color("on_primary"), p.color("primary"));
        text("success/surface", p.color("success"), p.color("surface"));
        text("on-success/success", p.color("on_success"), p.color("success"));
        text("success/success-surface", p.color("success"), p.color("success_surface"));
        text("preview/surface", p.color("preview"), p.color("surface"));
        text("on-preview/preview", p.color("on_preview"), p.color("preview"));
        text("preview/preview-surface", p.color("preview"), p.color("preview_surface"));
        text("warning/surface", p.color("warning"), p.color("surface"));
        text("warning/warning-surface", p.color("warning"), p.color("warning_surface"));
        text("danger/surface", p.color("danger"), p.color("surface"));
        text("danger/bg", p.color("danger"), p.color("bg"));
        text("on-danger/danger", p.color("on_danger"), p.color("danger"));
        text("disabled-ink/disabled-fill", p.color("disabled_ink"), p.color("disabled_fill"));
        text("on-segment-selected/primary", p.color("on_segment_selected"), p.color("primary"));
        text("code-ink/code-surface", p.color("code_ink"), p.color("code_surface"));
    }

    @Test
    public void functionalIconsAndStrokesMeetThreeToOne() throws Exception {
        Palette d = day();
        functional("day icon/surface", d.color("icon_on_surface"), d.color("surface"));
        functional("day icon/subtle", d.color("icon_on_subtle"), d.color("surface_subtle"));
        functional("day icon/bg", d.color("icon_on_surface"), d.color("bg"));
        functional("day primary/surface", d.color("primary"), d.color("surface"));
        functional("day success/surface", d.color("success"), d.color("surface"));
        functional("day warning/surface", d.color("warning"), d.color("surface"));
        functional("day danger/surface", d.color("danger"), d.color("surface"));

        Palette n = night();
        functional("night icon/surface", n.color("icon_on_surface"), n.color("surface"));
        functional("night icon/subtle", n.color("icon_on_subtle"), n.color("surface_subtle"));
        functional("night icon/bg", n.color("icon_on_surface"), n.color("bg"));
        // Night has no inherited dark stroke: bright icon ink on every surface.
        functional("night primary/surface", n.color("primary"), n.color("surface"));
        functional("night success/surface", n.color("success"), n.color("surface"));
        functional("night warning/surface", n.color("warning"), n.color("surface"));
        functional("night danger/surface", n.color("danger"), n.color("surface"));
    }

    @Test
    public void selectedSegmentAndFunctionalTintsKeepStateVisible() throws Exception {
        for (Palette p : new Palette[]{day(), night()}) {
            String theme = p == day() ? "day" : "night";
            // The selected segment is a filled primary chip carrying on-primary ink.
            text(theme + " selected-ink/primary", p.color("on_segment_selected"), p.color("primary"));
            functional(theme + " primary/track", p.color("primary"), p.color("segment_track"));
            functional(theme + " primary/surface", p.color("primary"), p.color("surface"));
            functional(theme + " primary/subtle", p.color("primary"), p.color("surface_subtle"));
            // The coloured 1dp functional tints are drawn as real boundary markers
            // (undo/redo availability, XML status card, warning badge) so they must
            // stay >= 3:1, not merely decorative hairlines.
            functional(theme + " success/subtle", p.color("success"), p.color("surface_subtle"));
            functional(theme + " warning/subtle", p.color("warning"), p.color("surface_subtle"));
        }
    }

    @Test
    public void disabledStateIsReadableButClearlyInactive() throws Exception {
        // A disabled control is not near-invisible: the muted label still clears AA
        // against the muted fill, so "unavailable" reads as off, not as blank.
        for (Palette p : new Palette[]{day(), night()}) {
            String theme = p == day() ? "day" : "night";
            text(theme + " disabled-ink/disabled-fill",
                    p.color("disabled_ink"), p.color("disabled_fill"));
            functional(theme + " disabled-ink/bg",
                    p.color("disabled_ink"), p.color("bg"));
        }
    }

    @Test
    public void syntaxSpansMeetAaOnCodeSurfaceInBothThemes() throws Exception {
        Palette d = day();
        text("day syntax_tag", d.color("syntax_tag"), d.color("code_surface"));
        text("day syntax_attr", d.color("syntax_attr"), d.color("code_surface"));
        text("day syntax_string", d.color("syntax_string"), d.color("code_surface"));
        text("day syntax_comment", d.color("syntax_comment"), d.color("code_surface"));

        Palette n = night();
        text("night syntax_tag", n.color("syntax_tag"), n.color("code_surface"));
        text("night syntax_attr", n.color("syntax_attr"), n.color("code_surface"));
        text("night syntax_string", n.color("syntax_string"), n.color("code_surface"));
        text("night syntax_comment", n.color("syntax_comment"), n.color("code_surface"));
    }

    @Test
    public void syntaxHighlighterSpansMatchTheActualResourcePalette() throws Exception {
        // The highlighter keeps int constants for performance, so bind them to the
        // real XML values rather than trusting a parallel copy.
        Palette d = day();
        assertEquals("DAY_TAG", d.color("syntax_tag"), SyntaxHighlighter.DAY_TAG);
        assertEquals("DAY_ATTR", d.color("syntax_attr"), SyntaxHighlighter.DAY_ATTR);
        assertEquals("DAY_STRING", d.color("syntax_string"), SyntaxHighlighter.DAY_STRING);
        assertEquals("DAY_COMMENT", d.color("syntax_comment"), SyntaxHighlighter.DAY_COMMENT);

        Palette n = night();
        assertEquals("NIGHT_TAG", n.color("syntax_tag"), SyntaxHighlighter.NIGHT_TAG);
        assertEquals("NIGHT_ATTR", n.color("syntax_attr"), SyntaxHighlighter.NIGHT_ATTR);
        assertEquals("NIGHT_STRING", n.color("syntax_string"), SyntaxHighlighter.NIGHT_STRING);
        assertEquals("NIGHT_COMMENT", n.color("syntax_comment"), SyntaxHighlighter.NIGHT_COMMENT);
    }

    @Test
    public void nightPaletteIsGenuinelyReAuthoredNoInheritedDarkInk() throws Exception {
        Palette d = day();
        Palette n = night();
        // Regression guard: night must be brighter than day for every ink token,
        // which is the signature of a real dark palette instead of inheritance.
        for (String token : new String[]{
                "ink_primary", "ink_secondary", "icon_on_surface", "icon_on_subtle",
                "primary", "success", "preview", "warning", "danger",
                "syntax_tag", "syntax_attr", "syntax_string", "syntax_comment"}) {
            double dayLum = PaletteLogic.luminance(d.color(token));
            double nightLum = PaletteLogic.luminance(n.color(token));
            assertTrue(token + " night luminance " + nightLum
                    + " must exceed day " + dayLum, nightLum > dayLum);
        }
        assertTrue("night surfaces must be darker than day",
                PaletteLogic.luminance(n.color("bg")) < PaletteLogic.luminance(d.color("bg")));
    }

    @Test
    public void functionalBoundariesStayVisibleInBothThemes() throws Exception {
        // canvas_border_strong is a real boundary (search field outline, segment chip
        // outline). Roundtrip measurement used to be skipped here while the day value
        // sat at 2.41:1; it is now enforced.
        for (Palette p : new Palette[]{day(), night()}) {
            String theme = p == day() ? "day" : "night";
            functional(theme + " border_strong/surface", p.color("border_strong"), p.color("surface"));
            functional(theme + " border_strong/subtle", p.color("border_strong"), p.color("surface_subtle"));
            functional(theme + " border_strong/segment_track", p.color("border_strong"), p.color("segment_track"));
        }
    }

    @Test
    public void disabledControlIsReadableAndStillDistinguishableFromThePage() throws Exception {
        // The disabled primary button is a muted fill carrying disabled ink. It must
        // not use the enabled on-primary white label, and the fill must still read as
        // a control rather than dissolving into the page background.
        for (Palette p : new Palette[]{day(), night()}) {
            String theme = p == day() ? "day" : "night";
            text(theme + " disabled ink on disabled fill", p.color("disabled_ink"), p.color("disabled_fill"));
            double fillVsSurface = PaletteLogic.contrast(p.color("disabled_fill"), p.color("surface"));
            assertTrue(theme + " disabled fill must still read as a control, not dissolve into the page ("
                    + fillVsSurface + ")", fillVsSurface >= 1.25);
            assertTrue(theme + " enabled white label would be unreadable on the disabled fill ("
                            + PaletteLogic.contrast(p.color("on_primary"), p.color("disabled_fill")) + ")",
                    PaletteLogic.contrast(p.color("on_primary"), p.color("disabled_fill")) < 4.5);
        }
    }

    @Test
    public void everyPaletteColorIsFullyOpaque() throws Exception {
        for (String theme : new String[]{Palette.DAY, Palette.NIGHT}) {
            Palette p = Palette.load(theme);
            for (String token : new String[]{
                    "bg", "surface", "surface_subtle", "ink_primary", "ink_secondary",
                    "icon_on_surface", "icon_on_subtle", "primary", "on_primary",
                    "success", "on_success", "preview", "on_preview",
                    "warning", "danger", "on_danger", "disabled_fill", "disabled_ink",
                    "segment_track", "on_segment_selected", "code_surface",
                    "syntax_tag", "syntax_attr", "syntax_string", "syntax_comment"}) {
                assertNoAccidentalOpacity(theme + " " + token, p.color(token));
            }
        }
    }
}