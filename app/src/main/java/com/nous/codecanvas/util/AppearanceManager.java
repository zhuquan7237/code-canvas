package com.nous.codecanvas.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Appearance (light / dark / follow-system) without AndroidX.
 *
 * <p>This app ships as pure native Java with zero third-party UI libraries, so
 * {@code AppCompatDelegate} is not available. The framework-only equivalent is to hand each
 * Activity a Context whose Configuration already carries the chosen {@code uiMode}: the platform
 * then resolves {@code res/values-night/*} on its own, so the palette, the status-bar icons and
 * the dialog themes all flip together instead of each screen re-deciding what "dark" means.</p>
 *
 * <p><b>Why {@code attachBaseContext} and not {@code applyOverrideConfiguration}.</b>
 * {@code ContextThemeWrapper.applyOverrideConfiguration} throws
 * {@code IllegalStateException("getResources() or getAssets() has already been called")} once the
 * Activity's resources have been touched — and {@code Activity.performCreate} reads them before
 * {@code onCreate} runs, so calling it from {@code onCreate} (even as the very first statement)
 * crashes with that exact exception. It additionally cannot be called twice, which would have
 * broken the first {@code recreate()} after a theme change as well. Rewriting the base Context is
 * the supported route and has neither restriction.</p>
 */
public final class AppearanceManager {

    /** Follow the system setting (default). */
    public static final int MODE_SYSTEM = 0;
    /** Always light. */
    public static final int MODE_LIGHT = 1;
    /** Always dark. */
    public static final int MODE_DARK = 2;

    private AppearanceManager() {}

    /** The stored preference. Falls back to follow-system when the value is unknown. */
    public static int getMode(Context c) {
        int v = CanvasPrefs.getInt(c, CanvasPrefs.KEY_APPEARANCE, MODE_SYSTEM);
        return (v == MODE_LIGHT || v == MODE_DARK) ? v : MODE_SYSTEM;
    }

    public static void setMode(Context c, int mode) {
        CanvasPrefs.putInt(c, CanvasPrefs.KEY_APPEARANCE, mode);
    }

    /**
     * Wrap a base Context so its resources resolve against the user's appearance choice.
     *
     * <p>Call from {@code Activity.attachBaseContext}. On {@link #MODE_SYSTEM} the base Context is
     * returned untouched, so the platform keeps following the device setting live (including
     * scheduled dark mode) rather than freezing whatever the system happened to be at start-up.</p>
     */
    public static Context wrap(Context base) {
        int mode = getMode(base);
        if (mode == MODE_SYSTEM) {
            return base;
        }
        Configuration override = new Configuration(base.getResources().getConfiguration());
        int night = (mode == MODE_DARK)
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        override.uiMode = (override.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(override);
    }

    /**
     * Whether this Context is currently rendering the dark palette.
     *
     * <p>Derived from the resolved resources rather than from the preference, so it stays truthful
     * when the mode is "follow system" and the device flips on schedule.</p>
     */
    public static boolean isDark(Context c) {
        int mask = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (mask == Configuration.UI_MODE_NIGHT_YES) {
            return true;
        }
        if (mask == Configuration.UI_MODE_NIGHT_NO) {
            return false;
        }
        // UI_MODE_NIGHT_UNDEFINED: honour an explicit preference, else assume light.
        return getMode(c) == MODE_DARK;
    }

    /** Human-readable label for the settings screen. */
    public static String label(Context c, int mode) {
        switch (mode) {
            case MODE_LIGHT:
                return "浅色";
            case MODE_DARK:
                return "深色";
            default:
                return "跟随系统";
        }
    }

    /** True when the platform is new enough for the framework's own dark-mode APIs. */
    public static boolean supportsFrameworkNightMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
}
