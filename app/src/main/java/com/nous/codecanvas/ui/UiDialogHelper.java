package com.nous.codecanvas.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.nous.codecanvas.R;
import com.nous.codecanvas.util.FileUtils;

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
        if (initialText != null) {
            editInput.setText(initialText);
            editInput.setSelection(initialText.length());
        }

        editInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String val = s.toString().trim();
                if (val.isEmpty()) {
                    txtErrorTip.setVisibility(View.VISIBLE);
                    txtErrorTip.setText("文件名不能为空");
                    btnPositive.setEnabled(false);
                    btnPositive.setAlpha(0.5f);
                } else {
                    txtErrorTip.setVisibility(View.GONE);
                    btnPositive.setEnabled(true);
                    btnPositive.setAlpha(1.0f);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnNegative.setOnClickListener(v -> dialog.dismiss());
        btnPositive.setOnClickListener(v -> {
            String sanitized = FileUtils.sanitizeFileName(editInput.getText().toString().trim());
            if (sanitized.isEmpty()) {
                txtErrorTip.setVisibility(View.VISIBLE);
                txtErrorTip.setText("请输入有效的文件名");
                return;
            }
            dialog.dismiss();
            if (listener != null) {
                listener.onConfirmed(sanitized);
            }
        });

        return dialog;
    }
}
