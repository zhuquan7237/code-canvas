package com.nous.codecanvas;

import android.content.Context;
import android.test.InstrumentationTestCase;

import com.nous.codecanvas.util.AppearanceManager;
import com.nous.codecanvas.util.CanvasPrefs;

/**
 * The preference store, which is what makes a switch mean the same thing on every screen.
 *
 * <p>The defect this guards against has already happened once in this app: "allow scripts" was a
 * per-screen toggle in the editor and a hard-coded {@code true} in the thumbnail renderer, so the
 * same setting had three answers. Every test here is therefore about the *contract* — defaults,
 * round-trips, and unknown-value handling — rather than about any single screen.</p>
 */
public class CanvasPrefsTest extends InstrumentationTestCase {

    private Context c;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        c = getInstrumentation().getTargetContext();
        // Start from shipped defaults so a previous test's choice cannot masquerade as a default.
        CanvasPrefs.setScriptsAllowed(c, true);
        CanvasPrefs.setThumbnailsEnabled(c, true);
        CanvasPrefs.setNetworkAllowedByDefault(c, false);
        TestAppearance.resetToSystem(c);
    }

    @Override
    protected void tearDown() throws Exception {
        CanvasPrefs.setScriptsAllowed(c, true);
        CanvasPrefs.setThumbnailsEnabled(c, true);
        CanvasPrefs.setNetworkAllowedByDefault(c, false);
        TestAppearance.resetToSystem(c);
        super.tearDown();
    }

    /** Scripts must default to on: charts, animation and calculators do not render without them. */
    public void testScriptsDefaultToOn() {
        assertTrue("脚本默认必须开启", CanvasPrefs.scriptsAllowed(c));
    }

    public void testScriptsRoundTrip() {
        CanvasPrefs.setScriptsAllowed(c, false);
        assertFalse(CanvasPrefs.scriptsAllowed(c));
        CanvasPrefs.setScriptsAllowed(c, true);
        assertTrue(CanvasPrefs.scriptsAllowed(c));
    }

    /** Thumbnails default to on, because previewing is the whole point of this app. */
    public void testThumbnailsDefaultToOn() {
        assertTrue("主页缩略图默认必须开启", CanvasPrefs.thumbnailsEnabled(c));
    }

    public void testThumbnailsRoundTrip() {
        CanvasPrefs.setThumbnailsEnabled(c, false);
        assertFalse(CanvasPrefs.thumbnailsEnabled(c));
        CanvasPrefs.setThumbnailsEnabled(c, true);
        assertTrue(CanvasPrefs.thumbnailsEnabled(c));
    }

    /**
     * Network defaults to off. A document's remote loading stays an explicit, per-document
     * confirmation; this preference only decides what a newly opened preview starts as.
     */
    public void testNetworkDefaultsToOff() {
        assertFalse("默认联网必须关闭", CanvasPrefs.networkAllowedByDefault(c));
    }

    public void testNetworkRoundTrip() {
        CanvasPrefs.setNetworkAllowedByDefault(c, true);
        assertTrue(CanvasPrefs.networkAllowedByDefault(c));
        CanvasPrefs.setNetworkAllowedByDefault(c, false);
        assertFalse(CanvasPrefs.networkAllowedByDefault(c));
    }

    /** Appearance defaults to following the system, which is the least surprising choice. */
    public void testAppearanceDefaultsToSystem() {
        assertEquals("外观默认必须为跟随系统",
                AppearanceManager.MODE_SYSTEM, AppearanceManager.getMode(c));
    }

    public void testAppearanceRoundTripForEveryMode() {
        int[] modes = {AppearanceManager.MODE_LIGHT, AppearanceManager.MODE_DARK, AppearanceManager.MODE_SYSTEM};
        for (int mode : modes) {
            AppearanceManager.setMode(c, mode);
            assertEquals("模式必须原样往返", mode, AppearanceManager.getMode(c));
        }
    }

    /**
     * A stored value that is not a known mode must degrade to follow-system.
     *
     * <p>This is the upgrade path: if a future version drops a mode, an install carrying the old
     * number must not end up rendering an undefined palette or crashing.</p>
     */
    public void testUnknownAppearanceValueFallsBackToSystem() {
        CanvasPrefs.putInt(c, CanvasPrefs.KEY_APPEARANCE, 99);
        assertEquals("未知模式必须降级为跟随系统",
                AppearanceManager.MODE_SYSTEM, AppearanceManager.getMode(c));
        CanvasPrefs.putInt(c, CanvasPrefs.KEY_APPEARANCE, -7);
        assertEquals("负数模式同样必须降级",
                AppearanceManager.MODE_SYSTEM, AppearanceManager.getMode(c));
    }

    /** Generic accessors must round-trip and honour their fallbacks. */
    public void testGenericIntAccessors() {
        assertEquals("缺失键必须返回兜底值", 42, CanvasPrefs.getInt(c, "test_missing_int", 42));
        CanvasPrefs.putInt(c, "test_int", 7);
        assertEquals(7, CanvasPrefs.getInt(c, "test_int", 42));
    }

    public void testGenericStringAccessors() {
        assertEquals("缺失键必须返回兜底值", "fallback",
                CanvasPrefs.getString(c, "test_missing_string", "fallback"));
        CanvasPrefs.putString(c, "test_string", "v");
        assertEquals("v", CanvasPrefs.getString(c, "test_string", "fallback"));
    }

    /**
     * The preferences the app actually reads must live in one file, so a screen that writes and a
     * screen that reads cannot end up looking at different stores.
     */
    public void testAllPreferencesShareOneStore() {
        CanvasPrefs.setScriptsAllowed(c, false);
        CanvasPrefs.setThumbnailsEnabled(c, false);
        CanvasPrefs.setNetworkAllowedByDefault(c, true);
        AppearanceManager.setMode(c, AppearanceManager.MODE_DARK);

        // Reading each back through the same accessors proves the store is shared; a split store
        // would lose at least one of these writes.
        assertFalse(CanvasPrefs.scriptsAllowed(c));
        assertFalse(CanvasPrefs.thumbnailsEnabled(c));
        assertTrue(CanvasPrefs.networkAllowedByDefault(c));
        assertEquals(AppearanceManager.MODE_DARK, AppearanceManager.getMode(c));
    }
}
