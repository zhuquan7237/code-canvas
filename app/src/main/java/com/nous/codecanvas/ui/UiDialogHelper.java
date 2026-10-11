package com.nous.codecanvas.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.nous.codecanvas.R;
import com.nous.codecanvas.util.FileUtils;

/**
 * The app's one input dialog — same surface, same radius, same button rule as
 * {@link CanvasConfirmDialog}.
 */
public class UiDialogHelper {

    public interface OnInputConfirmedListener {
        void onConfirmed(String text);
    }

    public static AlertDialog createThemedInputDialog(
            Context context,
            String title,
            String hint,
            String initialText,
            final OnInputConfirmedListener listener
    ) {
        return createThemedInputDialog(context, title, hint, initialText,
                InputType.TYPE_CLASS_TEXT, "确定", false, true, listener);
    }

    /**
     * @param inputType      platform input type (numeric for "跳转到行")
     * @param positiveLabel  action button label
     * @param allowEmpty     when false the action stays disabled until something is typed
     * @param sanitizeAsFile true when the value becomes a file name
     */
    public static AlertDialog createThemedInputDialog(
            Context context,
            String title,
            String hint,
            String initialText,
            int inputType,
            String positiveLabel,
            boolean allowEmpty,
            boolean sanitizeAsFile,
            final OnInputConfirmedListener listener
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_themed_input, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView txtTitle = dialogView.findViewById(R.id.dialog_title);
        final EditText editInput = dialogView.findViewById(R.id.dialog_edit_input);
        final TextView txtErrorTip = dialogView.findViewById(R.id.dialog_error_tip);
        Button btnNegative = dialogView.findViewById(R.id.dialog_btn_negative);
        final Button btnPositive = dialogView.findViewById(R.id.dialog_btn_positive);

        txtTitle.setText(title);
        editInput.setHint(hint);
        editInput.setInputType(inputType);
        if (initialText != null) {
            editInput.setText(initialText);
            editInput.setSelection(initialText.length());
        }
        btnPositive.setText(positiveLabel);

        final Runnable validate = () -> {
            String val = editInput.getText().toString().trim();
            boolean ok = allowEmpty || !val.isEmpty();
            btnPositive.setEnabled(ok);
            btnPositive.setAlpha(ok ? 1.0f : 0.45f);
            if (!ok) {
                txtErrorTip.setVisibility(View.VISIBLE);
                txtErrorTip.setText("内容不能为空");
            } else {
                txtErrorTip.setVisibility(View.GONE);
            }
        };
        validate.run();

        editInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { validate.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnNegative.setOnClickListener(v -> dialog.dismiss());
        btnPositive.setOnClickListener(v -> {
            String raw = editInput.getText().toString().trim();
            String value = sanitizeAsFile ? FileUtils.sanitizeFileName(raw) : raw;
            if (!allowEmpty && value.isEmpty()) {
                txtErrorTip.setVisibility(View.VISIBLE);
                txtErrorTip.setText(sanitizeAsFile ? "请输入有效的文件名" : "请输入有效内容");
                return;
            }
            dialog.dismiss();
            if (listener != null) {
                listener.onConfirmed(value);
            }
        });

        // Focus the field and raise the keyboard: the dialog exists to be typed into.
        dialog.setOnShowListener(d -> {
            editInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editInput, InputMethodManager.SHOW_IMPLICIT);
        });

        return dialog;
    }
}
