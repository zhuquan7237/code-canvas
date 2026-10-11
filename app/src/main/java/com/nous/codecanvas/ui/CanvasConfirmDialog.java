package com.nous.codecanvas.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.nous.codecanvas.R;

/**
 * The app's one confirmation dialog.
 *
 * <p>Stock AlertDialogs carried the system's corners, the system's button bar and the system's
 * idea of what a destructive action looks like — every one of them a slightly different room.
 * This is the single replacement: the same surface, the same 20dp radius and the same button
 * choreography as the input dialog, with an optional danger treatment and an optional content
 * slot for a switch row or a short list.</p>
 */
public final class CanvasConfirmDialog {

    public interface OnConfirmListener {
        void onConfirm();
    }

    private CanvasConfirmDialog() {}

    /**
     * Test seam: the dialog's own action buttons. Tests drive the buttons a user would tap
     * instead of the platform button bar, which these dialogs do not use.
     */
    public static View confirmButton(android.app.Dialog dialog) {
        return dialog == null ? null : dialog.findViewById(R.id.dialog_btn_positive);
    }

    public static View cancelButton(android.app.Dialog dialog) {
        return dialog == null ? null : dialog.findViewById(R.id.dialog_btn_negative);
    }

    /** Danger-free confirm with a single callback. */
    public static AlertDialog show(Context context, String title, String message,
                                   String positiveLabel, boolean danger,
                                   OnConfirmListener listener) {
        return show(context, title, message, positiveLabel, "取消", danger, listener, null);
    }

    /**
     * Both paths callback-carrying: a dialog that exists only to branch (rebuild the preview vs go
     * back to code) needs the negative answer as much as the positive one.
     */
    public static AlertDialog show(Context context, String title, String message,
                                   String positiveLabel, String negativeLabel, boolean danger,
                                   OnConfirmListener listener, OnConfirmListener onNegative) {
        return showCustom(context, title, message, null, positiveLabel, negativeLabel, danger,
                listener, onNegative);
    }

    /**
     * Both paths labelled, only the positive one wired: the shape most "you can also just cancel"
     * confirmations actually need.
     */
    public static AlertDialog show(Context context, String title, String message,
                                   String positiveLabel, String negativeLabel, boolean danger,
                                   OnConfirmListener listener) {
        return show(context, title, message, positiveLabel, negativeLabel, danger, listener, null);
    }

    /** A confirm dialog carrying arbitrary content: a switch row, a short list of choices. */
    public static AlertDialog showCustom(Context context, String title, String message, View content,
                                         String positiveLabel, String negativeLabel, boolean danger,
                                         OnConfirmListener listener, OnConfirmListener onNegative) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null);
        TextView txtTitle = view.findViewById(R.id.dialog_title);
        TextView txtMessage = view.findViewById(R.id.dialog_message);
        Button btnNegative = view.findViewById(R.id.dialog_btn_negative);
        Button btnPositive = view.findViewById(R.id.dialog_btn_positive);
        FrameLayout slot = view.findViewById(R.id.dialog_content);

        txtTitle.setText(title);
        if (message == null || message.isEmpty()) {
            txtMessage.setVisibility(View.GONE);
        } else {
            txtMessage.setText(message);
        }
        if (content != null) {
            slot.setVisibility(View.VISIBLE);
            slot.addView(content);
        }
        btnPositive.setText(positiveLabel);
        btnNegative.setText(negativeLabel != null ? negativeLabel : "取消");
        if (danger) {
            btnPositive.setBackgroundResource(R.drawable.bg_dialog_button_danger);
            btnPositive.setTextColor(context.getResources().getColor(R.color.canvas_danger));
        }

        AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnNegative.setOnClickListener(v -> {
            dialog.dismiss();
            if (onNegative != null) onNegative.onConfirm();
        });
        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) listener.onConfirm();
        });
        dialog.show();
        return dialog;
    }
}
