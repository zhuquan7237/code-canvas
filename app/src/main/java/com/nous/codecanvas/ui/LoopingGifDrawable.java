package com.nous.codecanvas.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * Plays a GIF on API 26 and 27, where {@code ImageDecoder} does not exist yet.
 * The drawable letterboxes the movie inside its bounds and keeps scheduling frames.
 */
final class LoopingGifDrawable extends Drawable implements Runnable {

    private final Movie movie;
    private long start;

    LoopingGifDrawable(Movie movie) {
        this.movie = movie;
    }

    @Override
    public void draw(Canvas canvas) {
        if (movie == null) return;
        long now = SystemClock.uptimeMillis();
        if (start == 0L) start = now;
        int duration = movie.duration();
        if (duration <= 0) duration = 1000;
        movie.setTime((int) ((now - start) % duration));

        Rect bounds = getBounds();
        int mw = Math.max(1, movie.width());
        int mh = Math.max(1, movie.height());
        float scale = Math.min(bounds.width() / (float) mw, bounds.height() / (float) mh);
        if (scale <= 0f) scale = 1f;
        canvas.save();
        canvas.translate(
                bounds.left + (bounds.width() - mw * scale) / 2f,
                bounds.top + (bounds.height() - mh * scale) / 2f);
        canvas.scale(scale, scale);
        movie.draw(canvas, 0, 0);
        canvas.restore();
        scheduleSelf(this, now + 32);
    }

    @Override
    public void run() {
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
