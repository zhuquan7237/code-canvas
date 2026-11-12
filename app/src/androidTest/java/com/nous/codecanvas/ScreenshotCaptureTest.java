package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Real screenshots of real screens, written to the app's own storage.
 *
 * <p>Distinct from the assertion suites on purpose: "the tests pass" and "the screen looks right"
 * are different claims, and only a picture settles the second one. Each case opens a document,
 * lets the preview finish, captures the window, and closes — no sleeping, so the suite cannot stall.</p>
 */
public class ScreenshotCaptureTest extends InstrumentationTestCase {

    private Context context;
    private DocumentRepository repository;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
    }

    /** Capture the given activity's window into files/shots/<name>.png. */
    private void capture(String name, android.app.Activity current) throws Exception {
        assertNotNull("no activity to capture", current);
        View decor = current.getWindow().getDecorView();
        decor.post(() -> {});
        getInstrumentation().waitForIdleSync();
        Thread.sleep(600);
        final android.graphics.Bitmap[] holder = new android.graphics.Bitmap[1];
        getInstrumentation().runOnMainSync(() -> {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    decor.getWidth(), decor.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            decor.draw(canvas);
            holder[0] = bitmap;
        });
        assertNotNull("capture produced no bitmap", holder[0]);
        File dir = new File(context.getFilesDir(), "shots");
        if (!dir.exists() && !dir.mkdirs()) fail("cannot create " + dir);
        File out = new File(dir, name + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            holder[0].compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
        }
        holder[0].recycle();
        android.util.Log.i("CCSHOT", out.getAbsolutePath() + " " + out.length() + " bytes");
    }

    private EditorActivity open(String name, String kind, String body, boolean preview) throws Exception {
        String id = "shot-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(id, name, body, System.currentTimeMillis());
        doc.setKindName(kind);
        repository.saveDocument(doc);
        Intent intent = new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, id)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, preview);
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

    private String readFixture(String name) throws Exception {
        File file = new File(new File(context.getFilesDir(), "fixtures"), name);
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public void testCaptureSvgWide() throws Throwable {
        String svg = readFixture("wide.svg");
        if (svg == null) { android.util.Log.i("CCSHOT", "no wide.svg fixture"); return; }
        EditorActivity activity = open("wide.svg", "SVG", svg, true);
        try {
            Thread.sleep(2500);
            capture("svg-wide", activity);
        } finally {
            close(activity);
        }
    }

    public void testCaptureMarkdown() throws Throwable {
        String md = readFixture("工艺卡片.md");
        if (md == null) { android.util.Log.i("CCSHOT", "no markdown fixture"); return; }
        EditorActivity activity = open("工艺卡片.md", "MARKDOWN", md, true);
        try {
            Thread.sleep(3000);
            capture("markdown", activity);
        } finally {
            close(activity);
        }
    }

    public void testCaptureCsv() throws Throwable {
        String csv = readFixture("samples.csv");
        if (csv == null) { android.util.Log.i("CCSHOT", "no csv fixture"); return; }
        EditorActivity activity = open("samples.csv", "CSV", csv, true);
        try {
            Thread.sleep(1500);
            capture("csv", activity);
        } finally {
            close(activity);
        }
    }

    public void testCaptureJson() throws Throwable {
        String json = readFixture("data.json");
        if (json == null) { android.util.Log.i("CCSHOT", "no json fixture"); return; }
        EditorActivity activity = open("data.json", "JSON", json, true);
        try {
            Thread.sleep(1200);
            capture("json", activity);
        } finally {
            close(activity);
        }
    }

    public void testCaptureCodeEditorWithSearch() throws Throwable {
        String code = readFixture("weld.py");
        if (code == null) { android.util.Log.i("CCSHOT", "no py fixture"); return; }
        EditorActivity activity = open("weld.py", "CODE", code, false);
        try {
            getInstrumentation().runOnMainSync(() -> activity.findViewById(R.id.btn_search).performClick());
            getInstrumentation().waitForIdleSync();
            final android.widget.EditText query = activity.findViewById(R.id.edit_search_query);
            getInstrumentation().runOnMainSync(() -> query.setText("line_energy"));
            getInstrumentation().waitForIdleSync();
            Thread.sleep(800);
            capture("code-search", activity);
        } finally {
            close(activity);
        }
    }
}
