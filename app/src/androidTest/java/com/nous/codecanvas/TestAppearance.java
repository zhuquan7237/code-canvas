package com.nous.codecanvas;

import android.content.Context;

import com.nous.codecanvas.util.AppearanceManager;
import com.nous.codecanvas.util.CanvasPrefs;

/**
 * Test isolation for the appearance preference.
 *
 * <p>Appearance is a persisted, app-wide setting, and that makes it the one piece of state that
 * leaks between instrumentation tests: a test that switches to "浅色" leaves the preference behind,
 * and the next test then reads a palette it did not ask for — so an assertion pinned to the dark
 * ink token fails on a perfectly correct render, and a thumbnail written under one uiMode is looked
 * for under another. Every test that asserts on resolved colours or on theme-derived keys must
 * start from a known theme.</p>
 *
 * <p>Tests run against the device's own uiMode, which is what the product does in
 * {@link AppearanceManager#MODE_SYSTEM}. Resetting to that mode — rather than forcing light or
 * dark — keeps the assertions honest about the "follow system" default too.</p>
 */
final class TestAppearance {

    private TestAppearance() {}

    /** Restore the shipped default so the palette the test sees is the device's, not a leftover. */
    static void resetToSystem(Context context) {
        CanvasPrefs.putInt(context, CanvasPrefs.KEY_APPEARANCE, AppearanceManager.MODE_SYSTEM);
    }
}
