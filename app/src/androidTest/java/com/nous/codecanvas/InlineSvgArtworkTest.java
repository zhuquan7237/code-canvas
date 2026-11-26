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
 * <p>The original HTML document must keep styles and scripts. A narrow phone must still show
 * the entire stage and accept normal WebView touch input and zoom.</p>
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
    public void testTheArtworkGetsRealWebZoom() throws Throwable {
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

    public void testThePreviewPreservesThePageAndViewBox() throws Exception {
        WebView web = editor.findViewById(R.id.web_preview);
        assertEquals("HTML_0 0 1200 675", evalToken(web,
                "document.documentElement.tagName+'_'+document.querySelector('svg').getAttribute('viewBox')"));
        assertEquals("true", evalToken(web, "!!document.querySelector('meta[name=viewport]')"
                + " && document.querySelector('meta[name=viewport]').content.includes('user-scalable=yes')"));
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
                "(function(){var s=document.getElementById('diary-stage');if(!s)return 'none';"
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

        assertEquals("true", evalToken(web,
                "[...document.querySelectorAll('.btn-group')].every(b=>{const r=b.getBoundingClientRect();"
                + "return r.width>0&&r.left>=-1&&r.right<=innerWidth+1&&r.top>=-1&&r.bottom<=innerHeight+1})"));
    }

    public void testTappingTheArtworkControlPausesIt() throws Exception {
        final WebView web = editor.findViewById(R.id.web_preview);
        String box = evalToken(web,
                "(()=>{const r=document.getElementById('btn-pause').getBoundingClientRect();"
                + "return (r.left+r.width/2)+','+(r.top+r.height/2)})()");
        String[] xy = box.split(",");
        final View viewport = editor.findViewById(R.id.preview_viewport);
        getInstrumentation().runOnMainSync(() -> {
            float x = Float.parseFloat(xy[0]) * web.getScale();
            float y = Float.parseFloat(xy[1]) * web.getScale();
            long t = android.os.SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0);
            viewport.dispatchTouchEvent(down);
            viewport.dispatchTouchEvent(up);
            down.recycle();
            up.recycle();
        });
        Thread.sleep(550);
        assertEquals("1", evalToken(web, "document.getElementById('paused-badge').getAttribute('opacity')"));
    }

    private float pageScale(WebView web) throws Exception {
        return Float.parseFloat(evalToken(web, "String(visualViewport.scale)"));
    }
}
