package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import android.widget.EditText;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real buttons against live WebView geometry, not copies of the fitting algorithm. */
public class PreviewFitModesTest extends InstrumentationTestCase {
    private static final String SVG_START = "<svg xmlns='http://www.w3.org/2000/svg' ";
    private static final String GEOMETRY = """
            (()=>{const a=window.CCViewport,s=a&&a.svg(); if(!s)return null;
              const v=s.viewBox.baseVal,r=s.querySelector('rect'),b=r.getBBox();
              const f=document.getElementById('cc-fit'), p=f.getBoundingClientRect(),k=p.width/f.clientWidth;
              const m=r.getScreenCTM(), t=(x,y)=>new DOMPoint(x,y).matrixTransform(m);
              const lo=t(b.x,b.y), hi=t(b.x+b.width,b.y+b.height);
              return {x:v.x,y:v.y,w:v.width,h:v.height,frame:innerWidth,fh:innerHeight,
                      left:p.x+lo.x*k,right:p.x+hi.x*k,top:p.y+lo.y*k,bottom:p.y+hi.y*k,
                      mode:a.fitMode(),state:r.getAttribute('data-state')};
            })()
            """;

    private DocumentRepository repo;
    private CanvasDocument doc;
    private EditorActivity activity;

    private WebView open(String title, String content) throws Exception {
        Context context = getInstrumentation().getTargetContext();
        repo = new DocumentRepository(context);
        doc = new CanvasDocument("preview-fit-" + System.nanoTime(), title, content, System.currentTimeMillis());
        repo.saveDocument(doc);
        activity = (EditorActivity) getInstrumentation().startActivitySync(
                new Intent(context, EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                        .putExtra(EditorActivity.EXTRA_START_PREVIEW, true));
        return activity.findViewById(R.id.web_preview);
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) getInstrumentation().runOnMainSync(activity::finish);
        if (doc != null) repo.deleteDocument(doc.getId());
        super.tearDown();
    }

    public void testSmallVerticalArtworkUsesMorePixelsWithoutReloading() throws Throwable {
        String source = SVG_START + "viewBox='-200 -100 600 400'>"
                + "<rect x='100' y='80' width='30' height='100' fill='orange'/></svg>";
        WebView web = open("portrait-in-oversized-frame.svg", source);
        JSONObject window = awaitMode(web, "window");
        assertEquals(-200d, window.getDouble("x"), .01);
        assertEquals(600d, window.getDouble("w"), .01);
        double oldHeight = window.getDouble("bottom") - window.getDouble("top");

        // A DOM-only state marker cannot survive a page reload. The artwork itself is not edited.
        evaluate(web, "CCViewport.svg().querySelector('rect').setAttribute('data-state','alive')");
        final int loads = activity.getPreviewLoadCountForTest();
        press(R.id.btn_viewport_fit_content);
        JSONObject content = awaitMode(web, "content");
        assertTrue("A small vertical drawing must use more visible pixels",
                content.getDouble("bottom") - content.getDouble("top") > 2 * oldHeight);
        assertOnScreen(content);
        assertEquals("alive", content.getString("state"));
        runTestOnUiThread(() -> {
            assertEquals(loads, activity.getPreviewLoadCountForTest());
            assertEquals(source, ((EditText) activity.findViewById(R.id.edit_code)).getText().toString());
            assertSelection(true);
            activity.findViewById(R.id.btn_viewport_zoom_in).performClick();
            assertSelection(true); // manual zoom never lies about the base fit mode
        });
        press(R.id.btn_viewport_fit);
        JSONObject restored = awaitMode(web, "window");
        assertEquals(window.getDouble("x"), restored.getDouble("x"), .01);
        assertEquals(window.getDouble("w"), restored.getDouble("w"), .01);
        assertEquals("alive", restored.getString("state"));
        runTestOnUiThread(() -> assertSelection(false));
    }

