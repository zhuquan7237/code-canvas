package com.nous.codecanvas;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SharedFileIntakeTest extends InstrumentationTestCase {
    private Context context;
    private DocumentRepository repository;
    private File source;
    private Uri uri;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
        File shares = new File(context.getCacheDir(), "shares");
        assertTrue(shares.isDirectory() || shares.mkdirs());
        source = new File(shares, "intake-" + System.nanoTime() + ".txt");
        uri = new Uri.Builder().scheme("content").authority(context.getPackageName() + ".fileprovider")
                .appendPath(source.getName()).build();
    }

    @Override protected void tearDown() throws Exception {
        if (source != null) {
            for (CanvasDocument doc : copies()) repository.deleteDocument(doc.getId());
            source.delete();
        }
        super.tearDown();
    }

    private void bytes(String s) throws Exception {
        try (FileOutputStream out = new FileOutputStream(source, false)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private Intent fileIntent(String action) {
        Intent intent = new Intent(action).setClass(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Intent.ACTION_VIEW.equals(action)) intent.setDataAndType(uri, "text/plain");
        else intent.setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .setClipData(ClipData.newRawUri("file", uri));
        return intent;
    }

    private List<CanvasDocument> copies() throws Exception {
        List<CanvasDocument> result = new ArrayList<>();
        for (CanvasDocument doc : repository.getAllDocuments()) {
            if (source.getName().equals(doc.getTitle())) result.add(doc);
        }
        return result;
    }

    private List<CanvasDocument> awaitCopies(int count) throws Exception {
        long end = SystemClock.uptimeMillis() + 12000;
        while (SystemClock.uptimeMillis() < end) {
            List<CanvasDocument> docs = copies();
            if (docs.size() == count) return docs;
            SystemClock.sleep(100);
        }
        fail("Expected " + count + " local copies, saw " + copies().size());
        return new ArrayList<>();
    }

    private boolean hasText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (text.contentEquals(node.getText() == null ? "" : node.getText())) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (hasText(node.getChild(i), text)) return true;
        }
        return false;
    }

    private boolean awaitText(String text, long millis) {
        long end = SystemClock.uptimeMillis() + millis;
        do {
            if (hasText(getInstrumentation().getUiAutomation().getRootInActiveWindow(), text)) return true;
            SystemClock.sleep(150);
        } while (SystemClock.uptimeMillis() < end);
        return false;
    }

    private MainActivity recreate(MainActivity activity) {
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(activity::recreate);
            MainActivity replaced = (MainActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull(replaced);
            assertNotSame(activity, replaced);
            return replaced;
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    public void testFreshSameUriViewAndSharePreserveLocalEdit() throws Exception {
        bytes("first export");
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(
                fileIntent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        CanvasDocument local = awaitCopies(1).get(0);
        assertEquals("first export", local.getContent());
        assertTrue(awaitText(source.getName(), 5000));
        local.setContent("edited local copy");
        repository.saveDocument(local);
        bytes("second export");
        Instrumentation.ActivityMonitor editorMonitor =
                getInstrumentation().addMonitor(EditorActivity.class.getName(), null, false);
        try {
            context.startActivity(fileIntent(Intent.ACTION_SEND));
            List<CanvasDocument> found = awaitCopies(2);
            CanvasDocument fresh = null;
            for (CanvasDocument doc : found) if (!local.getId().equals(doc.getId())) fresh = doc;
            assertNotNull(fresh);
            assertEquals("second export", fresh.getContent());
            assertEquals("edited local copy", repository.findById(local.getId()).getContent());
            EditorActivity preview = (EditorActivity) getInstrumentation().waitForMonitorWithTimeout(editorMonitor, 8000);
            assertNotNull(preview);
            assertEquals(fresh.getId(), preview.getIntent().getStringExtra(EditorActivity.EXTRA_DOC_ID));
        } finally {
            getInstrumentation().removeMonitor(editorMonitor);
        }
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        assertFalse(main.isDestroyed());
        recreate(main);
        SystemClock.sleep(700);
        assertEquals(2, copies().size());
    }

    public void testRetrySameUriAfterProviderFileBecomesReadable() throws Exception {
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(
                fileIntent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String errorTitle = "\u672a\u80fd\u5bfc\u5165\u6587\u4ef6";
        assertTrue(awaitText(errorTitle, 8000));
        recreate(main);
        assertTrue(awaitText(errorTitle, 8000));
        assertTrue(awaitText("\u91cd\u65b0\u9009\u62e9", 3000));
        assertEquals(0, copies().size());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        bytes("readable after a new delivery");
        context.startActivity(fileIntent(Intent.ACTION_VIEW));
        assertEquals("readable after a new delivery", awaitCopies(1).get(0).getContent());
    }

    public void testDismissedTextShareIsNotReplayedByRecreation() throws Exception {
        String raw = "Two examples\n```python\nprint(3)\n```\n```json\n{}\n```";
        Intent share = new Intent(Intent.ACTION_SEND).setClass(context, MainActivity.class)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(share).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String retain = "\u4fdd\u7559\u5b8c\u6574\u539f\u6587";
        assertTrue(awaitText(retain, 8000));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        assertFalse(awaitText(retain, 500));
        recreate(main);
        assertFalse(awaitText(retain, 800));
        context.startActivity(share);
        assertTrue(awaitText(retain, 8000));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }
    public void testRecreateDuringAReadDeliversOneCopyAndOpensPreview() throws Exception {
        uri = new Uri.Builder().scheme("content").authority("com.nous.codecanvas.debug.test.intake.fixture")
                .appendPath(source.getName()).build();
        context.getContentResolver().call(uri, "reset", null, null);
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(
                fileIntent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            long end = SystemClock.uptimeMillis() + 5000;
            while (readingCount() == 0 && SystemClock.uptimeMillis() < end) SystemClock.sleep(75);
            assertEquals(1, readingCount());
            assertEquals(0, copies().size());
            recreate(main);
            assertEquals(1, readingCount());
            assertEquals(0, copies().size());
        } finally {
            context.getContentResolver().call(uri, "release", null, null);
        }
        assertEquals("finished after recreation", awaitCopies(1).get(0).getContent());
        assertTrue(awaitText("\u9884\u89c8", 8000));
        assertTrue(awaitText(source.getName(), 5000));
        SystemClock.sleep(600);
        assertEquals(1, copies().size());
    }

    private int readingCount() {
        android.os.Bundle state = context.getContentResolver().call(uri, "status", null, null);
        return state == null ? -1 : state.getInt("readers");
    }

}
