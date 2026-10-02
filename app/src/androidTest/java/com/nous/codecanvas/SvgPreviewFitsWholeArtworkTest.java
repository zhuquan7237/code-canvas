package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * "导入进去预览只能看到中间一部分" — an artwork authored for a full screen uses
 * {@code preserveAspectRatio="…slice"} plus {@code width:100vw;height:100vh}, i.e. it asks to cover
 * the viewport and crop whatever does not fit. On a portrait phone that crops a wide scene down to a
 * middle band, so the preview shows a fragment instead of the artwork.
 *
 * <p>The measurement is exact rather than visual: {@code getScreenCTM()} maps the viewBox corners
 * into screen coordinates, so if any corner falls outside the viewport the artwork is being cut.</p>
 */
public class SvgPreviewFitsWholeArtworkTest extends InstrumentationTestCase {

    private static final String FIXTURE = "wide-scene.svg";

    /** A wide scene with the same authoring pattern as the reported file, plus corner markers. */
    private static final String WIDE_SCENE =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1280 800\" width=\"100%\" height=\"100%\""
            + " preserveAspectRatio=\"xMidYMid slice\" style=\"display:block;width:100vw;height:100vh;background:#cce9e5\">"
            + "<rect x=\"0\" y=\"0\" width=\"40\" height=\"40\" fill=\"#ff0000\"/>"
            + "<rect x=\"1240\" y=\"0\" width=\"40\" height=\"40\" fill=\"#00ff00\"/>"
            + "<rect x=\"0\" y=\"760\" width=\"40\" height=\"40\" fill=\"#0000ff\"/>"
            + "<rect x=\"1240\" y=\"760\" width=\"40\" height=\"40\" fill=\"#ffff00\"/>"
            + "<text x=\"640\" y=\"400\" font-size=\"48\" text-anchor=\"middle\">scene</text>"
            + "</svg>";

    public void testWideArtworkIsNotCroppedByItsOwnSlice() throws Throwable {
        assertWholeArtworkVisible("合成的宽幅场景（作者写法 slice + 100vw/100vh）", WIDE_SCENE);
    }

    /** The file the user actually reported, when it has been placed in the app's files dir. */
    public void testReportedArtworkIsNotCropped() throws Throwable {
        File fixture = new File(getInstrumentation().getTargetContext().getFilesDir(), FIXTURE);
        if (!fixture.isFile()) {
            android.util.Log.i("CodeCanvasTest", "no " + fixture + "; skipping the reported-file case");
            return;
        }
        String svg = readAll(fixture);
        assertTrue("fixture looks like an svg", svg.contains("<svg"));
        assertWholeArtworkVisible("用户报告的宽幅作品", svg);
    }

    private String readAll(File f) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void assertWholeArtworkVisible(String what, String svg) throws Throwable {
        final Context c = getInstrumentation().getTargetContext();
        DocumentRepository repo = new DocumentRepository(c);
        CanvasDocument doc = new CanvasDocument("fit-" + System.nanoTime(), "fit.svg", svg,
                System.currentTimeMillis());
        repo.saveDocument(doc);

        Intent intent = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(intent);
        try {
            final WebView web = activity.findViewById(R.id.web_preview);
            assertNotNull("preview WebView must exist", web);
            long deadline = System.currentTimeMillis() + 12000;
            String geometry = null;
            while (System.currentTimeMillis() < deadline) {
                geometry = evaluate(web, GEOMETRY_JS);
                if (geometry != null && geometry.startsWith("{")) break;
                Thread.sleep(400);
            }
            android.util.Log.i("CodeCanvasTest", what + " geometry=" + geometry);
            assertNotNull("could not read the artwork geometry from the preview", geometry);

            double[] box = parse(geometry);
            // box = innerWidth, innerHeight, x0, y0, x1, y1 of the viewBox corners in screen px
            double vw = box[0], vh = box[1];
            double x0 = box[2], y0 = box[3], x1 = box[4], y1 = box[5];
            assertTrue(what + "：整幅必须完整可见，实际 " + geometry,
                    x0 >= -1 && y0 >= -1 && x1 <= vw + 1 && y1 <= vh + 1);
        } finally {
            final EditorActivity toFinish = activity;
            getInstrumentation().runOnMainSync(toFinish::finish);
            repo.deleteDocument(doc.getId());
        }
    }

    /** Maps the viewBox corners through getScreenCTM, which includes the preserveAspectRatio fit. */
    private static final String GEOMETRY_JS =
            "(function(){var s=document.querySelector('svg');if(!s||!s.getScreenCTM)return 'NO_SVG';"
            + "var m=s.getScreenCTM(),p=s.createSVGPoint(),vb=s.viewBox.baseVal;"
            + "function t(x,y){p.x=x;p.y=y;var r=p.matrixTransform(m);return [r.x,r.y];}"
            + "var a=t(vb.x,vb.y),b=t(vb.x+vb.width,vb.y+vb.height);"
            + "return JSON.stringify({w:window.innerWidth,h:window.innerHeight,"
            + "x0:a[0],y0:a[1],x1:b[0],y1:b[1]});})()";

    private String evaluate(final WebView web, final String js) throws Exception {
        final AtomicReference<String> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(js, value -> {
            result.set(value);
            latch.countDown();
        }));
        latch.await(4, TimeUnit.SECONDS);
        String raw = result.get();
        if (raw == null) return null;
        // evaluateJavascript hands back a JSON string, so the payload is quoted and escaped.
        return raw.replace("\\\"", "\"").replaceAll("^\"|\"$", "");
    }

    private double[] parse(String json) {
        // Simple manual extractor for {w:...,h:...,x0:...,y0:...,x1:...,y1:...}
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            return new double[] {
                    o.getDouble("w"), o.getDouble("h"),
                    o.getDouble("x0"), o.getDouble("y0"),
                    o.getDouble("x1"), o.getDouble("y1")
            };
        } catch (Exception e) {
            throw new RuntimeException("bad json: " + json, e);
        }
    }
}
