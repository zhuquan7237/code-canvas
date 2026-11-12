package com.nous.codecanvas.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.nous.codecanvas.data.DocumentKind;

import java.util.HashMap;
import java.util.Map;

/**
 * A coloured tile with the format name, drawn before a real thumbnail exists.
 * A white square reads as a broken image; this does not.
 */
final class KindPlaceholder {

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private KindPlaceholder() {
    }

    static Bitmap bitmap(Context context, DocumentKind kind) {
        if (kind == null) kind = DocumentKind.UNKNOWN;
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String key = kind.name() + (night ? ":n" : ":d");
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        int size = 128;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(context.getResources().getColor(KindStyle.fillRes(kind)));
        canvas.drawRect(0, 0, size, size, paint);
        paint.setColor(context.getResources().getColor(KindStyle.inkRes(kind)));
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        String mark = KindStyle.mark(kind);
        paint.setTextSize(mark.length() > 4 ? 28f : mark.length() > 2 ? 36f : 52f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = size / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(mark, size / 2f, y, paint);
        CACHE.put(key, bitmap);
        return bitmap;
    }
}
