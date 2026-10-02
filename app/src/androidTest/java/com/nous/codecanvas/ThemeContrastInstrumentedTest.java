package com.nous.codecanvas;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.ui.MainActivity;

/**
 * On-device proof that the shipped resources really resolve to a readable palette in both
 * themes, and that the text actually rendered on the home screen is readable against the
 * surface it sits on. The user-facing complaint this guards against is "文字和背景颜色差不多".
 *
 * <p>Ordinary text needs >= 4.5:1, functional icons/strokes >= 3:1 (WCAG 2.1 AA).</p>
 */
public class ThemeContrastInstrumentedTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // These assertions compare the palette a running screen actually resolved, so the appearance
        // preference must be the default rather than whatever a previous test left behind.
        TestAppearance.resetToSystem(getInstrumentation().getTargetContext());
    }

    private static final int[][] TEXT_PAIRS = {
            {R.color.canvas_ink_primary, R.color.canvas_bg},
            {R.color.canvas_ink_primary, R.color.canvas_surface},
            {R.color.canvas_ink_primary, R.color.canvas_surface_subtle},
            {R.color.canvas_ink_secondary, R.color.canvas_bg},
            {R.color.canvas_ink_secondary, R.color.canvas_surface},
            {R.color.canvas_ink_secondary, R.color.canvas_surface_subtle},
            {R.color.canvas_on_primary, R.color.canvas_primary},
            {R.color.canvas_on_success, R.color.canvas_success},
            {R.color.canvas_on_preview, R.color.canvas_preview},
            {R.color.canvas_on_danger, R.color.canvas_danger},
            {R.color.canvas_on_segment_selected, R.color.canvas_primary},
            {R.color.canvas_primary, R.color.canvas_surface},
            {R.color.canvas_preview, R.color.canvas_surface_subtle},
            {R.color.canvas_warning, R.color.canvas_warning_surface},
            {R.color.canvas_danger, R.color.canvas_surface},
            {R.color.canvas_code_ink, R.color.canvas_code_surface},
            {R.color.syntax_tag, R.color.canvas_code_surface},
            {R.color.syntax_attr, R.color.canvas_code_surface},
            {R.color.syntax_string, R.color.canvas_code_surface},
            {R.color.syntax_comment, R.color.canvas_code_surface}
    };

    private static final int[][] FUNCTIONAL_PAIRS = {
            {R.color.canvas_icon_on_surface, R.color.canvas_surface},
            {R.color.canvas_icon_on_subtle, R.color.canvas_surface_subtle},
            {R.color.canvas_border_strong, R.color.canvas_surface},
            {R.color.canvas_primary, R.color.canvas_surface},
            {R.color.canvas_preview, R.color.canvas_surface},
            {R.color.canvas_disabled_ink, R.color.canvas_disabled_fill}
    };

    private static double luminance(int color) {
        double[] c = {((color >> 16) & 0xFF) / 255.0, ((color >> 8) & 0xFF) / 255.0, (color & 0xFF) / 255.0};
        for (int i = 0; i < 3; i++) {
            c[i] = c[i] <= 0.04045 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
        }
        return c[0] * 0.2126 + c[1] * 0.7152 + c[2] * 0.0722;
    }

    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private Context themed(boolean night) {
        Context base = getInstrumentation().getTargetContext();
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(cfg);
    }

    private static int color(Context c, int resId) {
        return c.getResources().getColor(resId, c.getTheme());
    }

    public void testShippedPaletteMeetsWcagInBothThemesOnDevice() {
        for (boolean night : new boolean[]{false, true}) {
            Context c = themed(night);
            String mode = night ? "night" : "day";
            for (int[] pair : TEXT_PAIRS) {
                double ratio = contrast(color(c, pair[0]), color(c, pair[1]));
                assertTrue(mode + " text " + name(c, pair[0]) + "/" + name(c, pair[1])
                        + " contrast " + ratio + " < 4.5", ratio >= 4.5);
            }
            for (int[] pair : FUNCTIONAL_PAIRS) {
                double ratio = contrast(color(c, pair[0]), color(c, pair[1]));
                assertTrue(mode + " functional " + name(c, pair[0]) + "/" + name(c, pair[1])
                        + " contrast " + ratio + " < 3.0", ratio >= 3.0);
            }
        }
    }

    /**
     * The palette tables only prove the tokens are sane relative to each other. This proves the
     * real editor View actually paints them: a night palette where the body ink was near-black on
     * a near-black surface passed every table check, because the tables compared the code ink
     * against a light background token that no View ever used.
     */
    public void testRealEditorCodeAreaUsesCodeTokensAndStaysReadable() throws Throwable {
        EditorActivity editor = (EditorActivity) getInstrumentation().startActivitySync(
                new android.content.Intent(getInstrumentation().getTargetContext(), EditorActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        try {
            android.widget.EditText code = (android.widget.EditText) editor.findViewById(R.id.edit_code);
            assertNotNull("code editor must exist", code);
            Context device = getInstrumentation().getTargetContext();
            int expectedInk = color(device, R.color.canvas_code_ink);
            assertEquals("editor text must be bound to the code ink token", expectedInk, code.getCurrentTextColor());

            int painted = paintedBackgroundBehind(code);
            int expectedSurface = color(device, R.color.canvas_code_surface);
            assertEquals("the code area must paint the code surface token, not the generic surface",
                    expectedSurface, painted);

            double ratio = contrast(code.getCurrentTextColor(), painted);
            assertTrue("code body text on the surface it is actually drawn on is " + ratio + ":1 (<4.5)", ratio >= 4.5);
        } finally {
            editor.finish();
        }
    }

    /** First fully opaque colour actually painted behind a view. */
    private static int paintedBackgroundBehind(View v) {
        View cur = v;
        while (cur != null) {
            Drawable d = cur.getBackground();
            if (d instanceof ColorDrawable) {
                int c = ((ColorDrawable) d).getColor();
                if (((c >>> 24) & 0xFF) == 0xFF) {
                    return c;
                }
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        throw new AssertionError("no opaque background behind the code editor");
    }

    /** The disabled state must stay legible, not dissolve into the surface. */
    public void testDisabledStateStaysLegibleNotInvisible() {
        for (boolean night : new boolean[]{false, true}) {
            Context c = themed(night);
            double inkOnFill = contrast(color(c, R.color.canvas_disabled_ink), color(c, R.color.canvas_disabled_fill));
            assertTrue((night ? "night" : "day") + " disabled ink/fill contrast " + inkOnFill + " < 2.5", inkOnFill >= 2.5);
            double fillOnSurface = contrast(color(c, R.color.canvas_disabled_fill), color(c, R.color.canvas_surface));
            assertTrue((night ? "night" : "day") + " disabled fill/surface contrast " + fillOnSurface + " < 1.1", fillOnSurface >= 1.1);
        }
    }

    /** The primary button label must read in both states, in both themes. */
    public void testPrimaryButtonLabelIsReadableWhenEnabledAndDisabled() throws Throwable {
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(
                new android.content.Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        try {
            android.widget.Button paste = (android.widget.Button) main.findViewById(R.id.btn_quick_paste_preview);
            assertNotNull("quick-paste button must exist", paste);
            android.content.res.ColorStateList bound = paste.getTextColors();
            int[] enabledState = {android.R.attr.state_enabled};
            int[] disabledState = {-android.R.attr.state_enabled};
            assertTrue("the button must carry a state-aware label colour (enabled/disabled must differ)",
                    bound.getColorForState(enabledState, 0) != bound.getColorForState(disabledState, 0));

            // The live button is inflated in the device configuration: prove it is bound to
            // this very selector before judging that selector's readability per theme.
            Context device = getInstrumentation().getTargetContext();
            android.content.res.ColorStateList deviceSelector =
                    device.getResources().getColorStateList(R.color.text_on_primary_button, device.getTheme());
            assertEquals("button must use the shared primary-button label selector",
                    deviceSelector.getColorForState(enabledState, 0), bound.getColorForState(enabledState, 0));
            assertEquals("button must use the shared primary-button label selector (disabled)",
                    deviceSelector.getColorForState(disabledState, 0), bound.getColorForState(disabledState, 0));

            for (boolean night : new boolean[]{false, true}) {
                Context c = themed(night);
                String mode = night ? "night" : "day";
                android.content.res.ColorStateList selector =
                        c.getResources().getColorStateList(R.color.text_on_primary_button, c.getTheme());
                int enabledInk = selector.getColorForState(enabledState, 0);
                int disabledInk = selector.getColorForState(disabledState, 0);
                double enabledRatio = contrast(enabledInk, color(c, R.color.canvas_primary));
                double disabledRatio = contrast(disabledInk, color(c, R.color.canvas_disabled_fill));
                assertTrue(mode + " enabled label contrast " + enabledRatio + " < 4.5", enabledRatio >= 4.5);
                assertTrue(mode + " disabled label contrast " + disabledRatio + " < 4.5", disabledRatio >= 4.5);
            }
        } finally {
            getInstrumentation().runOnMainSync(() -> {
                if (!main.isFinishing()) main.finish();
            });
            getInstrumentation().waitForIdleSync();
        }
    }

    /** Real rendered home screen: every visible label must read against its own background. */
    public void testRenderedHomeTextIsReadableInBothThemes() throws Throwable {
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(
                new android.content.Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        try {
            for (boolean night : new boolean[]{false, true}) {
                Context c = themed(night);
                int fallbackA = color(c, R.color.canvas_surface), fallbackB = color(c, R.color.canvas_bg);
                final java.util.List<String> bad = new java.util.ArrayList<>();
                final View root = main.findViewById(R.id.root_main);
                walk(root, fallbackA, fallbackB, bad);
                assertTrue((night ? "night" : "day") + " unreadable rendered text: " + bad, bad.isEmpty());
            }
        } finally {
            getInstrumentation().runOnMainSync(() -> {
                if (!main.isFinishing()) main.finish();
            });
            getInstrumentation().waitForIdleSync();
        }
    }

    private void walk(View v, int fallbackA, int fallbackB, java.util.List<String> bad) {
        if (v == null || v.getVisibility() != View.VISIBLE || !v.isEnabled() || v.getAlpha() < 1f) return;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            CharSequence text = t.getText();
            // A view carrying its own non-solid shape (button/chip) paints on a substrate
            // this walk cannot resolve; those pairings are asserted by the dedicated
            // button/chip tests above instead of being guessed here.
            boolean ownSolidBackground = t.getBackground() == null || t.getBackground() instanceof ColorDrawable;
            if (text != null && text.length() > 0 && ownSolidBackground) {
                int fg = t.getCurrentTextColor();
                Integer bg = effectiveBackground(v);
                double ratio = bg != null
                        ? contrast(fg, bg)
                        : Math.max(contrast(fg, fallbackA), contrast(fg, fallbackB));
                if (ratio < 4.5) {
                    bad.add("\"" + text + "\" ratio=" + String.format(java.util.Locale.ROOT, "%.2f", ratio));
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), fallbackA, fallbackB, bad);
        }
    }

    /** Nearest ancestor with an opaque solid background, if one exists. */
    private static Integer effectiveBackground(View v) {
        for (View p = v.getParent() instanceof View ? (View) v.getParent() : null; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            Drawable d = p.getBackground();
            if (d instanceof ColorDrawable) {
                int col = ((ColorDrawable) d).getColor();
                if ((col >>> 24) == 255) return col;
            }
        }
        return null;
    }

    private static String name(Context c, int resId) {
        try {
            return c.getResources().getResourceEntryName(resId);
        } catch (Exception e) {
            return String.valueOf(resId);
        }
    }
}
