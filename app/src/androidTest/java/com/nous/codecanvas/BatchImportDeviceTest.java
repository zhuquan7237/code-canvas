package com.nous.codecanvas;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.data.ImportBatch;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.ui.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BatchImportDeviceTest extends InstrumentationTestCase {
    private Context context;
    private MainActivity activity;
    private File fixture;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) {
            getInstrumentation().runOnMainSync(() -> {
                if (!activity.isDestroyed()) activity.finishAndRemoveTask();
            });
            getInstrumentation().waitForIdleSync();
        }
        if (fixture != null) {
            for (CanvasDocument doc : copies()) new DocumentRepository(context).deleteDocument(doc.getId());
            fixture.delete();
        }
        super.tearDown();
    }

    private Intent share(String mime, Uri... items) {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (items.length > 0) {
            ClipData clips = ClipData.newRawUri("files", items[0]);
            ArrayList<Uri> uris = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                if (i > 0) clips.addItem(new ClipData.Item(items[i]));
                uris.add(items[i]);
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.setClipData(clips);
        }
        return intent;
    }

    private boolean isOffered(String mime) {
        for (ResolveInfo r : context.getPackageManager().queryIntentActivities(share(mime),
                PackageManager.MATCH_DEFAULT_ONLY)) {
            if (r.activityInfo != null && context.getPackageName().equals(r.activityInfo.packageName)
                    && MainActivity.class.getName().equals(r.activityInfo.name)) return true;
        }
        return false;
    }

    public void testImplicitResolverOffersMixedAndImageButNotVideoOrZip() {
        assertTrue(isOffered("*/*"));
        assertTrue(isOffered("image/*"));
        assertTrue(isOffered("application/octet-stream"));
        assertTrue(isOffered("application/x-ipynb+json"));
        assertFalse(isOffered("video/mp4"));
        assertFalse(isOffered("application/zip"));
    }

    public void testPickerClipDataAndShareDeduplicationAndBounds() {
        ArrayList<Uri> items = new ArrayList<>();
        for (int i = 0; i < ImportBatch.MAX_FILES + 2; i++) {
            items.add(Uri.parse("content://test.fixture/" + i));
        }
        items.add(items.get(1));
        items.add(Uri.parse("file:///not-a-picker-file"));
        items.add(Uri.parse("https://example.org/not-a-picker-file"));
        ClipData clip = ClipData.newRawUri("files", items.get(0));
        for (int i = 1; i < items.size(); i++) clip.addItem(new ClipData.Item(items.get(i)));
        Intent clipOnly = new Intent();
        clipOnly.setClipData(clip);
        ImportBatch picker = ImportBatch.fromPicker(clipOnly);
        assertTrue(picker.grouped);
        assertEquals(items.subList(0, ImportBatch.MAX_FILES), picker.files);
        assertEquals(1, picker.duplicates);
        assertEquals(2, picker.invalid);
        assertEquals(2, picker.overflow);
        ImportBatch single = ImportBatch.fromPicker(new Intent().setData(items.get(0)));
        assertFalse(single.grouped);
        assertEquals(1, single.files.size());
        assertEquals(picker.files, ImportBatch.fromShare(clipOnly).files);
        ImportBatch fromBoth = ImportBatch.fromShare(share("*/*", items.get(0), items.get(1), items.get(0)));
        assertEquals(2, fromBoth.files.size());
        assertEquals(1, fromBoth.duplicates);
        ArrayList<Uri> brokenStream = new ArrayList<>();
        brokenStream.add(null);
        Intent mixedSources = new Intent().putParcelableArrayListExtra(Intent.EXTRA_STREAM, brokenStream);
        mixedSources.setClipData(ClipData.newRawUri("actual file", items.get(1)));
        ImportBatch fallback = ImportBatch.fromShare(mixedSources);
        assertEquals(java.util.Collections.singletonList(items.get(1)), fallback.files);
        assertEquals(java.util.Collections.singletonList(1), fallback.positions);
        assertEquals(1, fallback.invalid);
    }

    private AccessibilityNodeInfo node(AccessibilityNodeInfo root, String text) {
        if (root == null) return null;
        CharSequence found = root.getText();
        if (found != null && found.toString().contains(text)) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo match = node(root.getChild(i), text);
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo awaitNode(String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo match = node(getInstrumentation().getUiAutomation().getRootInActiveWindow(), text);
            if (match != null) return match;
            SystemClock.sleep(120);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private List<CanvasDocument> copies() throws Exception {
        List<CanvasDocument> matches = new ArrayList<>();
        for (CanvasDocument file : new DocumentRepository(context).getAllDocuments()) {
            if (fixture.getName().equals(file.getTitle())) matches.add(file);
        }
        return matches;
    }

    public void testMixedOutcomeSurvivesRecreationWithoutOpeningEditorsOrLosingFilter() throws Exception {
        File shares = new File(context.getCacheDir(), "shares");
        assertTrue(shares.isDirectory() || shares.mkdirs());
        fixture = new File(shares, "batch-" + System.nanoTime() + ".txt");
        try (FileOutputStream out = new FileOutputStream(fixture)) {
            out.write("second independent artifact".getBytes(StandardCharsets.UTF_8));
        }
        Uri delayed = Uri.parse("content://com.nous.codecanvas.debug.test.intake.fixture/" + fixture.getName());
        Uri sameNameDifferentUri = Uri.parse("content://" + context.getPackageName() + ".fileprovider/" + fixture.getName());
        Uri unavailable = Uri.parse("content://" + context.getPackageName() + ".fileprovider/missing-" + fixture.getName());
        context.getContentResolver().call(delayed, "reset", null, null);
        Instrumentation.ActivityMonitor editors = getInstrumentation().addMonitor(EditorActivity.class.getName(), null, false);
        activity = (MainActivity) getInstrumentation().startActivitySync(
                share("*/*", delayed, sameNameDifferentUri, delayed, unavailable)
                        .setClass(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        // The test fixture is already shared to the test package and doesn't grant sub-URIs.
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            long end = SystemClock.uptimeMillis() + 7000;
            while (readers(delayed) == 0 && SystemClock.uptimeMillis() < end) SystemClock.sleep(75);
            assertEquals(1, readers(delayed));
            assertEquals(0, copies().size());
            Instrumentation.ActivityMonitor homes = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
            try {
                getInstrumentation().runOnMainSync(activity::recreate);
                MainActivity replacement = (MainActivity) getInstrumentation().waitForMonitorWithTimeout(homes, 10000);
                assertNotNull(replacement);
                assertNotSame(activity, replacement);
                activity = replacement;
            } finally {
                getInstrumentation().removeMonitor(homes);
            }
            getInstrumentation().runOnMainSync(() -> search().setText("hidden-by-active-search"));
            assertEquals(1, readers(delayed));
        } finally {
            context.getContentResolver().call(delayed, "release", null, null);
        }
        try {
            assertNotNull(awaitNode("批量导入结果", 12000));
            assertNotNull(awaitNode("已保存 2 个副本", 5000));
            assertNotNull(awaitNode("1 个文件未能导入", 3000));
            assertNotNull(awaitNode("已跳过 1 个重复项", 3000));
            assertNotNull(awaitNode("第 4 项：", 3000));
            assertEquals(0, editors.getHits());
            assertEquals(1, readers(delayed));
            List<CanvasDocument> files = copies();
            assertEquals(2, files.size());
            assertFalse(files.get(0).getId().equals(files.get(1).getId()));
            boolean second = false, first = false;
            for (CanvasDocument file : files) {
                assertNull(file.getSourceUri());
                first |= "finished after recreation".equals(file.getContent());
                second |= "second independent artifact".equals(file.getContent());
            }
            assertTrue(first && second);
            AccessibilityNodeInfo clear = awaitNode("清除筛选", 4000);
            assertNotNull(clear);
            assertTrue(clear.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> assertEquals("", search().getText().toString()));
            assertNotNull(awaitNode(fixture.getName(), 6000));
            assertNull(awaitNode("批量导入结果", 600));
            assertEquals(0, editors.getHits());
        } finally {
            getInstrumentation().removeMonitor(editors);
        }
    }

    private EditText search() { return activity.findViewById(R.id.edit_search); }

    private int readers(Uri uri) {
        android.os.Bundle state = context.getContentResolver().call(uri, "status", null, null);
        return state == null ? -1 : state.getInt("readers");
    }
}
