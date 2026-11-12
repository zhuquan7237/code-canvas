package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.graphics.RectF;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.ui.SvgViewport;

/**
 * A genuinely large SVG must zoom and pan on the real screen.
 *
 * <p>Reported twice as "大的 svg 依然无法缩放". The earlier gesture tests used a small synthetic
 * document and passed, so they were not exercising the real case. This one opens a 4000×3000
 * drawing — the kind of file that actually gets reported — drives real touch streams at the
 * viewport, and then reads the scale back out of the page itself, not just out of the viewport
 * object. If the transform never reaches the WebView, the test fails.</p>
 */
public class BigSvgZoomTest extends InstrumentationTestCase {

    private static final int W = 4000;
    private static final int H = 3000;

    private static String bigSvg() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(W).append(' ').append(H)
                .append("\" width=\"").append(W).append("\" height=\"").append(H).append("\">");
        sb.append("<rect width=\"").append(W).append("\" height=\"").append(H).append("\" fill=\"#ffffff\"/>");
        // Detail that only becomes legible when zoomed: that is the point of zooming a big drawing.
        for (int i = 0; i < 60; i++) {
            int x = 100 + (i % 10) * 380;
            int y = 100 + (i / 10) * 470;
            sb.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"300\" height=\"380\" fill=\"none\" stroke=\"#2f6fdb\" stroke-width=\"6\"/>");
            sb.append("<text x=\"").append(x + 20).append("\" y=\"").append(y + 60)
                    .append("\" font-size=\"56\" fill=\"#111\">cell ").append(i).append("</text>");
        }
        sb.append("<circle cx=\"").append(W / 2).append("\" cy=\"").append(H / 2)
                .append("\" r=\"600\" fill=\"#e8b93b\" fill-opacity=\"0.35\"/>");
        sb.append("</svg>");
        return sb.toString();
    }

    private EditorActivity editor;
    private String docId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context c = getInstrumentation().getTargetContext();
        docId = "bigsvg-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(docId, "big.svg", bigSvg(), System.currentTimeMillis());
        doc.setKindName("SVG");
        new DocumentRepository(c).saveDocument(doc);
        Intent i = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, docId)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        editor = (EditorActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(1500);
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        new DocumentRepository(getInstrumentation().getTargetContext()).deleteDocument(docId);
        getInstrumentation().runOnMainSync(() -> {
            if (!editor.isFinishing()) editor.finish();
        });
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    private SvgViewport viewport() {
        return editor.findViewById(R.id.preview_viewport);
    }

    /**
     * Read the scale the page actually applied to its transform layer.
     *
     * <p>Parsed with indexOf rather than a regex: the pattern has to survive two levels of string
     * escaping (Java source → JS source), and a mangled escape silently returned the fallback value,
     * which made a working zoom look broken.</p>
     */
    private float pageScale() throws Exception {
        final WebView web = editor.findViewById(R.id.web_preview);
        final String[] out = new String[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "(function(){var s=document.getElementById('cc-stage');"
                        + "if(!s)return 'none';"
                        + "var t=getComputedStyle(s).transform;"
                        + "if(!t||t==='none')return '1';"
                        + "var i=t.indexOf('matrix(');"
                        + "if(i<0)return '1';"
                        + "var body=t.substring(i+7,t.indexOf(')',i));"
                        + "return body.split(',')[0];})()",
                v -> {
                    out[0] = v;
                    latch.countDown();
                }));
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull("必须能从页面读到变换", out[0]);
        String raw = out[0].replace("\"", "").trim();
        android.util.Log.i("CCBIG", "raw page transform scale=" + raw);
        if ("none".equals(raw) || raw.isEmpty()) return 1f;
        return Float.parseFloat(raw);
    }

    private void sendPinch(float cx, float cy, float fromSpan, float toSpan, int steps) throws Throwable {
        final SvgViewport vp = viewport();
        long down = SystemClock.uptimeMillis();
        float startHalf = fromSpan / 2f;

        // DOWN with one finger first: that event is what gives ScaleGestureDetector its anchor.
        MotionEvent e0 = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 1,
                new MotionEvent.PointerProperties[]{props(0)},
                new MotionEvent.PointerCoords[]{coords(cx - startHalf, cy)},
                0, 0, 1f, 1f, 0, 0, 0, 0);
        getInstrumentation().runOnMainSync(() -> vp.onTouchEvent(e0));
        e0.recycle();

        // Second finger lands.
        MotionEvent e1 = MotionEvent.obtain(down, down, MotionEvent.ACTION_POINTER_DOWN
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
                new MotionEvent.PointerProperties[]{props(0), props(1)},
                new MotionEvent.PointerCoords[]{coords(cx - startHalf, cy), coords(cx + startHalf, cy)},
                0, 0, 1f, 1f, 0, 0, 0, 0);
        getInstrumentation().runOnMainSync(() -> vp.onTouchEvent(e1));
        e1.recycle();

        for (int s = 1; s <= steps; s++) {
            float half = (fromSpan + (toSpan - fromSpan) * s / steps) / 2f;
            MotionEvent m = MotionEvent.obtain(down, down + s * 16L, MotionEvent.ACTION_MOVE, 2,
                    new MotionEvent.PointerProperties[]{props(0), props(1)},
                    new MotionEvent.PointerCoords[]{coords(cx - half, cy), coords(cx + half, cy)},
                    0, 0, 1f, 1f, 0, 0, 0, 0);
            getInstrumentation().runOnMainSync(() -> vp.onTouchEvent(m));
            m.recycle();
        }

        float endHalf = toSpan / 2f;
        MotionEvent up = MotionEvent.obtain(down, down + (steps + 1) * 16L, MotionEvent.ACTION_UP, 1,
                new MotionEvent.PointerProperties[]{props(0)},
                new MotionEvent.PointerCoords[]{coords(cx - endHalf, cy)},
                0, 0, 1f, 1f, 0, 0, 0, 0);
        getInstrumentation().runOnMainSync(() -> vp.onTouchEvent(up));
        up.recycle();
        getInstrumentation().waitForIdleSync();
    }

    private static MotionEvent.PointerProperties props(int id) {
        MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
        p.id = id;
        p.toolType = MotionEvent.TOOL_TYPE_FINGER;
        return p;
    }

    private static MotionEvent.PointerCoords coords(float x, float y) {
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = x;
        c.y = y;
        c.pressure = 1f;
        c.size = 1f;
        return c;
    }

    /** The reported bug: a big drawing ignores a pinch. */
    public void testPinchZoomsABigSvg() throws Throwable {
        float before = pageScale();
        android.util.Log.i("CCBIG", "scale before pinch=" + before);

        sendPinch(600, 1200, 300, 900, 12);
        Thread.sleep(400);
        getInstrumentation().waitForIdleSync();

        float after = pageScale();
        android.util.Log.i("CCBIG", "scale after pinch=" + after + " viewportScale=" + viewport().transformForTest().toShortString());
        assertTrue("双指放大后页面里的变换必须真的变大（之前 " + before + "，之后 " + after + "）",
                after > before * 1.3f);
    }

    /** Zoom buttons must move the page too, not just an internal number. */
    public void testZoomInButtonMovesThePage() throws Throwable {
        float before = pageScale();
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_viewport_zoom_in).performClick());
        getInstrumentation().waitForIdleSync();
        Thread.sleep(300);
        float after = pageScale();
        android.util.Log.i("CCBIG", "button zoom " + before + " -> " + after);
        assertTrue("放大按钮必须改变页面变换", after > before * 1.1f);

        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_viewport_fit).performClick());
        getInstrumentation().waitForIdleSync();
        Thread.sleep(300);
        float fitted = pageScale();
        android.util.Log.i("CCBIG", "after fit=" + fitted);
        assertTrue("适应窗口必须回到 1.0 附近，实际 " + fitted, Math.abs(fitted - 1f) < 0.15f);
    }

    /** Dragging must move the drawing, and fit must bring it back. */
    public void testDragPansAndFitRecovers() throws Throwable {
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_viewport_zoom_in).performClick());
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_viewport_zoom_in).performClick());
        getInstrumentation().waitForIdleSync();
        Thread.sleep(300);

        final SvgViewport vp = viewport();
        RectF before = vp.contentBoundsForTest();
        long t = SystemClock.uptimeMillis();
        getInstrumentation().runOnMainSync(() -> {
            vp.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 500, 1200, 0));
            for (int i = 1; i <= 8; i++) {
                vp.onTouchEvent(MotionEvent.obtain(t, t + i * 16L, MotionEvent.ACTION_MOVE, 500 - i * 25, 1200 - i * 20, 0));
            }
            vp.onTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, 300, 1040, 0));
        });
        getInstrumentation().waitForIdleSync();
        RectF after = vp.contentBoundsForTest();
        android.util.Log.i("CCBIG", "pan before=" + before + " after=" + after);
        assertTrue("单指拖动必须移动图形", Math.abs(after.left - before.left) > 20f
                || Math.abs(after.top - before.top) > 20f);

        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_viewport_fit).performClick());
        getInstrumentation().waitForIdleSync();
        Thread.sleep(300);
        RectF fitted = vp.contentBoundsForTest();
        android.util.Log.i("CCBIG", "fitted bounds=" + fitted);
        assertTrue("适应窗口必须把图形找回来", Math.abs(fitted.left) < 2f && Math.abs(fitted.top) < 2f);
    }
}
