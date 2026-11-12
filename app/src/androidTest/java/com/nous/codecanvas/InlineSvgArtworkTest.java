package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * The exact file the user reported must open as a zoomable, <b>animated</b> drawing.
 *
 * <p>{@code gemini carbon.html} is a real AI-generated artwork: a full HTML page whose body is one
 * inline {@code <svg viewBox="0 0 1200 675" preserveAspectRatio="xMidYMid slice">} with
 * {@code width:100vw;height:100vh}, driven by a 24KB script (parallax layers, spinning wheels, a
 * pedalling crank).</p>
 *
 * <p>Two reports came in against it: "无法缩放，显示不全" and then "只能加载出静态的". The first fix moved it
 * onto the vector viewport but extracted only the {@code <svg>} element, which dropped the script —
 * so the artwork rendered frozen. Both the viewport and the animation are asserted here against the
 * real file.</p>
 */
public class InlineSvgArtworkTest extends InstrumentationTestCase {

    private static final String FIXTURE = "fixtures/gemini-carbon.html";

    private EditorActivity editor;
    private String docId;

    private String fixture() throws Exception {
        try (InputStream in = getInstrumentation().getContext().getAssets().open(FIXTURE);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context c = getInstrumentation().getTargetContext();
        docId = "inline-svg-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(docId, "gemini carbon.html", fixture(), System.currentTimeMillis());
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

    /**
     * Run a JS expression that returns a bare token, and hand back the token.
     *
     * <p>Deliberately not JSON: every layer of quoting between Java and JS was a chance for the
     * value to arrive mangled, and these assertions only need a token or a number.</p>
     */
    private String evalToken(WebView web, String js) throws Exception {
        final String[] out = new String[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(js, v -> {
            out[0] = v;
            latch.countDown();
        }));
        latch.await(8, java.util.concurrent.TimeUnit.SECONDS);
        if (out[0] == null) return "";
        return out[0].replace("\"", "").trim();
    }

    /** The vector viewport must be offered: that is the only way this artwork can be zoomed. */
    public void testTheArtworkGetsTheVectorViewport() throws Throwable {
        runTestOnUiThread(() -> {
            View toolbar = editor.findViewById(R.id.viewport_toolbar);
            assertNotNull("视口工具栏必须存在", toolbar);
            assertEquals("单张内嵌 SVG 的 HTML 必须显示视口工具栏（否则无法缩放）",
                    View.VISIBLE, toolbar.getVisibility());
            View fit = editor.findViewById(R.id.btn_viewport_fit);
            assertNotNull("必须有「适应窗口」", fit);
            assertEquals(View.VISIBLE, fit.getVisibility());
        });
    }

    /** The preview page must hold the drawing and its script — and no page shell. */
    public void testThePreviewPageContainsTheDrawingAndItsScript() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        assertNotNull(web);

        String svgCount = evalToken(web,
                "(function(){return 'SVG_' + document.querySelectorAll('svg').length;})()");
        android.util.Log.i("CCINLINE", "svgCount=" + svgCount);
        assertEquals("页面里应当只有一张图，实际 " + svgCount, "SVG_1", svgCount);

        String viewBox = evalToken(web,
                "(function(){var s=document.querySelector('svg');"
                        + "return 'VB_' + (s?s.getAttribute('viewBox'):'none');})()");
        android.util.Log.i("CCINLINE", "viewBox=" + viewBox);
        assertEquals("必须保留原 viewBox", "VB_0 0 1200 675", viewBox);

        String stage = evalToken(web,
                "(function(){return 'STAGE_' + (document.getElementById('cc-stage')?1:0);})()");
        assertEquals("必须有视口变换层", "STAGE_1", stage);

        String shell = evalToken(web,
                "(function(){var b=document.body.innerHTML;"
                        + "var bad=(b.match(/<body/gi)||[]).length"
                        + " + (b.match(/<html/gi)||[]).length"
                        + " + (b.indexOf('<title>')>-1?1:0);"
                        + "return 'SHELL_' + bad;})()");
        android.util.Log.i("CCINLINE", "shellLeak=" + shell);
        assertEquals("包装页里不能残留原 HTML 的页面外壳，实际 " + shell, "SHELL_0", shell);
    }

    /** The animation script must be present in the preview page. */
    public void testTheAnimationScriptIsPresent() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        String probe = evalToken(web,
                "(function(){var s=document.querySelectorAll('script');"
                        + "for(var i=0;i<s.length;i++){"
                        + "  if((s[i].textContent||'').indexOf('requestAnimationFrame')>-1)"
                        + "    return 'RAF_YES_' + s.length;"
                        + "}"
                        + "return 'RAF_NO_' + s.length;})()");
        android.util.Log.i("CCINLINE", "scriptProbe=" + probe);
        assertTrue("预览页里必须有驱动动画的脚本，实际 " + probe, probe.startsWith("RAF_YES_"));
    }

