package com.nous.codecanvas.ui;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Pads a screen for the status and navigation bars only.
 *
 * <p>These activities also use {@code adjustResize}. {@code getSystemWindowInsetBottom()} on
 * API 30+ reports the keyboard as well, so applying it as padding pushes the focused field
 * up a second time and the code ends up hidden behind the keyboard.</p>
 */
final class SystemBarInsets {

    private SystemBarInsets() {
    }

    static void apply(View view, WindowInsets insets) {
        if (view == null || insets == null) return;
        int left;
        int top;
        int right;
        int bottom;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            left = bars.left;
            top = bars.top;
            right = bars.right;
            // The keyboard is not a resize on this target: ignoring it hides the focused
            // field. Taking the larger of the nav bar and the keyboard keeps one gap.
            bottom = Math.max(bars.bottom, ime.bottom);
        } else {
            left = insets.getSystemWindowInsetLeft();
            top = insets.getSystemWindowInsetTop();
            right = insets.getSystemWindowInsetRight();
            bottom = insets.getSystemWindowInsetBottom();
        }
        view.setPadding(left, top, right, bottom);
    }
}
