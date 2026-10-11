package com.nous.codecanvas.ui;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.nous.codecanvas.R;

/**
 * The app's one action surface.
 *
 * <p>Every "what do you want to do with this thing" menu in the app used to be a
 * different creature: a plain AlertDialog list here, a raw system PopupMenu there,
 * none of them sharing a shape. This is the single replacement — a bottom sheet
 * with a drag handle, icon-led action rows, and a tinted danger row for the one
 * action that cannot be undone. If a menu does not come from here, it does not
 * ship.</p>
 */
public final class CanvasBottomSheet {

    public interface OnActionListener {
        void onAction(int actionId);
    }

    public static final class Action {
        public final int id;
        public final int iconRes;      // 0 = no leading icon
        public final String label;
        public final String caption;   // nullable; small secondary line under the label
        public final boolean danger;

        public Action(int id, int iconRes, String label) {
            this(id, iconRes, label, null, false);
        }

        public Action(int id, int iconRes, String label, String caption, boolean danger) {
            this.id = id;
            this.iconRes = iconRes;
            this.label = label;
            this.caption = caption;
            this.danger = danger;
        }
    }

    private CanvasBottomSheet() {}

    /**
     * Test seam: the real action row at {@code index}, straight out of the live sheet. Tests drive
     * the actual rows and their real click listeners rather than a replica of the menu.
     */
    public static View actionRowAt(Dialog dialog, int index) {
        if (dialog == null) return null;
        LinearLayout container = dialog.findViewById(R.id.sheet_actions_container);
        if (container == null || index < 0 || index >= container.getChildCount()) return null;
        return container.getChildAt(index);
    }

    public static int actionCount(Dialog dialog) {
        if (dialog == null) return 0;
        LinearLayout container = dialog.findViewById(R.id.sheet_actions_container);
        return container == null ? 0 : container.getChildCount();
    }

    public static Dialog show(Context context, String title, String subtitle,
                              Action[] actions, OnActionListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_actions, null);

        View backdrop = sheet.findViewById(R.id.sheet_backdrop);
        View card = sheet.findViewById(R.id.sheet_card);
        View handle = sheet.findViewById(R.id.sheet_handle);
        TextView txtTitle = sheet.findViewById(R.id.sheet_title);
        TextView txtSubtitle = sheet.findViewById(R.id.sheet_subtitle);
        LinearLayout container = sheet.findViewById(R.id.sheet_actions_container);

        if (backdrop != null) {
            backdrop.setOnClickListener(v -> dialog.dismiss());
        }
        if (card != null) {
            card.setOnClickListener(v -> {
                // Consume clicks inside the sheet card surface so they do not bubble to backdrop.
            });
        }
        if (handle != null) {
            handle.setOnClickListener(v -> dialog.dismiss());
        }

        if (title == null || title.isEmpty()) {
            txtTitle.setVisibility(View.GONE);
        } else {
            txtTitle.setText(title);
        }
        if (subtitle == null || subtitle.isEmpty()) {
            txtSubtitle.setVisibility(View.GONE);
        } else {
            txtSubtitle.setText(subtitle);
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (Action action : actions) {
            View row = inflater.inflate(R.layout.item_sheet_action, container, false);
            ImageView icon = row.findViewById(R.id.action_icon);
            TextView label = row.findViewById(R.id.action_label);
            TextView caption = row.findViewById(R.id.action_caption);

            if (action.iconRes != 0) {
                icon.setImageResource(action.iconRes);
            } else {
                icon.setVisibility(View.INVISIBLE);
            }
            label.setText(action.label);
            if (action.caption != null && !action.caption.isEmpty()) {
                caption.setVisibility(View.VISIBLE);
                caption.setText(action.caption);
            }
            if (action.danger) {
                row.setBackgroundResource(R.drawable.bg_sheet_action_danger);
                label.setTextColor(context.getResources().getColor(R.color.canvas_danger));
                icon.setColorFilter(context.getResources().getColor(R.color.canvas_danger));
            }

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) listener.onAction(action.id);
            });
            container.addView(row);
        }

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.5f;
            window.setAttributes(lp);
        }
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        if (card != null && ValueAnimator.areAnimatorsEnabled()) {
            float offset = 120f * context.getResources().getDisplayMetrics().density;
            card.setTranslationY(offset);
            card.setAlpha(0.6f);
            card.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }
        return dialog;
    }
}
