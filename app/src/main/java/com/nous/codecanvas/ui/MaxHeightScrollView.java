package com.nous.codecanvas.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ScrollView;

import com.nous.codecanvas.R;

/**
 * A ScrollView that stops growing at a maximum height.
 *
 * <p>{@code android:maxHeight} is only honoured by {@code ImageView} and {@code TextView}; putting
 * it on a ScrollView does nothing at all, which is how a long update note pushed a dialog's action
 * buttons off the bottom of the screen and left the dialog dismissable only with the back key.
 * This caps the measured height so the rest of the dialog stays where it belongs.</p>
 */
public class MaxHeightScrollView extends ScrollView {

    private int maxHeightPx;

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        readMaxHeight(context, attrs);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        readMaxHeight(context, attrs);
    }

    private void readMaxHeight(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView);
        try {
            maxHeightPx = a.getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, 0);
        } finally {
            a.recycle();
        }
    }

    public void setMaxHeight(int px) {
        maxHeightPx = px;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeightPx > 0) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
