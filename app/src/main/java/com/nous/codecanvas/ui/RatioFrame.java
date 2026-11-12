package com.nous.codecanvas.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** A 16:10 slot. Wide drawings stay a band, not a thin strip and not a centre crop. */
public class RatioFrame extends FrameLayout {

    public RatioFrame(Context context) {
        super(context);
    }

    public RatioFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RatioFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.max(1, Math.round(width * 10f / 16f));
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
