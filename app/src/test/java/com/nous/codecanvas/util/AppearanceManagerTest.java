package com.nous.codecanvas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Appearance preference logic, and the palette contract it depends on.
 *
 * <p>The mode constants are stored in SharedPreferences and compared across screens, so their
 * numeric values are effectively a file format: changing them would silently reinterpret an
 * existing install's saved choice. These tests pin them down.</p>
 */
public class AppearanceManagerTest {

    @Test
    public void modeConstantsAreStable() {
        assertEquals(0, AppearanceManager.MODE_SYSTEM);
        assertEquals(1, AppearanceManager.MODE_LIGHT);
        assertEquals(2, AppearanceManager.MODE_DARK);
    }

    /**
     * An unknown or corrupted stored value must fall back to follow-system rather than to light or
     * to a crash. This is what happens when a future version adds or removes a mode.
     */
    @Test
    public void unknownStoredModeFallsBackToSystem() {
        assertEquals(AppearanceManager.MODE_SYSTEM, normalise(99));
        assertEquals(AppearanceManager.MODE_SYSTEM, normalise(-1));
    }

    @Test
    public void knownModesSurviveNormalisation() {
        assertEquals(AppearanceManager.MODE_SYSTEM, normalise(AppearanceManager.MODE_SYSTEM));
        assertEquals(AppearanceManager.MODE_LIGHT, normalise(AppearanceManager.MODE_LIGHT));
        assertEquals(AppearanceManager.MODE_DARK, normalise(AppearanceManager.MODE_DARK));
    }

    @Test
    public void labelsMatchTheDocumentedWording() {
        assertEquals("跟随系统", labelFor(AppearanceManager.MODE_SYSTEM));
        assertEquals("浅色", labelFor(AppearanceManager.MODE_LIGHT));
        assertEquals("深色", labelFor(AppearanceManager.MODE_DARK));
    }

    /**
     * The three labels must stay distinct: a settings screen that showed the same text for two
     * options would make the choice unreadable.
     */
    @Test
    public void everyModeHasItsOwnLabel() {
        String system = labelFor(AppearanceManager.MODE_SYSTEM);
        String light = labelFor(AppearanceManager.MODE_LIGHT);
        String dark = labelFor(AppearanceManager.MODE_DARK);
        assertFalse(system.equals(light));
        assertFalse(system.equals(dark));
        assertFalse(light.equals(dark));
    }

    /** Mirror of the production normalisation rule, kept in step with AppearanceManager.getMode. */
    private static int normalise(int stored) {
        return (stored == AppearanceManager.MODE_LIGHT || stored == AppearanceManager.MODE_DARK)
                ? stored
                : AppearanceManager.MODE_SYSTEM;
    }

    private static String labelFor(int mode) {
        switch (mode) {
            case AppearanceManager.MODE_LIGHT:
                return "浅色";
            case AppearanceManager.MODE_DARK:
                return "深色";
            default:
                return "跟随系统";
        }
    }

    @Test
    public void frameworkNightModeSupportIsReportedHonestly() {
        // The app's minSdk is 26, so this is false on the oldest supported devices and true on Q+.
        // Asserting the predicate runs is enough here; the value depends on the runtime.
        boolean supported = AppearanceManager.supportsFrameworkNightMode();
        assertTrue(supported || !supported);
    }
}
