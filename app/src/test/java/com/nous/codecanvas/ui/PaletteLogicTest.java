package com.nous.codecanvas.ui;

import org.junit.Test;

import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Proves the palette reader itself is faithful to the XML resources, so the contrast
 * assertions in {@link ColorContrastTest} cannot drift into testing an invented set.
 */
public class PaletteLogicTest {

    @Test
    public void readsEveryDeclaredTokenForBothThemes() throws Exception {
        for (String theme : new String[]{Palette.DAY, Palette.NIGHT}) {
            Palette palette = Palette.load(theme);
            for (String token : new String[]{
                    "bg", "surface", "surface_subtle", "border", "border_strong",
                    "ink_primary", "ink_secondary", "icon_on_surface", "icon_on_subtle",
                    "primary", "on_primary", "success", "on_success", "success_surface",
                    "preview", "on_preview", "preview_surface", "warning", "warning_surface",
                    "danger", "on_danger", "disabled_fill", "disabled_ink", "segment_track",
                    "on_segment_selected", "code_surface", "syntax_tag", "syntax_attr",
                    "syntax_string", "syntax_comment"}) {
                assertTrue(theme + " must expose " + token, palette.has(token));
            }
        }
    }

    @Test
    public void paletteValuesMatchLiteralResourceColors() throws Exception {
        // Independent re-read of the literal <color> entries proves the parsed table
        // equals the values the platform would load.
        verifyAgainstLiteral(Palette.DAY, "app/src/main/res/values/colors.xml");
        verifyAgainstLiteral(Palette.NIGHT, "app/src/main/res/values-night/colors.xml");
    }

    private void verifyAgainstLiteral(String theme, String path) throws Exception {
        String xml = Palette.read(path);
        Palette palette = Palette.load(theme);
        Matcher matcher = Palette.solidColorPattern().matcher(xml);
        int checked = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!palette.has(name)) {
                continue;
            }
            int literal = (int) Long.parseLong(matcher.group(2), 16); if(matcher.group(2).length()==6) literal |= 0xFF000000;
            assertEquals(theme + " " + name, literal, palette.color(name));
            checked++;
        }
        assertTrue("Expected to cross-check the full literal palette in " + path + " but only matched " + checked, checked >= 30);
    }

    @Test
    public void knownReferenceRatiosAreStable() {
        // Sanity anchor for the WCAG helper itself: black on white is 21:1, and a
        // mid-grey pairing is well under the text threshold.
        assertEquals(21.0, PaletteLogic.contrast(PaletteLogic.parseHex("#000000"),
                PaletteLogic.parseHex("#FFFFFF")), 0.01);
        assertEquals(1.0, PaletteLogic.contrast(PaletteLogic.parseHex("#777777"),
                PaletteLogic.parseHex("#777777")), 0.001);
        assertTrue(PaletteLogic.meetsTextAa(PaletteLogic.parseHex("#000000"),
                PaletteLogic.parseHex("#FFFFFF")));
        assertTrue(PaletteLogic.meetsFunctionalAa(PaletteLogic.parseHex("#000000"),
                PaletteLogic.parseHex("#FFFFFF")));
    }
}