    public void testWideArtworkOutsideViewBoxIsBroughtFullyIntoFrame() throws Throwable {
        WebView web = open("wide-outside-declared-camera.svg", SVG_START + "viewBox='0 0 100 100'>"
                + "<rect x='-200' y='20' width='600' height='45' fill='deepskyblue'/></svg>");
        JSONObject window = awaitMode(web, "window");
        assertTrue(window.getDouble("left") < 0 || window.getDouble("right") > window.getDouble("frame"));
        press(R.id.btn_viewport_fit_content);
        JSONObject content = awaitMode(web, "content");
        assertTrue(content.getDouble("x") < -200);
        assertTrue(content.getDouble("w") > 600);
        assertOnScreen(content);
    }

    public void testHtmlKeepsLivePreviewAndSingleWindowMode() throws Throwable {
        WebView web = open("responsive-document.html", "<!doctype html><meta name=viewport "
                + "content='width=device-width,initial-scale=1'><article id=probe>HTML remains live</article>");
        long deadline = System.currentTimeMillis() + 12000;
        String result = "";
        while (System.currentTimeMillis() < deadline) {
            result = evaluate(web, "document.getElementById('probe')?.textContent");
            if ("\"HTML remains live\"".equals(result)) break;
            Thread.sleep(120);
        }
        assertEquals("\"HTML remains live\"", result);
        final int loads = activity.getPreviewLoadCountForTest();
        runTestOnUiThread(() -> {
            View fit = activity.findViewById(R.id.btn_viewport_fit_content);
            assertEquals(View.GONE, fit.getVisibility());
            activity.findViewById(R.id.btn_viewport_fit).performClick();
            assertEquals(loads, activity.getPreviewLoadCountForTest());
        });
        assertEquals("\"HTML remains live\"", evaluate(web, "document.getElementById('probe')?.textContent"));
    }

    private void assertSelection(boolean selectingContent) {
        View window = activity.findViewById(R.id.btn_viewport_fit);
        View content = activity.findViewById(R.id.btn_viewport_fit_content);
        AccessibilityNodeInfo node = content.createAccessibilityNodeInfo();
        assertTrue(node.isCheckable());
        assertEquals(selectingContent, node.isChecked());
        assertEquals(selectingContent, content.isSelected());
        assertEquals(!selectingContent, window.isSelected());
        assertEquals(window.getRight(), content.getLeft());
        View parent = (View) window.getParent();
        assertEquals(0, window.getLeft());
        assertTrue(parent.getClipToOutline());
        int touch = activity.getResources().getDimensionPixelSize(R.dimen.touch_min);
        for (int id : new int[] {R.id.btn_viewport_fit, R.id.btn_viewport_fit_content,
                R.id.btn_preview_refresh, R.id.btn_preview_fullscreen}) {
            View target = activity.findViewById(id);
            assertTrue(target.getWidth() >= touch && target.getHeight() >= touch);
        }
    }

    private void assertOnScreen(JSONObject b) throws Exception {
        assertTrue(b.toString(), b.getDouble("left") >= -1
                && b.getDouble("right") <= b.getDouble("frame") + 1
                && b.getDouble("top") >= -1 && b.getDouble("bottom") <= b.getDouble("fh") + 1);
    }

    private void press(int id) {
        getInstrumentation().runOnMainSync(() -> activity.findViewById(id).performClick());
    }

    private JSONObject awaitMode(WebView web, String mode) throws Exception {
        long deadline = System.currentTimeMillis() + 12000;
        String result = "";
        while (System.currentTimeMillis() < deadline) {
            result = evaluate(web, GEOMETRY);
            if (result != null && result.startsWith("{")) {
                JSONObject geometry = new JSONObject(result);
                if (mode.equals(geometry.optString("mode"))) {
                    getInstrumentation().waitForIdleSync();
                    return geometry;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Expected live mode " + mode + ", actual " + result);
    }

    private String evaluate(WebView web, String script) throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(script, v -> {
            result.set(v); latch.countDown();
        }));
        assertTrue("WebView did not evaluate fixture", latch.await(4, TimeUnit.SECONDS));
        return result.get();
    }
}