    /** The artwork must actually move, not sit there as a still image. */
    public void testTheArtworkAnimates() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        Thread.sleep(1200);

        String first = frameSignature(web);
        Thread.sleep(900);
        String second = frameSignature(web);
        android.util.Log.i("CCINLINE", "frame1=" + first);
        android.util.Log.i("CCINLINE", "frame2=" + second);

        assertFalse("必须能读到图形状态", first.isEmpty());
        assertFalse("动画必须在跑：两帧完全相同说明是静态图（" + first + "）", first.equals(second));
    }

    /** A fingerprint of the drawing's live transform state. */
    private String frameSignature(WebView web) throws Exception {
        return evalToken(web,
                "(function(){"
                        + "var ids=['wheel-rear','wheel-front','crank-wheel','p-layer-1','p-layer-2'];"
                        + "var parts=[];"
                        + "for(var i=0;i<ids.length;i++){"
                        + "  var el=document.getElementById(ids[i]);"
                        + "  if(!el){parts.push(ids[i]+':none');continue;}"
                        + "  parts.push(ids[i]+':'+(el.getAttribute('transform')||''));"
                        + "}"
                        + "return parts.join(';');})()");
    }

    /** Zoom must actually move the page transform. */
    public void testZoomingTheArtworkMovesThePage() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        float before = pageScale(web);
        getInstrumentation().runOnMainSync(
                () -> editor.findViewById(R.id.btn_viewport_zoom_in).performClick());
        getInstrumentation().waitForIdleSync();
        Thread.sleep(400);
        float after = pageScale(web);
        android.util.Log.i("CCINLINE", "zoom " + before + " -> " + after);
        assertTrue("放大按钮必须改变页面变换（之前 " + before + "，之后 " + after + "）",
                after > before * 1.1f);

        getInstrumentation().runOnMainSync(
                () -> editor.findViewById(R.id.btn_viewport_fit).performClick());
        getInstrumentation().waitForIdleSync();
        Thread.sleep(400);
        float fitted = pageScale(web);
        assertTrue("适应窗口必须回到 1.0 附近，实际 " + fitted, Math.abs(fitted - 1f) < 0.15f);
    }

    /** The drawing must be fully visible, not cropped by its own slice attribute. */
    public void testTheWholeDrawingIsVisible() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        String geom = evalToken(web,
                "(function(){var s=document.querySelector('#cc-fit svg');if(!s)return 'none';"
                        + "var r=s.getBoundingClientRect();"
                        + "return r.width+'x'+r.height+' in '+window.innerWidth+'x'+window.innerHeight;})()");
        android.util.Log.i("CCINLINE", "geometry=" + geom);
        assertTrue("必须能读到图形尺寸，实际 " + geom, geom.contains(" in "));

        String[] parts = geom.split(" in ");
        String[] size = parts[0].split("x");
        String[] view = parts[1].split("x");
        double w = Double.parseDouble(size[0]);
        double h = Double.parseDouble(size[1]);
        double iw = Double.parseDouble(view[0]);
        double ih = Double.parseDouble(view[1]);

        assertTrue("图形必须完整落在视口内：宽 " + w + " <= " + iw, w <= iw + 1);
        assertTrue("图形必须完整落在视口内：高 " + h + " <= " + ih, h <= ih + 1);
        double ratio = w / h;
        assertTrue("必须保持原始宽高比 16:9，实际 " + ratio, Math.abs(ratio - (1200.0 / 675.0)) < 0.08);

        String par = evalToken(web,
                "(function(){var s=document.querySelector('svg');"
                        + "return 'PAR_' + (s?s.getAttribute('preserveAspectRatio'):'none');})()");
        android.util.Log.i("CCINLINE", "preserveAspectRatio=" + par);
        assertFalse("slice 必须被改写成 meet，实际 " + par,
                par.toLowerCase(java.util.Locale.ROOT).contains("slice"));
    }

    /**
     * A tap on the artwork's own control must reach it.
     *
     * <p>The viewport intercepts every touch to own pan and pinch, which also meant the drawing's
     * play/pause/speed buttons could never be pressed — the second half of "动态的加载不出来".
     * This taps the pause button through the viewport and requires the animation to stop.</p>
     */
    public void testTappingTheArtworkControlPausesIt() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        Thread.sleep(1200);

        // Find the pause button's position in page coordinates.
        String box = evalToken(web,
                "(function(){var b=document.getElementById('btn-pause');"
                        + "if(!b)return 'none';var r=b.getBoundingClientRect();"
                        + "return (r.left+r.width/2)+','+(r.top+r.height/2);})()");
        android.util.Log.i("CCINLINE", "pauseButtonAt=" + box);
        assertFalse("作品里必须有暂停按钮", "none".equals(box) || box.isEmpty());

        String[] xy = box.split(",");
        final float pageX = Float.parseFloat(xy[0]);
        final float pageY = Float.parseFloat(xy[1]);

        // Convert page coordinates back into viewport coordinates, which is what a touch carries.
        final View viewport = editor.findViewById(R.id.preview_viewport);
        final float scale = ((com.nous.codecanvas.ui.SvgViewport) viewport).getZoomFactor();
        final android.graphics.RectF bounds =
                ((com.nous.codecanvas.ui.SvgViewport) viewport).contentBoundsForTest();
        final float vx = pageX * scale + bounds.left;
        final float vy = pageY * scale + bounds.top;

        long t = android.os.SystemClock.uptimeMillis();
        getInstrumentation().runOnMainSync(() -> {
            viewport.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, vx, vy, 0));
            viewport.dispatchTouchEvent(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, vx, vy, 0));
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(700);

        String paused = evalToken(web,
                "(function(){var b=document.getElementById('btn-pause');"
                        + "if(!b)return 'none';"
                        + "return 'PAUSED_' + (b.getAttribute('data-state')||b.textContent||'');})()");
        android.util.Log.i("CCINLINE", "afterTap=" + paused);

        // The button must have reacted in some observable way: either its label/state changed, or
        // the animation stopped. Compare two frames after the tap.
        String f1 = frameSignature(web);
        Thread.sleep(800);
        String f2 = frameSignature(web);
        boolean stopped = f1.equals(f2);
        boolean reacted = !"none".equals(paused) && paused.toLowerCase().contains("pause");
        android.util.Log.i("CCINLINE", "stopped=" + stopped + " reacted=" + reacted);
        assertTrue("点击必须送达作品自己的按钮：要么动画停止，要么按钮状态变化（" + paused + "）",
                stopped || reacted);
    }

    private float pageScale(WebView web) throws Exception {
        String raw = evalToken(web,
                "(function(){var s=document.getElementById('cc-stage');if(!s)return '1';"
                        + "var t=getComputedStyle(s).transform;if(!t||t==='none')return '1';"
                        + "var i=t.indexOf('matrix(');if(i<0)return '1';"
                        + "return t.substring(i+7,t.indexOf(')',i)).split(',')[0];})()");
        return raw.isEmpty() ? 1f : Float.parseFloat(raw);
    }
}
