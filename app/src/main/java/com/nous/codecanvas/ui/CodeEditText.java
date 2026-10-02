package com.nous.codecanvas.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * An EditText that reports cursor moves.
 *
 * <p>The gutter has to emphasise the line the cursor is on, and {@code EditText} offers no
 * selection-changed callback — the usual workaround is a TextWatcher plus a click listener, which
 * still misses arrow keys and programmatic moves. Overriding the one method that the framework
 * already calls is both shorter and correct.</p>
 */
public class CodeEditText extends EditText {

    public interface OnCursorLineChanged {
        void onCursorLineChanged(int line);
    }

    private OnCursorLineChanged listener;

    public CodeEditText(Context context) {
        super(context);
    }

    public CodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CodeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnCursorLineChanged(OnCursorLineChanged listener) {
        this.listener = listener;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) {
            listener.onCursorLineChanged(
                    com.nous.codecanvas.editor.LineNumberGutter.lineOf(getText(), Math.max(selStart, selEnd)));
        }
    }
}
