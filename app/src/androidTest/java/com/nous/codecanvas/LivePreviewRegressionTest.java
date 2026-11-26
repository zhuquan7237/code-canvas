package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimatedImageDrawable;
import android.os.Build;
import android.test.InstrumentationTestCase;
import android.webkit.WebView;
import android.widget.ImageView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.util.CanvasPrefs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LivePreviewRegressionTest extends InstrumentationTestCase {
    private EditorActivity editor;
    private CanvasDocument doc;
    private boolean scripts;

    @Override protected void setUp() throws Exception {
        super.setUp();
        scripts = CanvasPrefs.scriptsAllowed(context());
        CanvasPrefs.setScriptsAllowed(context(), true);
    }

    @Override protected void tearDown() throws Exception {
        if (editor != null) getInstrumentation().runOnMainSync(editor::finish);
        if (doc != null) {
            new DocumentRepository(context()).deleteDocument(doc.getId());
            FileImporter.blobFile(context(), doc.getId()).delete();
        }
        CanvasPrefs.setScriptsAllowed(context(), scripts);
        super.tearDown();
    }

    private Context context() { return getInstrumentation().getTargetContext(); }

    private void open(String title, String source, byte[] blob) throws Exception {
        doc = new CanvasDocument("live-" + System.nanoTime(), title, source, System.currentTimeMillis());
        if (blob != null) {
            doc.setBlob(true);
            doc.setMimeType(title.endsWith(".pdf") ? "application/pdf" : "image/webp");
            doc.setByteSize(blob.length);
            FileImporter.writeBlob(context(), doc.getId(), blob);
        }
        new DocumentRepository(context()).saveDocument(doc);
        editor = (EditorActivity) getInstrumentation().startActivitySync(new Intent(context(), EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true));
        getInstrumentation().waitForIdleSync();
    }

    private String eval(String code) throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> ((WebView) editor.findViewById(R.id.web_preview))
                .evaluateJavascript(code, val -> {result.set(val); latch.countDown();}));
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        return result.get();
    }

    private void waitFor(String condition) throws Exception {
        long deadline = System.currentTimeMillis() + 12000;
        do {
            if ("true".equals(eval(condition))) return;
            Thread.sleep(120);
        } while (System.currentTimeMillis() < deadline);
        fail("No matching visible state: " + condition);
    }

    public void testNativeSvgRootDynamicViewBoxAndScriptSwitch() throws Exception {
        String original = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 100" width="100%" height="100%">
                  <rect id="plate" width="200" height="200" fill="teal"/>
                  <script><![CDATA[
                    const root=document.documentElement;
                    root.setAttribute('viewBox','0 0 200 200');
                    root.addEventListener('click',()=>root.setAttribute('data-clicked','yes'));
                  ]]></script>
                </svg>
                """;
        open("native-root.svg", original, null);
        waitFor("!!window.CCViewport?.svg() && CCViewport.svg().getAttribute('viewBox')==='0 0 200 200'"
                + " && Math.abs(CCViewport.bounds().w-CCViewport.bounds().h)<1");
        assertEquals("true", eval("(()=>{const b=CCViewport.bounds();CCTap(b.x+b.w/2,b.y+b.h/2);"
                + "return CCViewport.svg().getAttribute('data-clicked')==='yes'})()"));
        getInstrumentation().runOnMainSync(() -> ((android.widget.Switch) editor.findViewById(R.id.switch_allow_js))
                .setChecked(false));
        waitFor("!!window.CCViewport?.svg() && CCViewport.svg().getAttribute('viewBox')==='0 0 200 100'"
                + " && Math.abs(CCViewport.bounds().w/CCViewport.bounds().h-2)<.03");
        assertEquals(original, new DocumentRepository(context()).findById(doc.getId()).getContent());
    }

    public void testCanvasGameKeepsRunningAndAcceptsPointerInput() throws Exception {
        open("game.html", """
                <!doctype html><meta name="viewport" content="width=device-width, user-scalable=no">
                <style>body{margin:0;overflow:hidden;display:grid;place-items:center;height:100vh}</style>
                <canvas id="game" width="1600" height="900"></canvas>
                <script>
                  const game=document.querySelector('canvas'), ctx=game.getContext('2d');
                  localStorage.setItem('round','0');
                  game.onpointerdown=()=>document.body.dataset.clicked='yes';
                  function frame(t){ctx.fillStyle='teal';ctx.fillRect(t%100,0,100,100);
                    game.dataset.frame=String(t);requestAnimationFrame(frame);}
                  requestAnimationFrame(frame);
                </script>
                """, null);
        waitFor("!!document.querySelector('canvas')?.dataset.frame");
        assertEquals("true", eval("(()=>{const c=document.querySelector('canvas'), r=c.getBoundingClientRect();"
                + "c.dispatchEvent(new PointerEvent('pointerdown'));"
                + "return document.body.dataset.clicked==='yes'&&r.right<=innerWidth+1"
                + "&&r.bottom<=innerHeight+1&&Math.abs(r.width/r.height-16/9)<.05})()"));
        String before=eval("document.querySelector('canvas').dataset.frame");
        Thread.sleep(250);
        assertFalse(before.equals(eval("document.querySelector('canvas').dataset.frame")));
    }

    public void testMermaidTallDiagramFitsAndZooms() throws Exception {
        StringBuilder diagram = new StringBuilder("flowchart TD\n");
        for (int i=0;i<17;i++) diagram.append("N").append(i).append(" --> N").append(i+1).append("\n");
        open("tall.mmd", diagram.toString(), null);
        waitFor("!!document.querySelector('#cc-mermaid svg')");
        assertEquals("true", eval("(()=>{const r=document.querySelector('#cc-mermaid svg').getBoundingClientRect();"
                + "return r.height>50&&r.bottom<=innerHeight+1&&r.top>=0})()"));
        double before=Double.parseDouble(eval("visualViewport.scale"));
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_viewport_zoom_in).performClick());
        Thread.sleep(550);
        assertTrue(Double.parseDouble(eval("visualViewport.scale"))>before*1.1);
    }

    public void testTallPdfFitsBelowItsPager() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        android.graphics.pdf.PdfDocument pdf = new android.graphics.pdf.PdfDocument();
        try {
            android.graphics.pdf.PdfDocument.Page page = pdf.startPage(
                    new android.graphics.pdf.PdfDocument.PageInfo.Builder(180, 16000, 1).create());
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(android.graphics.Color.RED);
            page.getCanvas().drawRect(0, 0, 180, 900, paint);
            paint.setColor(android.graphics.Color.BLUE);
            page.getCanvas().drawRect(0, 15100, 180, 16000, paint);
            pdf.finishPage(page);
            pdf.writeTo(out);
        } finally { pdf.close(); }
        open("tall-page.pdf", "", out.toByteArray());
        ImageView image = editor.findViewById(R.id.preview_image);
        android.view.View viewport = editor.findViewById(R.id.preview_viewport);
        android.view.View pager = editor.findViewById(R.id.pdf_pager);
        long deadline = System.currentTimeMillis() + 12000;
        AtomicReference<android.graphics.drawable.Drawable> result = new AtomicReference<>();
        do {
            getInstrumentation().runOnMainSync(() -> result.set(image.getDrawable()));
            if (result.get() != null) break;
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        assertTrue(result.get() instanceof android.graphics.drawable.BitmapDrawable);
        getInstrumentation().runOnMainSync(() -> {
            assertTrue(viewport.getTop() >= pager.getBottom());
            android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) result.get()).getBitmap();
            assertTrue(bitmap.getHeight() <= 3072);
            int mid = bitmap.getWidth()/2;
            int h = bitmap.getHeight();
            assertEquals(android.graphics.Color.RED, bitmap.getPixel(mid, h/50));
            assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(mid, h-h/50));
        });
    }

    public void testAnimatedWebpProducesPlayingDrawable() throws Exception {
        if (Build.VERSION.SDK_INT < 28) return;
        byte[] bytes=android.util.Base64.decode(
                "UklGRoQAAABXRUJQVlA4WAoAAAACAAAAFwAACwAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAA"
                +"ABcAAAsAACwBAAJWUDhMDwAAAC8XwAIABxD9j/4HIqL/AQBBTk1GKAAAAAAAAAAAABcAAAsAACwB"
                +"AABWUDhMDwAAAC8XwAIABxDR//4HIqL/AQA=", android.util.Base64.DEFAULT);
        open("two-frames.webp", "", bytes);
        getInstrumentation().runOnMainSync(() -> {
            ImageView v=editor.findViewById(R.id.preview_image);
            assertTrue(v.getDrawable() instanceof AnimatedImageDrawable);
            assertTrue(((AnimatedImageDrawable)v.getDrawable()).isRunning());
        });
    }
}
