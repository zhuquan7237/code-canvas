package com.nous.codecanvas.util;

import android.content.Context;

/**
 * App-wide switches that must mean the same thing everywhere.
 *
 * <p>Scripts used to be a per-screen toggle that reset every visit, and the home screen's thumbnail
 * renderer hard-coded its own answer — so "allow scripts" was really three opinions. It is one
 * remembered setting now.</p>
 */
public final class CanvasPrefs {

    private static final String FILE = "codecanvas_prefs";
    private static final String KEY_ALLOW_SCRIPTS = "allow_scripts";

    private CanvasPrefs() {}

    /** Scripts default to on: charts, animation and calculators simply do not render without them. */
    public static boolean scriptsAllowed(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ALLOW_SCRIPTS, true);
    }

    public static void setScriptsAllowed(Context c, boolean allowed) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ALLOW_SCRIPTS, allowed).apply();
    }
}
