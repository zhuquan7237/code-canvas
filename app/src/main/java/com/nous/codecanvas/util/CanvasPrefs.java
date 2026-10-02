package com.nous.codecanvas.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * App-wide switches that must mean the same thing everywhere.
 *
 * <p>Scripts used to be a per-screen toggle that reset every visit, and the home screen's thumbnail
 * renderer hard-coded its own answer — so "allow scripts" was really three opinions. It is one
 * remembered setting now, and every other cross-screen preference goes through the same store
 * rather than being re-invented per screen.</p>
 */
public final class CanvasPrefs {

    private static final String FILE = "codecanvas_prefs";
    private static final String KEY_ALLOW_SCRIPTS = "allow_scripts";
    private static final String KEY_THUMBNAILS = "home_thumbnails";
    private static final String KEY_NETWORK = "allow_network";

    /** Appearance: 0 follow system, 1 light, 2 dark. See {@link AppearanceManager}. */
    public static final String KEY_APPEARANCE = "appearance_mode";

    private CanvasPrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Scripts default to on: charts, animation and calculators simply do not render without them. */
    public static boolean scriptsAllowed(Context c) {
        return prefs(c).getBoolean(KEY_ALLOW_SCRIPTS, true);
    }

    public static void setScriptsAllowed(Context c, boolean allowed) {
        prefs(c).edit().putBoolean(KEY_ALLOW_SCRIPTS, allowed).apply();
    }

    /** Home-screen thumbnails: on by default, since previewing is the whole point of the app. */
    public static boolean thumbnailsEnabled(Context c) {
        return prefs(c).getBoolean(KEY_THUMBNAILS, true);
    }

    public static void setThumbnailsEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_THUMBNAILS, enabled).apply();
    }

    /**
     * Whether a freshly opened preview starts with remote loading allowed.
     *
     * <p>Off by default. Network access stays a per-document, explicitly confirmed action; this
     * preference only decides what the switch looks like when a document is first opened, and is
     * intentionally not remembered from a previous session's one-off consent.</p>
     */
    public static boolean networkAllowedByDefault(Context c) {
        return prefs(c).getBoolean(KEY_NETWORK, false);
    }

    public static void setNetworkAllowedByDefault(Context c, boolean allowed) {
        prefs(c).edit().putBoolean(KEY_NETWORK, allowed).apply();
    }

    // --- generic accessors used by the settings screen -----------------------------------------

    public static int getInt(Context c, String key, int fallback) {
        return prefs(c).getInt(key, fallback);
    }

    public static void putInt(Context c, String key, int value) {
        prefs(c).edit().putInt(key, value).apply();
    }

    public static String getString(Context c, String key, String fallback) {
        return prefs(c).getString(key, fallback);
    }

    public static void putString(Context c, String key, String value) {
        prefs(c).edit().putString(key, value).apply();
    }
}
