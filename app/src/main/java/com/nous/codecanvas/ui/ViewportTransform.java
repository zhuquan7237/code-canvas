package com.nous.codecanvas.ui;

/**
 * Pan and zoom state with no Android types, so the focal-point rules can be tested on their own.
 *
 * <p>The point under the fingers stays under the fingers. Translation is in viewport pixels.
 * Scale is a multiple of the fitted size.</p>
 */
public final class ViewportTransform {

    public static final float MIN_SCALE = 0.1f;
    public static final float MAX_SCALE = 20f;
    public static final float DOUBLE_TAP_SCALE = 2.5f;
    /** How much of an overscroll is allowed to show. The rest is resisted. */
    private static final float RUBBER = 0.28f;
    private static final float MIN_VISIBLE = 0.15f;

    public float scale = 1f;
    public float tx;
    public float ty;
    public float viewW = 1f;
    public float viewH = 1f;
    public float vx;
    public float vy;

    public float contentX(float px) {
        return (px - tx) / scale;
    }

    public float contentY(float py) {
        return (py - ty) / scale;
    }

    /** Multiply the zoom about a screen point. That point's content stays put. */
    public void zoomAbout(float px, float py, float factor) {
        if (factor <= 0f || scale <= 0f) return;
        float target = clamp(scale * factor, MIN_SCALE, MAX_SCALE);
        float applied = target / scale;
        tx = px - (px - tx) * applied;
        ty = py - (py - ty) * applied;
        scale = target;
    }

    /** Zoom to an absolute factor about a screen point. */
    public void zoomTo(float target, float px, float py) {
        if (scale <= 0f) return;
        float clamped = clamp(target, MIN_SCALE, MAX_SCALE);
        zoomAbout(px, py, clamped / scale);
    }

    /** Zoom in about the tap, or back out to 1x about the same tap. */
    public void doubleTap(float px, float py) {
        if (Math.abs(scale - 1f) < 0.05f && Math.abs(tx) < 0.5f && Math.abs(ty) < 0.5f) {
            zoomTo(DOUBLE_TAP_SCALE, px, py);
        } else {
            zoomTo(1f, px, py);
        }
    }

    public void pan(float dx, float dy) {
        tx += dx;
        ty += dy;
    }

    /** Let the content travel past the edge, but only a fraction of the way. */
    public void dampEdges() {
        tx = rubber(tx, minTx(), maxTx());
        ty = rubber(ty, minTy(), maxTy());
    }

    /** Pull a released gesture back inside the hard limits. */
    public void settleEdges() {
        tx = clamp(tx, minTx(), maxTx());
        ty = clamp(ty, minTy(), maxTy());
        if (Math.abs(scale - 1f) < 0.001f && Math.abs(tx) < 0.5f && Math.abs(ty) < 0.5f) {
            scale = 1f;
            tx = 0f;
            ty = 0f;
        }
    }

    public void fit() {
        scale = 1f;
        tx = 0f;
        ty = 0f;
        vx = 0f;
        vy = 0f;
    }

    public void fling(float velocityX, float velocityY) {
        vx = velocityX;
        vy = velocityY;
    }

    /**
     * Advance a fling. Velocity is in pixels per second. Past an edge the velocity reverses
     * and shrinks, which is the bounce.
     *
     * @return true while the motion should keep going
     */
    public boolean step(float dtSeconds) {
        if (dtSeconds <= 0f) return false;
        float dt = Math.min(dtSeconds, 0.032f);
        tx += vx * dt;
        ty += vy * dt;
        float decay = (float) Math.exp(-3.4 * dt);
        vx *= decay;
        vy *= decay;
        bounceX();
        bounceY();
        if (Math.hypot(vx, vy) < 40f) {
            vx = 0f;
            vy = 0f;
            settleEdges();
            return false;
        }
        return true;
    }

    public boolean isFitted() {
        return Math.abs(scale - 1f) < 0.001f && tx == 0f && ty == 0f && vx == 0f && vy == 0f;
    }

    private void bounceX() {
        float min = minTx();
        float max = maxTx();
        if (tx < min) {
            tx = min;
            vx = Math.abs(vx) * 0.45f;
        } else if (tx > max) {
            tx = max;
            vx = -Math.abs(vx) * 0.45f;
        }
    }

    private void bounceY() {
        float min = minTy();
        float max = maxTy();
        if (ty < min) {
            ty = min;
            vy = Math.abs(vy) * 0.45f;
        } else if (ty > max) {
            ty = max;
            vy = -Math.abs(vy) * 0.45f;
        }
    }

    private float minTx() {
        float contentW = viewW * scale;
        float keep = Math.min(viewW, contentW) * MIN_VISIBLE;
        return keep - contentW;
    }

    private float maxTx() {
        float contentW = viewW * scale;
        float keep = Math.min(viewW, contentW) * MIN_VISIBLE;
        return viewW - keep;
    }

    private float minTy() {
        float contentH = viewH * scale;
        float keep = Math.min(viewH, contentH) * MIN_VISIBLE;
        return keep - contentH;
    }

    private float maxTy() {
        float contentH = viewH * scale;
        float keep = Math.min(viewH, contentH) * MIN_VISIBLE;
        return viewH - keep;
    }

    private static float rubber(float value, float min, float max) {
        if (value < min) return min - (min - value) * RUBBER;
        if (value > max) return max + (value - max) * RUBBER;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
