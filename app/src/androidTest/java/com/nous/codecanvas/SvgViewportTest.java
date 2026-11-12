package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.graphics.RectF;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.ui.SvgViewport;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The SVG viewport: whole-drawing fit, non-zero origins, gestures, reset, fullscreen, rotation,
 * and the promise that none of it touches the file.
 *
 * <p>Measured with {@code getScreenCTM()} rather than by eye: mapping the viewBox corners into
 * screen coordinates says exactly whether the artwork is inside the viewport, which is the
 * difference between "looks fine" and "the corners are visible".</p>
 */
public class SvgViewportTest extends InstrumentationTestCase {

    private Context context;
    private DocumentRepository repository;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
    }

    private EditorActivity open(String name, String svg) throws Exception {
        String id = "svg-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(id, name, svg, System.currentTimeMillis());
        doc.setKindName("SVG");
        repository.saveDocument(doc);
        Intent intent = new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, id)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        return activity;
    }

    private void close(EditorActivity activity) throws Exception {
        final String id = activity.getCurrentDocumentForTest().getId();
        getInstrumentation().runOnMainSync(() -> {
            if (!activity.isFinishing()) activity.finish();
        });
        getInstrumentation().waitForIdleSync();
        repository.deleteDocument(id);
    }

    private String evaluate(WebView web, String js) throws Exception {
        final AtomicReference<String> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(js, value -> {
            result.set(value);
            latch.countDown();
        }));
        latch.await(6, TimeUnit.SECONDS);
        String raw = result.get();
        if (raw == null) return null;
        return raw.replace("\\\"", "\"").replaceAll("^\"|\"$", "");
    }

    private void waitForPage(WebView web) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if ("complete".equals(evaluate(web, "document.readyState"))) {
                Thread.sleep(500);
                return;
            }
            Thread.sleep(250);
        }
        fail("preview never loaded");
    }

    /** The viewBox corners in screen pixels, plus the viewport size. */
    private double[] geometry(WebView web) throws Exception {
        String js = "(function(){var s=document.querySelector('svg');if(!s||!s.getScreenCTM)return 'NO_SVG';"
                + "var m=s.getScreenCTM(),p=s.createSVGPoint(),vb=s.viewBox.baseVal;"
                + "function t(x,y){p.x=x;p.y=y;var r=p.matrixTransform(m);return [r.x,r.y];}"
                + "var a=t(vb.x,vb.y),b=t(vb.x+vb.width,vb.y+vb.height);"
                + "return JSON.stringify({w:window.innerWidth,h:window.innerHeight,"
                + "x0:a[0],y0:a[1],x1:b[0],y1:b[1]});})()";
        String raw = evaluate(web, js);
        assertNotNull("could not read geometry: " + raw, raw);
        assertTrue("no svg root: " + raw, raw.startsWith("{"));
        org.json.JSONObject o = new org.json.JSONObject(raw);
        return new double[]{
                o.getDouble("w"), o.getDouble("h"),
                o.getDouble("x0"), o.getDouble("y0"),
                o.getDouble("x1"), o.getDouble("y1")
        };
    }

    private void assertWholeArtworkVisible(String what, WebView web) throws Exception {
        double[] g = geometry(web);
        // A 1px tolerance absorbs sub-pixel rounding in the CTM.
        assertTrue(what + "：整幅必须完整可见，实际 " + java.util.Arrays.toString(g),
                g[2] >= -1 && g[3] >= -1 && g[4] <= g[0] + 1 && g[5] <= g[1] + 1);
    }

    // ------------------------------------------------------------------ default fit

    public void testWideArtworkWithSliceIsShownWhole() throws Throwable {
        // The authoring pattern that used to crop a wide drawing down to its middle band.
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1280 800\" width=\"100%\""
                + " height=\"100%\" preserveAspectRatio=\"xMidYMid slice\""
                + " style=\"display:block;width:100vw;height:100vh;background:#eef4ff\">"
                + "<rect x=\"0\" y=\"0\" width=\"40\" height=\"40\" fill=\"#f00\"/>"
                + "<rect x=\"1240\" y=\"760\" width=\"40\" height=\"40\" fill=\"#0a0\"/>"
                + "<circle cx=\"640\" cy=\"400\" r=\"260\" fill=\"#2456C6\"/></svg>";
        EditorActivity activity = open("wide.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            assertWholeArtworkVisible("宽幅 1280×800", web);
        } finally {
            close(activity);
        }
    }

    public void testTallArtworkIsShownWhole() throws Throwable {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 300 2400\">"
                + "<rect x=\"0\" y=\"0\" width=\"300\" height=\"2400\" fill=\"#DCF2E6\"/>"
                + "<rect x=\"0\" y=\"0\" width=\"30\" height=\"30\" fill=\"#B42318\"/>"
                + "<rect x=\"270\" y=\"2370\" width=\"30\" height=\"30\" fill=\"#B42318\"/></svg>";
        EditorActivity activity = open("tall.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            assertWholeArtworkVisible("超高 300×2400", web);
        } finally {
            close(activity);
        }
    }

    public void testNegativeViewBoxOriginIsHonoured() throws Throwable {
        // "viewBox=-200 -120 400 240" does not start at (0,0). Assuming it does shifts the drawing
        // off-centre, and the corner markers are what make that visible.
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-200 -120 400 240\">"
                + "<rect x=\"-200\" y=\"-120\" width=\"400\" height=\"240\" fill=\"#f7fbff\" stroke=\"#2456C6\" stroke-width=\"4\"/>"
                + "<circle cx=\"0\" cy=\"0\" r=\"80\" fill=\"#2456C6\"/></svg>";
        EditorActivity activity = open("negative.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            assertWholeArtworkVisible("负原点 viewBox", web);
            // The drawing must also be centred, not pushed into a corner.
            double[] g = geometry(web);
            double leftGap = g[2];
            double rightGap = g[0] - g[4];
            double topGap = g[3];
            double bottomGap = g[1] - g[5];
            assertTrue("水平必须居中：左 " + leftGap + " 右 " + rightGap,
                    Math.abs(leftGap - rightGap) < 6);
            assertTrue("垂直必须居中：上 " + topGap + " 下 " + bottomGap,
                    Math.abs(topGap - bottomGap) < 6);
        } finally {
            close(activity);
        }
    }

    public void testPercentageSizedSvgStillRendersWithoutCrashing() throws Throwable {
        // No viewBox and percentage width/height: nothing can be measured from the markup, so the
        // requirement is "handle it or say so", never "crash".
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" height=\"100%\">"
                + "<rect x=\"20\" y=\"20\" width=\"200\" height=\"120\" fill=\"#2456C6\"/></svg>";
        EditorActivity activity = open("percent.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            String found = evaluate(web, "document.querySelectorAll('svg').length + ''");
            assertEquals("SVG 必须仍然渲染", "1", found);
            // The wrapper only sets display when it has something to complain about, so an empty
            // value means "no error" — asserting the literal "none" would fail on a healthy page.
            String errorShown = evaluate(web, "document.getElementById('cc-error').style.display || 'none'");
            assertEquals("不得把可渲染的图判成错误", "none", errorShown);
        } finally {
            close(activity);
        }
    }

    public void testFilteredArtworkWithStrokeIsNotClippedAtTheEdge() throws Throwable {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 300 200\">"
                + "<defs><filter id=\"s\" x=\"-30%\" y=\"-30%\" width=\"180%\" height=\"180%\">"
                + "<feDropShadow dx=\"6\" dy=\"8\" stdDeviation=\"6\"/></filter></defs>"
                + "<rect x=\"40\" y=\"40\" width=\"220\" height=\"120\" rx=\"14\" fill=\"#EDE6FA\""
                + " stroke=\"#6D3FC0\" stroke-width=\"6\" filter=\"url(#s)\"/></svg>";
        EditorActivity activity = open("filtered.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            assertWholeArtworkVisible("带阴影与描边", web);
        } finally {
            close(activity);
        }
    }

    // ------------------------------------------------------------------ gestures and reset

    public void testPinchPanAndResetAreRealAndNeverMarkTheFileDirty() throws Throwable {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\">"
                + "<rect width=\"400\" height=\"300\" fill=\"#eef\"/></svg>";
        EditorActivity activity = open("zoom.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            final SvgViewport viewport = activity.getViewportForTest();
            assertNotNull(viewport);

            runTestOnUiThread(() -> assertEquals("首次打开必须处于适应状态", true, viewport.isFitted()));

            // Pinch about the centre: the zoom factor must change and the state must leave "fitted".
            runTestOnUiThread(() -> viewport.pinchForTest(2.0f, viewport.getWidth() / 2f, viewport.getHeight() / 2f));
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertEquals("双指缩放必须生效", 2.0f, viewport.getZoomFactor(), 0.01f);
                assertEquals("缩放后不得仍标记为适应", false, viewport.isFitted());
            });

            // Panning moves the content but must keep part of it on screen.
            runTestOnUiThread(() -> viewport.panForTest(-10000f, -10000f));
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                RectF bounds = viewport.contentBoundsForTest();
                assertTrue("拖动后不得把图完全移出视野", bounds.right > 0 && bounds.bottom > 0);
            });

            // The zoom buttons work from the toolbar.
            runTestOnUiThread(() -> activity.findViewById(R.id.btn_viewport_zoom_in).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> assertTrue("放大按钮必须生效", viewport.getZoomFactor() > 2.0f));

            // "Fit to window" always recovers the whole drawing.
            runTestOnUiThread(() -> activity.findViewById(R.id.btn_viewport_fit).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertEquals("适应窗口必须回到 1.0", 1.0f, viewport.getZoomFactor(), 0.001f);
                assertEquals("适应窗口后必须标记为适应", true, viewport.isFitted());
            });

            // View state is not edit state: none of the above may look like a modification.
            runTestOnUiThread(() -> assertEquals("缩放/平移不得把文件变成未保存修改",
                    false, activity.isDirtyForTest()));
        } finally {
            close(activity);
        }
    }

    public void testZoomIsClampedToTheConfiguredRange() throws Throwable {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><rect/></svg>";
        EditorActivity activity = open("clamp.svg", svg);
        try {
            final SvgViewport viewport = activity.getViewportForTest();
            runTestOnUiThread(() -> {
                for (int i = 0; i < 40; i++) viewport.zoomIn();
                assertTrue("放大必须有上限 " + viewport.getZoomFactor(),
                        viewport.getZoomFactor() <= SvgViewport.MAX_ZOOM_FACTOR + 0.001f);
                for (int i = 0; i < 80; i++) viewport.zoomOut();
                assertTrue("缩小必须有下限 " + viewport.getZoomFactor(),
                        viewport.getZoomFactor() >= SvgViewport.MIN_ZOOM_FACTOR - 0.001f);
            });
        } finally {
            close(activity);
        }
    }

    /** The real touch path: a two-finger pinch must scale, and a one-finger drag must pan. */
    public void testRealTouchGesturesReachTheViewport() throws Throwable {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\">"
                + "<rect width=\"400\" height=\"300\" fill=\"#eef\"/></svg>";
        EditorActivity activity = open("touch.svg", svg);
        try {
            final SvgViewport viewport = activity.getViewportForTest();
            getInstrumentation().waitForIdleSync();
            final int cx = viewport.getWidth() / 2;
            final int cy = viewport.getHeight() / 2;
            assertTrue("viewport must have a size", cx > 0 && cy > 0);

            // --- pinch out: two pointers moving apart ---
            long down = android.os.SystemClock.uptimeMillis();
            final android.view.MotionEvent.PointerProperties[] props =
                    new android.view.MotionEvent.PointerProperties[2];
            final android.view.MotionEvent.PointerCoords[] coords =
                    new android.view.MotionEvent.PointerCoords[2];
            for (int i = 0; i < 2; i++) {
                props[i] = new android.view.MotionEvent.PointerProperties();
                props[i].id = i;
                props[i].toolType = android.view.MotionEvent.TOOL_TYPE_FINGER;
                coords[i] = new android.view.MotionEvent.PointerCoords();
                coords[i].pressure = 1f;
                coords[i].size = 1f;
            }

            runTestOnUiThread(() -> {
                // Start well above the platform's minimum scaling span: a pinch narrower than
                // that is ignored by ScaleGestureDetector, which is why a tiny synthetic pinch
                // produces no scale at all.
                coords[0].x = cx - 220; coords[0].y = cy;
                coords[1].x = cx + 220; coords[1].y = cy;
                viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(down, down,
                        android.view.MotionEvent.ACTION_DOWN, 1, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
                viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(down, down + 10,
                        android.view.MotionEvent.ACTION_POINTER_DOWN
                                | (1 << android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
                // Move the fingers apart in steps, as a real pinch does.
                for (int step = 1; step <= 8; step++) {
                    coords[0].x = cx - 220 - step * 40;
                    coords[1].x = cx + 220 + step * 40;
                    viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(down, down + 10 + step * 16,
                            android.view.MotionEvent.ACTION_MOVE, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
                }
                viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(down, down + 200,
                        android.view.MotionEvent.ACTION_POINTER_UP
                                | (1 << android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
                viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(down, down + 210,
                        android.view.MotionEvent.ACTION_UP, 1, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0));
            });
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> assertTrue(
                    "真实的双指张开必须放大，实际 " + viewport.getZoomFactor(),
                    viewport.getZoomFactor() > 1.2f));

            // --- one-finger drag: must pan, and must not leave the artwork off-screen ---
            final float before = viewport.contentBoundsForTest().left;
            runTestOnUiThread(() -> {
                long t = android.os.SystemClock.uptimeMillis();
                viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t,
                        android.view.MotionEvent.ACTION_DOWN, cx, cy, 0));
                for (int step = 1; step <= 6; step++) {
                    viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + step * 16,
                            android.view.MotionEvent.ACTION_MOVE, cx - step * 20, cy - step * 10, 0));
                }
                viewport.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 120,
                        android.view.MotionEvent.ACTION_UP, cx - 120, cy - 60, 0));
            });
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertTrue("单指拖动必须平移", Math.abs(viewport.contentBoundsForTest().left - before) > 5);
                RectF bounds = viewport.contentBoundsForTest();
                assertTrue("拖动后图形不得完全离开视野", bounds.right > 0 && bounds.bottom > 0);
                assertEquals("手势不得把文件标记为未保存", false, activity.isDirtyForTest());
            });
        } finally {
            close(activity);
        }
    }

    // ------------------------------------------------------------------ fullscreen

    public void testFullscreenEnterExitAndBackKey() throws Throwable {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1280 800\"><circle cx=\"640\" cy=\"400\" r=\"300\"/></svg>";
        EditorActivity activity = open("fs.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            final View header = activity.findViewById(R.id.header_editor);
            final View exit = activity.findViewById(R.id.btn_exit_fullscreen);
            final SvgViewport viewport = activity.getViewportForTest();

            runTestOnUiThread(() -> activity.findViewById(R.id.btn_preview_fullscreen).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertEquals("全屏后必须隐藏顶部工具栏", View.GONE, header.getVisibility());
                assertEquals("全屏后必须出现退出按钮", View.VISIBLE, exit.getVisibility());
                assertEquals("进入全屏必须重新完整显示", true, viewport.isFitted());
            });

            // Back exits fullscreen before it leaves the page.
            runTestOnUiThread(() -> activity.onBackPressed());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertEquals("返回键必须先退出全屏", View.VISIBLE, header.getVisibility());
                assertEquals("退出全屏后按钮隐藏", View.GONE, exit.getVisibility());
                assertFalse("返回键不得直接关闭页面", activity.isFinishing());
            });
            assertWholeArtworkVisible("退出全屏后", web);
        } finally {
            close(activity);
        }
    }

    public void testRotationRefitsWhenTheUserHasNotZoomed() throws Throwable {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1280 800\"><circle cx=\"640\" cy=\"400\" r=\"300\"/></svg>";
        EditorActivity activity = open("rotate.svg", svg);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            final SvgViewport viewport = activity.getViewportForTest();
            runTestOnUiThread(() -> assertEquals(true, viewport.isFitted()));

            // Simulate the rotation's effect on the viewport: the size changes.
            runTestOnUiThread(() -> viewport.measure(
                    View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)));
            runTestOnUiThread(() -> viewport.layout(0, 0, 900, 400));
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> assertEquals("未手动缩放时旋转后必须重新适应", true, viewport.isFitted()));
        } finally {
            close(activity);
        }
    }

    // ------------------------------------------------------------------ source protection

    public void testViewportOperationsNeverChangeTheDocument() throws Throwable {
        String original = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"10 20 400 300\">"
                + "<rect x=\"10\" y=\"20\" width=\"400\" height=\"300\" fill=\"#eef\"/></svg>";
        EditorActivity activity = open("protect.svg", original);
        try {
            final SvgViewport viewport = activity.getViewportForTest();
            runTestOnUiThread(() -> {
                viewport.pinchForTest(3f, 100, 100);
                viewport.panForTest(-50, -60);
                viewport.fitToWindow();
                viewport.zoomIn();
                viewport.zoomOut();
            });
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                String text = ((android.widget.EditText) activity.findViewById(R.id.edit_code))
                        .getText().toString();
                assertEquals("缩放/平移/复位不得改动源码", original, text);
                assertEquals("不得标记为未保存", false, activity.isDirtyForTest());
                assertEquals("不得改动 viewBox", true, text.contains("viewBox=\"10 20 400 300\""));
            });
        } finally {
            close(activity);
        }
    }

    public void testFitToContentIsPreviewOnlyAndKeepsClipSemantics() throws Throwable {
        String original = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 300 200\">"
                + "<defs><clipPath id=\"c\"><circle cx=\"150\" cy=\"100\" r=\"70\"/></clipPath></defs>"
                + "<rect x=\"40\" y=\"30\" width=\"220\" height=\"140\" fill=\"#B42318\" clip-path=\"url(#c)\"/></svg>";
        EditorActivity activity = open("clip.svg", original);
        try {
            WebView web = activity.findViewById(R.id.web_preview);
            waitForPage(web);
            runTestOnUiThread(() -> activity.findViewById(R.id.btn_viewport_fit_content).performClick());
            getInstrumentation().waitForIdleSync();
            Thread.sleep(1200);
            runTestOnUiThread(() -> {
                String text = ((android.widget.EditText) activity.findViewById(R.id.edit_code))
                        .getText().toString();
                assertEquals("适应内容不得写回原文件", original, text);
                assertTrue("clipPath 定义必须原样保留", text.contains("clipPath"));
                assertTrue("clip-path 引用必须原样保留", text.contains("clip-path=\"url(#c)\""));
                assertEquals("不得标记为未保存", false, activity.isDirtyForTest());
            });
        } finally {
            close(activity);
        }
    }
}
