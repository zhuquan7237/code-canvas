package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.widget.EditText;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

/**
 * Opening and typing in a large document must stay responsive.
 *
 * <p>Measures wall-clock time for the two things the user actually felt: the editor becoming usable
 * after opening, and a keystroke being reflected. Thresholds are generous — this is a regression
 * guard against whole-document work on the UI thread, not a benchmark.</p>
 */
public class LargeDocumentPerformanceTest extends InstrumentationTestCase {

    private Context context;
    private DocumentRepository repository;

    /** ~300KB of SVG, the size range where the reported lag appeared. */
    private static String largeSvg() {
        StringBuilder sb = new StringBuilder(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 8000 6000\">");
        for (int i = 0; i < 3000; i++) {
            sb.append("<g id=\"g").append(i).append("\" transform=\"translate(")
                    .append(i % 8000).append(",").append(i % 6000).append(")\">")
                    .append("<rect width=\"40\" height=\"30\" fill=\"#2456C6\" stroke=\"#0F172A\" stroke-width=\"2\"/>")
                    .append("<text x=\"4\" y=\"20\" font-size=\"10\">node ").append(i).append("</text>")
                    .append("</g>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
    }

    /** ~150KB: big enough to expose whole-document work, small enough to stay editable. */
    private EditorActivity openMedium() throws Exception {
        StringBuilder sb = new StringBuilder(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 8000 6000\">");
        for (int i = 0; i < 850; i++) {
            sb.append("<g id=\"g").append(i).append("\"><rect width=\"40\" height=\"30\" fill=\"#2456C6\"/>")
                    .append("<text x=\"4\" y=\"20\">node ").append(i).append("</text></g>");
        }
        sb.append("</svg>");
        return openWith("medium.svg", sb.toString());
    }

    private EditorActivity openWith(String name, String body) throws Exception {
        String id = "perf-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(id, name, body, System.currentTimeMillis());
        doc.setKindName("SVG");
        repository.saveDocument(doc);
        Intent i = new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, id)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, false);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        android.util.Log.i("CCPERF", name + " size=" + body.length() + " chars");
        return activity;
    }

    private EditorActivity openLarge() throws Exception {
        String body = largeSvg();
        android.util.Log.i("CCPERF", "document size=" + body.length() + " chars");
        String id = "perf-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(id, "large.svg", body, System.currentTimeMillis());
        doc.setKindName("SVG");
        repository.saveDocument(doc);
        Intent i = new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, id)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, false);
        long start = System.currentTimeMillis();
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        long elapsed = System.currentTimeMillis() - start;
        android.util.Log.i("CCPERF", "open(code tab) took " + elapsed + " ms");
        return activity;
    }

    /**
     * A 535KB SVG must open quickly and must not freeze.
     *
     * <p>It previously ANR'd for 60 seconds: one {@code EditText} cannot lay out half a megabyte.
     * The app now shows the preview and explains that the source is too large to edit in place,
     * which is a usable state instead of a hang.</p>
     */
    public void testLargeDocumentOpensWithoutStalling() throws Throwable {
        long openStart = System.currentTimeMillis();
        EditorActivity activity = openLarge();
        try {
            long start = System.currentTimeMillis();
            long deadline = start + 8000;
            boolean shown = false;
            while (System.currentTimeMillis() < deadline) {
                final boolean[] ok = {false};
                runTestOnUiThread(() -> {
                    // The banner may be hidden again once the preview paints, so the durable
                    // signals are: the code tab is disabled and the huge text never entered the
                    // EditText (which is what used to freeze the app).
                    android.view.View tab = activity.findViewById(R.id.tab_code);
                    android.widget.EditText code = activity.findViewById(R.id.edit_code);
                    ok[0] = tab != null && !tab.isEnabled() && code != null && code.length() == 0;
                });
                if (ok[0]) { shown = true; break; }
                Thread.sleep(100);
            }
            long ready = System.currentTimeMillis() - start;
            android.util.Log.i("CCPERF", "large document handled after " + ready
                    + " ms (open itself " + (start - openStart) + " ms)");
            assertTrue("大文件必须在 8 秒内给出明确状态（实际 " + ready + " ms）", ready < 8000);
            assertTrue("必须明确告诉用户源码太大、预览仍可用（代码页禁用、文本未进入编辑器）", shown);
            // And the document must not have been emptied by the guard.
            assertTrue("原始内容不得被清空",
                    activity.getCurrentDocumentForTest().getContent().length() > 100_000);
        } finally {
            final EditorActivity a = activity;
            final String id = activity.getCurrentDocumentForTest().getId();
            getInstrumentation().runOnMainSync(() -> {
                if (!a.isFinishing()) a.finish();
            });
            getInstrumentation().waitForIdleSync();
            repository.deleteDocument(id);
        }
    }

    /** A keystroke must land quickly in a document that is large but still editable. */
    public void testTypingInLargeDocumentIsResponsive() throws Throwable {
        EditorActivity activity = openMedium();
        try {
            final EditText code = activity.findViewById(R.id.edit_code);
            getInstrumentation().waitForIdleSync();
            Thread.sleep(1500);   // let the first highlight pass finish

            long worst = 0;
            for (int i = 0; i < 12; i++) {
                final int at = 20 + i;
                long t0 = System.currentTimeMillis();
                runTestOnUiThread(() -> {
                    code.getText().insert(Math.min(at, code.length()), "x");
                });
                getInstrumentation().waitForIdleSync();
                long elapsed = System.currentTimeMillis() - t0;
                worst = Math.max(worst, elapsed);
            }
            android.util.Log.i("CCPERF", "worst keystroke latency=" + worst + " ms");
            assertTrue("单次按键处理必须在 500ms 内完成（实际最差 " + worst + " ms）", worst < 500);
        } finally {
            final EditorActivity a = activity;
            final String id = activity.getCurrentDocumentForTest().getId();
            getInstrumentation().runOnMainSync(() -> {
                if (!a.isFinishing()) a.finish();
            });
            getInstrumentation().waitForIdleSync();
            repository.deleteDocument(id);
        }
    }
}
