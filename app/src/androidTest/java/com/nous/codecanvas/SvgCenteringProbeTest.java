package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

/**
 * Measures whether the drawing is actually centred in the area the user can see.
 *
 * <p>A screenshot showed a wide SVG sitting low in the preview. The page centres with flexbox over
 * its own viewport, so if the drawing is off-centre then either the WebView is taller than the
 * visible area (its centre is below the visible centre) or the page is centring against a stale
 * innerHeight. This test prints the numbers that tell the two apart, and then asserts the drawing
 * is centred within a tolerance — so a regression fails the build instead of needing an eyeball.</p>
 */
public class SvgCenteringProbeTest extends InstrumentationTestCase {

    private static final String WIDE_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1280 800\" width=\"1280\" height=\"800\">"
            + "<rect width=\"1280\" height=\"800\" fill=\"#ffffff\"/>"
            + "<ellipse cx=\"640\" cy=\"400\" rx=\"420\" ry=\"220\" fill=\"#2f6fdb\"/>"
            + "<circle cx=\"40\" cy=\"40\" r=\"30\" fill=\"#e23b3b\"/>"
            + "<circle cx=\"1240\" cy=\"40\" r=\"30\" fill=\"#2fae5f\"/>"
            + "<circle cx=\"40\" cy=\"760\" r=\"30\" fill=\"#2f6fdb\"/>"
            + "<circle cx=\"1240\" cy=\"760\" r=\"30\" fill=\"#e8b93b\"/>"
            + "</svg>";

    private EditorActivity editor;
    private String docId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context c = getInstrumentation().getTargetContext();
        docId = "center-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(docId, "wide.svg", WIDE_SVG, System.currentTimeMillis());
        doc.setKindName("SVG");
        new DocumentRepository(c).saveDocument(doc);
        Intent i = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, docId)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        editor = (EditorActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(1200);
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

    private int[] screenBox(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return new int[]{loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight()};
    }

    public void testDrawingIsCentredInTheVisibleArea() throws Throwable {
        final WebView web = editor.findViewById(R.id.web_preview);
        final View viewport = editor.findViewById(R.id.preview_viewport);
        assertNotNull("预览 WebView 必须存在", web);
        assertNotNull(viewport);

        int[] webBox = screenBox(web);
        int[] vpBox = screenBox(viewport);
        android.util.Log.i("CCCENTER", "viewport=" + vpBox[0] + "," + vpBox[1] + ".." + vpBox[2] + "," + vpBox[3]
                + " h=" + (vpBox[3] - vpBox[1]));
        android.util.Log.i("CCCENTER", "webview =" + webBox[0] + "," + webBox[1] + ".." + webBox[2] + "," + webBox[3]
                + " h=" + (webBox[3] - webBox[1]));

        // Read the page's own numbers: its viewport height and where the drawing landed.
        final String[] json = new String[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "(function(){var s=document.querySelector('#cc-fit svg');"
                        + "if(!s)return 'no-svg';var r=s.getBoundingClientRect();"
                        + "return JSON.stringify({ih:window.innerHeight,iw:window.innerWidth,"
                        + "top:r.top,bottom:r.bottom,left:r.left,right:r.right,w:r.width,h:r.height});})()",
                value -> {
                    json[0] = value;
                    latch.countDown();
                }));
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        android.util.Log.i("CCCENTER", "page=" + json[0]);

        assertNotNull("必须能读到页面里的图形位置", json[0]);
        assertFalse("页面里必须有 <svg>", json[0].contains("no-svg"));

        org.json.JSONObject o = new org.json.JSONObject(json[0].replace("\\\"", "\"")
                .replaceAll("^\"|\"$", ""));
        double innerH = o.getDouble("ih");
        double top = o.getDouble("top");
        double bottom = o.getDouble("bottom");
        double gapAbove = top;
        double gapBelow = innerH - bottom;

        android.util.Log.i("CCCENTER", "gapAbove=" + gapAbove + " gapBelow=" + gapBelow
                + " innerH=" + innerH + " drawingH=" + o.getDouble("h"));

        // The page must not be taller than the area the user sees, or everything centred in it
        // lands below the visible centre.
        int webHeight = webBox[3] - webBox[1];
        int vpHeight = vpBox[3] - vpBox[1];
        assertTrue("WebView 不能比可见预览区更高（WebView " + webHeight + "px vs 预览区 " + vpHeight + "px）",
                webHeight <= vpHeight + 2);

        double tolerance = Math.max(6.0, innerH * 0.02);
        assertTrue("图形必须垂直居中：上方留白 " + Math.round(gapAbove)
                        + "px，下方留白 " + Math.round(gapBelow) + "px",
                Math.abs(gapAbove - gapBelow) <= tolerance);
    }
}
