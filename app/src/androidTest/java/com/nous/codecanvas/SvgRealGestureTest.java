package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.ui.SvgViewport;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A big SVG must zoom with real fingers.
 *
 * <p>The reported failure was "那种大的 svg 依然无法缩放". The earlier gesture test drove
 * {@code viewport.dispatchTouchEvent} directly, which bypasses the parent's intercept logic — so it
 * passed while the real path (a WebView child that consumes touches) did nothing. These cases send
 * events through {@code Activity.dispatchTouchEvent}, i.e. the same route a finger takes.</p>
 */
public class SvgRealGestureTest extends InstrumentationTestCase {

    /** A deliberately large document: the size that made the reported bug visible. */
    private static String bigSvg() {
        StringBuilder sb = new StringBuilder(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 4000 3000\" width=\"100%\" height=\"100%\">");
        sb.append("<rect x=\"0\" y=\"0\" width=\"4000\" height=\"3000\" fill=\"#eef4ff\"/>");
        for (int i = 0; i < 400; i++) {
            int x = (i * 97) % 3900;
            int y = (i * 53) % 2900;
            sb.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"60\" height=\"40\" fill=\"#2456C6\" opacity=\"0.5\"/>");
        }
        sb.append("<circle cx=\"2000\" cy=\"1500\" r=\"900\" fill=\"#6D3FC0\" opacity=\"0.35\"/>");
        sb.append("</svg>");
        return sb.toString();
    }

    private Context context;
    private DocumentRepository repository;
    private EditorActivity editor;
    private String docId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
        docId = "bigsvg-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(docId, "big.svg", bigSvg(), System.currentTimeMillis());
        doc.setKindName("SVG");
        repository.saveDocument(doc);
        Intent i = new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, docId)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        editor = (EditorActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(2500);
    }

    @Override
    protected void tearDown() throws Exception {
        repository.deleteDocument(docId);
        getInstrumentation().runOnMainSync(() -> {
            if (!editor.isFinishing()) editor.finish();
        });
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    /** Two fingers moving apart, dispatched through the Activity exactly as the system does. */
    private void pinchThroughActivity(final float startSpan, final float endSpan, final int steps)
            throws Throwable {
        final int cx = editor.getViewportForTest().getWidth() / 2;
        final int cy = editor.getViewportForTest().getHeight() / 2;
        runTestOnUiThread(new Runnable() { public void run() {
            long down = android.os.SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[2];
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
            for (int i = 0; i < 2; i++) {
                props[i] = new MotionEvent.PointerProperties();
                props[i].id = i;
                props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                coords[i] = new MotionEvent.PointerCoords();
                coords[i].pressure = 1f;
                coords[i].size = 1f;
            }
            coords[0].x = cx - startSpan / 2; coords[0].y = cy;
            coords[1].x = cx + startSpan / 2; coords[1].y = cy;

            editor.dispatchTouchEvent(MotionEvent.obtain(down, down,
                    MotionEvent.ACTION_DOWN, 1, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
            editor.dispatchTouchEvent(MotionEvent.obtain(down, down + 10,
                    MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
            for (int step = 1; step <= steps; step++) {
                float span = startSpan + (endSpan - startSpan) * step / steps;
                coords[0].x = cx - span / 2;
                coords[1].x = cx + span / 2;
                editor.dispatchTouchEvent(MotionEvent.obtain(down, down + 10 + step * 16,
                        MotionEvent.ACTION_MOVE, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
            }
            editor.dispatchTouchEvent(MotionEvent.obtain(down, down + 10 + steps * 16 + 20,
                    MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
            editor.dispatchTouchEvent(MotionEvent.obtain(down, down + 10 + steps * 16 + 40,
                    MotionEvent.ACTION_UP, 1, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
        }});
        getInstrumentation().waitForIdleSync();
        Thread.sleep(200);
    }

    private void dragThroughActivity(final float dx, final float dy, final int steps) throws Throwable {
        final int cx = editor.getViewportForTest().getWidth() / 2;
        final int cy = editor.getViewportForTest().getHeight() / 2;
        runTestOnUiThread(new Runnable() { public void run() {
            long t = android.os.SystemClock.uptimeMillis();
            editor.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0));
            for (int step = 1; step <= steps; step++) {
                editor.dispatchTouchEvent(MotionEvent.obtain(t, t + step * 16,
                        MotionEvent.ACTION_MOVE, cx + dx * step / steps, cy + dy * step / steps, 0));
            }
            editor.dispatchTouchEvent(MotionEvent.obtain(t, t + steps * 16 + 20,
                    MotionEvent.ACTION_UP, cx + dx, cy + dy, 0));
        }});
        getInstrumentation().waitForIdleSync();
        Thread.sleep(200);
    }

    public void testBigSvgZoomsWithRealPinch() throws Throwable {
        final SvgViewport viewport = editor.getViewportForTest();
        runTestOnUiThread(() -> {
            assertTrue("视口必须处于可交互状态（这是缩放的先决条件）", viewport.isInteractive());
            assertEquals("首次打开必须已适应窗口", 1.0f, viewport.getZoomFactor(), 0.01f);
        });

        pinchThroughActivity(300f, 900f, 8);

        runTestOnUiThread(() -> {
            float zoom = viewport.getZoomFactor();
            android.util.Log.i("CCZOOM", "after pinch zoom=" + zoom);
            assertTrue("真实双指张开必须放大，实际 " + zoom, zoom > 1.5f);
        });
    }

    public void testBigSvgPansWithRealDrag() throws Throwable {
        final SvgViewport viewport = editor.getViewportForTest();
        pinchThroughActivity(300f, 900f, 6);
        final float before = viewport.contentBoundsForTest().left;
        dragThroughActivity(-260f, -160f, 6);
        runTestOnUiThread(() -> {
            float after = viewport.contentBoundsForTest().left;
            android.util.Log.i("CCZOOM", "pan before=" + before + " after=" + after);
            assertTrue("单指拖动必须真的平移", Math.abs(after - before) > 20f);
        });
    }

    public void testDoubleTapZoomsAndSecondDoubleTapRestores() throws Throwable {
        final SvgViewport viewport = editor.getViewportForTest();
        final int cx = viewport.getWidth() / 2;
        final int cy = viewport.getHeight() / 2;
        runTestOnUiThread(() -> {
            long t = android.os.SystemClock.uptimeMillis();
            for (int i = 0; i < 2; i++) {
                long at = t + i * 60L;
                editor.dispatchTouchEvent(MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, cx, cy, 0));
                editor.dispatchTouchEvent(MotionEvent.obtain(at, at + 30, MotionEvent.ACTION_UP, cx, cy, 0));
            }
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(600);
        runTestOnUiThread(() -> assertTrue("双击必须放大，实际 " + viewport.getZoomFactor(),
                viewport.getZoomFactor() > 1.5f));

        runTestOnUiThread(() -> {
            long t = android.os.SystemClock.uptimeMillis();
            for (int i = 0; i < 2; i++) {
                long at = t + i * 60L;
                editor.dispatchTouchEvent(MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, cx, cy, 0));
                editor.dispatchTouchEvent(MotionEvent.obtain(at, at + 30, MotionEvent.ACTION_UP, cx, cy, 0));
            }
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(600);
        runTestOnUiThread(() -> assertEquals("再次双击必须回到适应窗口", 1.0f,
                viewport.getZoomFactor(), 0.01f));
    }

    /** The zoom must reach the SVG itself, not just the view's transform state. */
    public void testZoomActuallyReachesTheSvgContent() throws Throwable {
        final SvgViewport viewport = editor.getViewportForTest();
        pinchThroughActivity(300f, 900f, 8);
        final WebView web = editor.findViewById(R.id.web_preview);
        assertNotNull(web);
        final AtomicReference<String> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "(function(){var s=document.getElementById('cc-stage');"
                        + "if(!s) return 'NO_STAGE';"
                        + "return getComputedStyle(s).transform;})()",
                value -> { result.set(value); latch.countDown(); }));
        latch.await(5, TimeUnit.SECONDS);
        String transform = result.get();
        android.util.Log.i("CCZOOM", "stage transform=" + transform);
        assertNotNull(transform);
        assertTrue("SVG 内容层必须真的被缩放，实际 transform=" + transform,
                transform != null && !transform.contains("none") && !transform.contains("matrix(1, 0, 0, 1, 0, 0)"));
    }

    public void testFitButtonRecoversAfterWildPan() throws Throwable {
        final SvgViewport viewport = editor.getViewportForTest();
        pinchThroughActivity(300f, 900f, 6);
        dragThroughActivity(-2000f, -1500f, 4);
        runTestOnUiThread(() -> editor.findViewById(R.id.btn_viewport_fit).performClick());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            assertEquals("适应窗口必须回到 1.0", 1.0f, viewport.getZoomFactor(), 0.01f);
            assertTrue("适应窗口后必须标记为已适应", viewport.isFitted());
        });
    }
}
