package com.nous.codecanvas.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** A readable stand-in for a text file. A WebView capture of one came out as a black block. */
final class TextTile {

    private TextTile() {
    }

    static Bitmap draw(String content, boolean dark, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int paper = dark ? 0xFF1B2430 : 0xFFF8FAFC;
        int ink = dark ? 0xFFE7EEF8 : 0xFF1E293B;
        canvas.drawColor(paper);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ink);
        paint.setTextSize(Math.max(16f, height / 9f));
        String[] raw = content == null ? new String[0] : content.split("\\r\\n|\\n|\\r", 12);
        float y = paint.getTextSize() + 16f;
        int drawn = 0;
        for (String line : raw) {
            if (line.trim().isEmpty()) continue;
            String shown = line.length() > 36 ? line.substring(0, 36) : line;
            canvas.drawText(shown, 18f, y, paint);
            y += paint.getTextSize() * 1.45f;
            drawn++;
            if (drawn >= 7 || y > height - 8f) break;
        }
        if (drawn == 0) {
            paint.setColor(dark ? 0xFF94A3B8 : 0xFF64748B);
            canvas.drawText("文本", 18f, height / 2f, paint);
        }
        return bitmap;
    }
}
