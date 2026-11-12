package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * The exact file the user reported must open as a zoomable drawing.
 *
 * <p>{@code gemini carbon.html} is a real AI-generated artwork: a full HTML page whose body is one
 * inline {@code <svg viewBox="0 0 1200 675" preserveAspectRatio="xMidYMid slice">} with
 * {@code width:100vw;height:100vh}. It was reported as "放进代码画布里就是无法缩放，显示不全".</p>
 *
 * <p>Two things have to be true for that to be fixed, and both are asserted here against the real
 * file rather than a reduced fixture: the document must be previewed through the vector viewport
 * (so it can zoom at all), and the page inside that viewport must contain the drawing only — not a
 * whole HTML document nested inside the SVG stage.</p>
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

    /** The page inside the viewport must be the drawing, not a nested HTML document. */
    public void testThePreviewPageContainsOnlyTheDrawing() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        assertNotNull(web);
        final String[] out = new String[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "(function(){"
                        + "var svgs=document.querySelectorAll('svg');"
                        + "var stage=document.getElementById('cc-stage');"
                        + "var viewBox=svgs.length?svgs[0].getAttribute('viewBox'):null;"
                        // The wrapper page has its own <html>; what must NOT be present is the
                        // original document's markup (its lang, its title, its stylesheet) leaking
                        // into the stage.
                        // The drawing keeps its own id, xmlns and on-canvas text — that IS the
                        // artwork. What must not appear is the original page's scaffolding: its
                        // stylesheet rules, or a nested <body>/<html>.
                        + "var body=document.body.innerHTML;"
                        + "var why=[];"
                        + "if(body.indexOf('tabular-nums')>-1)why.push('style');"
                        + "if((body.match(/<body/gi)||[]).length>0)why.push('body');"
                        + "if((body.match(/<html/gi)||[]).length>0)why.push('html');"
                        + "return JSON.stringify({n:svgs.length,vb:viewBox,stage:!!stage,"
                        + "nested:why.length>0,why:why.join('+'),len:body.length});})()",
                v -> {
                    out[0] = v;
                    latch.countDown();
                }));
        latch.await(6, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(out[0]);
        android.util.Log.i("CCINLINE", "page=" + out[0]);
        String json = out[0].replace("\\\"", "\"").replaceAll("^\"|\"$", "");
        org.json.JSONObject o = new org.json.JSONObject(json);
        assertEquals("页面里应当只有一张图", 1, o.getInt("n"));
        assertEquals("必须保留原 viewBox", "0 0 1200 675", o.getString("vb"));
        assertTrue("必须有视口变换层", o.getBoolean("stage"));
        assertFalse("包装页里不能残留原 HTML 文档的标记", o.getBoolean("nested"));
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
        final String[] out = new String[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "(function(){var s=document.querySelector('#cc-fit svg');if(!s)return 'none';"
                        + "var r=s.getBoundingClientRect();"
                        + "return JSON.stringify({w:r.width,h:r.height,iw:window.innerWidth,ih:window.innerHeight,"
                        + "par:s.getAttribute('preserveAspectRatio')});})()",
                v -> {
                    out[0] = v;
                    latch.countDown();
                }));
        latch.await(6, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(out[0]);
        android.util.Log.i("CCINLINE", "geometry=" + out[0]);
        String json = out[0].replace("\\\"", "\"").replaceAll("^\"|\"$", "");
        org.json.JSONObject o = new org.json.JSONObject(json);
        double w = o.getDouble("w");
        double h = o.getDouble("h");
        double iw = o.getDouble("iw");
        double ih = o.getDouble("ih");

        assertTrue("图形必须完整落在视口内：宽 " + w + " <= " + iw, w <= iw + 1);
        assertTrue("图形必须完整落在视口内：高 " + h + " <= " + ih, h <= ih + 1);
        // 1200x675 is 16:9; a slice would have made it fill the portrait viewport and overflow.
        double ratio = w / h;
        assertTrue("必须保持原始宽高比 16:9，实际 " + ratio, Math.abs(ratio - (1200.0 / 675.0)) < 0.08);
        String par = o.getString("par");
        assertFalse("slice 必须被改写成 meet，实际 " + par,
                par != null && par.toLowerCase(java.util.Locale.ROOT).contains("slice"));
    }

    private float pageScale(WebView web) throws Exception {
        final String[] out = new String[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "(function(){var s=document.getElementById('cc-stage');if(!s)return '1';"
                        + "var t=getComputedStyle(s).transform;if(!t||t==='none')return '1';"
                        + "var i=t.indexOf('matrix(');if(i<0)return '1';"
                        + "return t.substring(i+7,t.indexOf(')',i)).split(',')[0];})()",
                v -> {
                    out[0] = v;
                    latch.countDown();
                }));
        latch.await(6, java.util.concurrent.TimeUnit.SECONDS);
        String raw = out[0] == null ? "1" : out[0].replace("\"", "").trim();
        return raw.isEmpty() ? 1f : Float.parseFloat(raw);
    }
}
