package com.nous.codecanvas.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The zoom / pan / fit viewport for vector content.
 *
 * <p>Everything here is <b>view state only</b>. Scaling never touches the document's
 * {@code width}, {@code height}, {@code viewBox} or any coordinate inside it — that is the whole
 * contract of this class, and the reason "zoomed in" must never mark a file as modified.</p>
 *
 * <p>Two ways the transform can be applied, because the right one differs by content:</p>
 * <ul>
 *   <li>{@link TransformSink} — the SVG path. The transform is handed to the renderer (a CSS
 *       transform on the SVG element inside the WebView), so zooming stays <b>vector</b>: the
 *       drawing is re-rasterised at the new scale instead of a bitmap being blown up.</li>
 *   <li>View transform — the image path. {@code setScaleX/Y} on the content view, which is what an
 *       ImageView needs and what a raster image can do anyway.</li>
 * </ul>
 *
 * <p>Gestures: pinch to zoom about the pinch centre, one finger to pan, double tap to zoom about the
 * tapped point and back again. Buttons call {@link #zoomIn}/{@link #zoomOut} (about the viewport
 * centre) and {@link #fitToWindow}. Panning is clamped so the artwork can never be dragged
 * completely out of sight, which is what makes "fit to window" a recovery rather than a rescue.</p>
 */
public class SvgViewport extends FrameLayout {

    /** Zoom limits, as multiples of the fitted scale. One place, so tuning is one edit. */
    public static final float MIN_ZOOM_FACTOR = 0.1f;
    public static final float MAX_ZOOM_FACTOR = 20f;
    /** Each button press multiplies the current zoom by this. */
    private static final float STEP = 1.4f;
    /** Double tap zooms to this multiple of the fitted scale. */
    private static final float DOUBLE_TAP_FACTOR = 2.5f;
    /** How much of the viewport must stay covered by content while panning. */
    private static final float MIN_VISIBLE_FRACTION = 0.15f;
    /** Two taps closer than this (in time and space) are a double tap. */
    private static final long DOUBLE_TAP_TIMEOUT_MS = 320L;
    private static final float TAP_SLOP_PX = 60f;

    /** Where the transform goes when the renderer can apply it itself (the SVG WebView). */
    public interface TransformSink {
        /**
         * @param scale uniform scale relative to the fitted size
         * @param dx    translation in viewport pixels
         * @param dy    translation in viewport pixels
         */
        void onTransform(float scale, float dx, float dy);
    }

    public interface OnViewportListener {
        /** Called whenever the zoom factor changes, relative to the fitted size. */
        void onZoomChanged(float factor);
        /** Called when the view state returns to (or leaves) the fitted state. */
        void onFitStateChanged(boolean fitted);
    }

    private View content;
    private TransformSink sink;
    /** When true, scale {@link #viewTarget} itself (raster images). SVG uses {@link #sink} instead. */
    private boolean useViewTransform;
    private View viewTarget;
    /** Uniform scale applied to the content; 1 means "at the fitted size". */
    private float scale = 1f;
    private float minScale = MIN_ZOOM_FACTOR;
    private float maxScale = MAX_ZOOM_FACTOR;
    private float translateX = 0f;
    private float translateY = 0f;
    /** True while the user has zoomed or panned away from the fitted state. */
    private boolean userAdjusted = false;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private OnViewportListener listener;
    /**
     * True for content this viewport owns (SVG, Mermaid, images): every touch is taken, including
     * the initial DOWN. False for pages that scroll themselves (HTML, Markdown, CSV), where the
     * viewport must stay out of the way entirely.
     *
     * <p>Taking DOWN matters. A child that consumes the first event (a WebView always does) leaves
     * {@link ScaleGestureDetector} without an anchor, so a real pinch produces no scale at all —
     * the gesture looks implemented but does nothing on a device.</p>
     */
    private boolean interactive = false;

    public SvgViewport(Context context) {
        super(context);
        init(context);
    }

    public SvgViewport(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SvgViewport(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setClipChildren(true);
        setClipToPadding(true);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                // Zoom about the pinch centre, so the point between the fingers stays put.
                zoomAbout(detector.getFocusX(), detector.getFocusY(), detector.getScaleFactor());
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                clampTranslation();
                applyTransform();
                notifyZoom();
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (scaleDetector.isInProgress()) return false;
                // One finger pans the artwork instead of scrolling the page behind it.
                translateX -= distanceX;
                translateY -= distanceY;
                clampTranslation();
                applyTransform();
                return true;
            }

            // Double tap is handled in onTouchEvent, not here: GestureDetector's double-tap window
            // depends on platform timing constants and silently never fires for some synthetic and
            // accessibility-generated streams, which made the gesture look unimplemented.

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // A tap must not activate a link inside the artwork: panning and pinching are the
                // gestures this surface owns. Links open through the explicit action instead.
                return true;
            }
        });
    }

    /** Attach the view that carries the content. */
    public void setContent(View view) {
        if (content != null && content.getParent() == this) {
            removeView(content);
        }
        content = view;
        if (view != null && view.getParent() != this) {
            addView(view, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        reset();
    }

    public View getContent() {
        return content;
    }

    /**
     * Let the renderer apply the transform itself (keeps SVG vector-sharp). When set, the content
     * view is left at scale 1 and only the sink is called.
     */
    public void setTransformSink(TransformSink sink) {
        this.sink = sink;
        reset();
    }

    /**
     * Raster images cannot take a CSS transform inside the WebView. Scale this view instead.
     * Turning it off restores the target to identity so a later SVG page is not also zoomed.
     */
    public void setUseViewTransform(boolean use) {
        this.useViewTransform = use;
        if (!use && viewTarget != null) {
            viewTarget.setScaleX(1f);
            viewTarget.setScaleY(1f);
            viewTarget.setTranslationX(0f);
            viewTarget.setTranslationY(0f);
        }
        applyTransform();
    }

    /** The view that receives the raster transform. Does not reparent it. */
    public void setViewTransformTarget(View view) {
        this.viewTarget = view;
        applyTransform();
    }

    public void setOnViewportListener(OnViewportListener listener) {
        this.listener = listener;
    }

    /**
     * Whether this viewport owns the gesture stream. Turn it on only for content that must not
     * scroll, because it stops the child from receiving touches at all.
     */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            scale = 1f;
            translateX = 0f;
            translateY = 0f;
            userAdjusted = false;
            applyTransform();
        }
    }

    public boolean isInteractive() {
        return interactive;
    }

    // --- geometry ------------------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == oldw && h == oldh) return;
        // A rotation or a fullscreen toggle changes the available area. A user who has not zoomed
        // gets a fresh fit; a user who has keeps their centre and magnification, re-clamped so the
        // artwork cannot end up outside the new viewport.
        if (!userAdjusted) {
            reset();
        } else {
            clampTranslation();
            applyTransform();
        }
    }

    /** Return to the fitted, centred state. */
    public void reset() {
        scale = 1f;
        translateX = 0f;
        translateY = 0f;
        userAdjusted = false;
        applyTransform();
        notifyZoom();
    }

    /** Fit the whole artwork and centre it — the one action that always recovers the drawing. */
    public void fitToWindow() {
        reset();
    }

    public boolean isFitted() {
        return !userAdjusted;
    }

    /** Current zoom as a multiple of the fitted size. */
    public float getZoomFactor() {
        return scale;
    }

    public void zoomIn() {
        zoomTo(scale * STEP, getWidth() / 2f, getHeight() / 2f);
    }

    public void zoomOut() {
        zoomTo(scale / STEP, getWidth() / 2f, getHeight() / 2f);
    }

    /** Zoom to an absolute factor about a point in viewport coordinates. */
    public void zoomTo(float target, float pivotX, float pivotY) {
        if (scale <= 0) return;
        float clamped = Math.max(minScale, Math.min(maxScale, target));
        zoomAbout(pivotX, pivotY, clamped / scale);
    }

    /** Multiply the zoom about a point, keeping that point fixed on screen. */
    private void zoomAbout(float pivotX, float pivotY, float factor) {
        if (factor <= 0) return;
        float target = Math.max(minScale, Math.min(maxScale, scale * factor));
        float applied = target / scale;
        if (Math.abs(applied - 1f) < 0.0001f) return;

        // Keep the pivot stationary: the offset from the pivot scales with the zoom.
        translateX = pivotX - (pivotX - translateX) * applied;
        translateY = pivotY - (pivotY - translateY) * applied;
        scale = target;
        userAdjusted = Math.abs(scale - 1f) > 0.001f || translateX != 0f || translateY != 0f;
        clampTranslation();
        applyTransform();
        notifyZoom();
    }

    /**
     * Keep at least {@link #MIN_VISIBLE_FRACTION} of the content inside the viewport on each axis.
     * Without this, a few fast drags leave the user staring at empty space with no way back except
     * the fit button.
     */
    private void clampTranslation() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float contentW = w * scale;
        float contentH = h * scale;
        float minVisibleX = Math.min(w, contentW) * MIN_VISIBLE_FRACTION;
        float minVisibleY = Math.min(h, contentH) * MIN_VISIBLE_FRACTION;

        float maxTx = w - minVisibleX;
        float minTx = minVisibleX - contentW;
        translateX = Math.max(minTx, Math.min(maxTx, translateX));

        float maxTy = h - minVisibleY;
        float minTy = minVisibleY - contentH;
        translateY = Math.max(minTy, Math.min(maxTy, translateY));
    }

    private void applyTransform() {
        if (useViewTransform && viewTarget != null) {
            viewTarget.setPivotX(0f);
            viewTarget.setPivotY(0f);
            viewTarget.setScaleX(scale);
            viewTarget.setScaleY(scale);
            viewTarget.setTranslationX(translateX);
            viewTarget.setTranslationY(translateY);
            return;
        }
        if (sink != null) {
            sink.onTransform(scale, translateX, translateY);
            return;
        }
        if (content == null) return;
        content.setPivotX(0f);
        content.setPivotY(0f);
        content.setScaleX(scale);
        content.setScaleY(scale);
        content.setTranslationX(translateX);
        content.setTranslationY(translateY);
    }

    private void notifyZoom() {
        if (listener != null) {
            listener.onZoomChanged(scale);
            listener.onFitStateChanged(isFitted());
        }
    }

    // --- gestures ------------------------------------------------------------------------------

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!interactive) return false;
        // Every event, including DOWN: a pinch needs its anchor, and a pan must not become a page
        // scroll. For content that scrolls itself this viewport is simply not in the chain.
        return true;
    }

    /**
     * Whether the last gesture was a tap that should be forwarded to the content.
     *
     * <p>Intercepting every event is what makes pinch and pan work, but it also meant a drawing's
     * own controls — a play/pause button, a speed toggle — could never be pressed: the viewport
     * swallowed the tap. A tap that never moved is now replayed into the WebView as a synthetic
     * click, so interactive artwork stays interactive while pan and pinch still belong to the
     * viewport.</p>
     */
    public interface OnTapListener {
        /** A tap at (x, y) in viewport coordinates, with no movement in between. */
        void onTap(float x, float y);
    }

    private OnTapListener tapListener;

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    /** Double-tap bookkeeping, tracked here so the gesture does not depend on platform timing. */
    private long lastTapTime = 0L;
    private float lastTapX = 0f;
    private float lastTapY = 0f;
    /** A tap that becomes part of a double tap must not also be treated as a pan start. */
    private boolean gestureMoved = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactive) return super.onTouchEvent(event);
        // ACTION_DOWN must reach both detectors before any second pointer: that event is what gives
        // ScaleGestureDetector its anchor. Without it a pinch silently produces no scale at all.
        boolean handled = scaleDetector.onTouchEvent(event);
        handled |= gestureDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // A second finger means this is a pinch, never a tap.
            gestureMoved = true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1 && !gestureMoved) {
                float dx = event.getX() - lastTapX;
                float dy = event.getY() - lastTapY;
                if (dx * dx + dy * dy > TAP_SLOP_PX * TAP_SLOP_PX) gestureMoved = true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (!gestureMoved && event.getPointerCount() == 1) {
                long now = event.getEventTime();
                if (now - lastTapTime <= DOUBLE_TAP_TIMEOUT_MS
                        && Math.abs(event.getX() - lastTapX) <= TAP_SLOP_PX
                        && Math.abs(event.getY() - lastTapY) <= TAP_SLOP_PX) {
                    onDoubleTap(event.getX(), event.getY());
                    lastTapTime = 0L;
                } else {
                    // A single tap belongs to the content: artwork with its own buttons (play,
                    // pause, a speed toggle) must stay usable. The viewport only owns pan and
                    // pinch, so it forwards the tap instead of swallowing it.
                    if (tapListener != null) tapListener.onTap(event.getX(), event.getY());
                    lastTapTime = now;
                    lastTapX = event.getX();
                    lastTapY = event.getY();
                }
            }
            gestureMoved = false;
            clampTranslation();
            applyTransform();
            notifyZoom();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            gestureMoved = false;
            clampTranslation();
            applyTransform();
            notifyZoom();
        }
        return handled || super.onTouchEvent(event);
    }

    /** Zoom in about the tapped point, or return to the fitted view when already zoomed. */
    private void onDoubleTap(float x, float y) {
        if (isFitted()) {
            zoomTo(DOUBLE_TAP_FACTOR, x, y);
        } else {
            fitToWindow();
        }
    }

    // --- test hooks ----------------------------------------------------------------------------

    /** Where the content currently sits on screen, in viewport coordinates. */
    public RectF contentBoundsForTest() {
        float w = getWidth();
        float h = getHeight();
        return new RectF(translateX, translateY, translateX + w * scale, translateY + h * scale);
    }

    public Matrix transformForTest() {
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(translateX, translateY);
        return m;
    }

    public boolean isUserAdjustedForTest() {
        return userAdjusted;
    }

    public void setZoomLimitsForTest(float min, float max) {
        minScale = min;
        maxScale = max;
    }

    /** Drive a pinch without a real second finger; used by instrumented tests. */
    public void pinchForTest(float factor, float pivotX, float pivotY) {
        zoomAbout(pivotX, pivotY, factor);
    }

    /** Drive a pan; used by instrumented tests. */
    public void panForTest(float dx, float dy) {
        translateX += dx;
        translateY += dy;
        clampTranslation();
        applyTransform();
        notifyZoom();
    }
}
