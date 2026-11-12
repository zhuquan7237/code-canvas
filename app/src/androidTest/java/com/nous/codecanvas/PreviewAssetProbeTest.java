package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Diagnostic: are the bundled preview assets actually reachable from the page? */
public class PreviewAssetProbeTest extends InstrumentationTestCase {

    public void testBundledLibrariesLoad() throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        DocumentRepository repo = new DocumentRepository(c);
        String id = "probe-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(id, "probe.md", "# hi\n\n| a | b |\n| - | - |\n| 1 | 2 |\n",
                System.currentTimeMillis());
        doc.setKindName("MARKDOWN");
        repo.saveDocument(doc);

        Intent intent = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, id)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(intent);
        try {
            getInstrumentation().waitForIdleSync();
            WebView web = activity.findViewById(R.id.web_preview);
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                if ("complete".equals(eval(web, "document.readyState"))) break;
                Thread.sleep(300);
            }
            Thread.sleep(2500);
            android.util.Log.i("CCPROBE", "marked=" + eval(web, "typeof window.marked"));
            android.util.Log.i("CCPROBE", "katex=" + eval(web, "typeof window.katex"));
            android.util.Log.i("CCPROBE", "mermaid=" + eval(web, "typeof window.mermaid"));
            android.util.Log.i("CCPROBE", "tables=" + eval(web, "document.querySelectorAll('#cc-md table').length + ''"));
            android.util.Log.i("CCPROBE", "mdErr=" + eval(web, "document.getElementById('cc-md-err').textContent"));
            android.util.Log.i("CCPROBE", "mdHtml=" + eval(web, "document.getElementById('cc-md').innerHTML.slice(0,300)"));
            android.util.Log.i("CCPROBE", "mdRan=" + eval(web, "String(window.CC_MD_RAN)"));
            android.util.Log.i("CCPROBE", "scripts=" + eval(web, "document.scripts.length + ''"));
            android.util.Log.i("CCPROBE", "srcs=" + eval(web, "Array.from(document.scripts).map(s=>s.src||'(inline)').join('|')"));
            android.util.Log.i("CCPROBE", "jsEnabled=" + eval(web, "String(document.getElementById('cc-md')!==null)"));
            android.util.Log.i("CCPROBE", "bodyLen=" + eval(web, "document.body.innerHTML.length + ''"));
        } finally {
            final EditorActivity a = activity;
            getInstrumentation().runOnMainSync(a::finish);
            getInstrumentation().waitForIdleSync();
            repo.deleteDocument(id);
        }
    }

    private String eval(WebView web, String js) throws Exception {
        final AtomicReference<String> out = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(js, v -> {
            out.set(v);
            latch.countDown();
        }));
        latch.await(6, TimeUnit.SECONDS);
        return out.get();
    }
}
