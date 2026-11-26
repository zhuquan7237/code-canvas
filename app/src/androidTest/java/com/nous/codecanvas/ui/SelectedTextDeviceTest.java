package com.nous.codecanvas.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.EditText;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SelectedTextDeviceTest extends InstrumentationTestCase {
    private static final String SOURCE = "  <html><body><h1>scratch fixture 089</h1></body></html>\n";
    private Context context;
    private DocumentRepository repository;
    private String fixture;
    private final List<Activity> activities = new ArrayList<>();

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
    }

    @Override protected void tearDown() throws Exception {
        ui(() -> {
            for (Activity activity : activities) {
                if (!activity.isDestroyed() && !activity.isFinishing()) {
                    if (activity.isTaskRoot()) activity.finishAndRemoveTask();
                    else activity.finish();
                }
            }
        });
        getInstrumentation().waitForIdleSync();
        if (fixture != null) {
            for (CanvasDocument document : copies()) repository.deleteDocument(document.getId());
        }
        super.tearDown();
    }

    public void testResolverAndTransientContentAfterPauseRecreationAndClose() throws Exception {
        fixture = SOURCE;
        Intent implicit = new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
        assertTrue(context.getPackageManager().queryIntentActivities(implicit, 0).stream()
                .anyMatch(item -> SelectedTextActivity.class.getName().equals(item.activityInfo.name)));
        Activity first = start(SOURCE, "text/plain");
        ui(() -> {
            TextView source = first.findViewById(R.id.selected_text_source);
            assertEquals(SOURCE, source.getText().toString());
            assertFalse(source.isSaveEnabled());
            assertFalse(source.getFreezesText());
            assertFalse(source.getLinksClickable() && source.getUrls().length > 0);
            assertTrue((first.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            assertFalse(first.getIntent().hasExtra(Intent.EXTRA_PROCESS_TEXT));
        });
        assertTrue(copies().isEmpty());
        Activity recreated = recreate(first);
        ui(() -> assertEquals(SOURCE, ((TextView) recreated.findViewById(R.id.selected_text_source)).getText().toString()));
        SystemClock.sleep(1200);
        assertTrue(copies().isEmpty());
        ui(() -> recreated.findViewById(R.id.btn_close_selected_text).performClick());
        getInstrumentation().waitForIdleSync();
        assertTrue(copies().isEmpty());
    }

    public void testExplicitSaveIsIdempotentAndOpensSourceEditor() throws Exception {
        fixture = SOURCE;
        Instrumentation.ActivityMonitor editorMonitor = monitor(EditorActivity.class);
        try {
            Activity scratch = start(fixture, "text/plain");
            ui(() -> {
                View action = scratch.findViewById(R.id.btn_save_selected_text);
                assertTrue(action.isEnabled());
                action.performClick();
                assertFalse(action.isEnabled());
                action.performClick();
            });
            Activity editor = getInstrumentation().waitForMonitorWithTimeout(editorMonitor, 8000);
            assertNotNull("Saved copy should open the source editor", editor);
            activities.add(editor);
            assertTrue(editor.getIntent().getBooleanExtra(EditorActivity.EXTRA_SELECTED_TEXT, false));
            assertFalse(editor.getIntent().getBooleanExtra(EditorActivity.EXTRA_START_PREVIEW, true));
            assertEquals(1, copies().size());
            assertEquals(copies().get(0).getId(), editor.getIntent().getStringExtra(EditorActivity.EXTRA_DOC_ID));
            assertTrue(copies().get(0).getTitle().endsWith(".html"));
            ui(() -> {
                EditText code = editor.findViewById(R.id.edit_code);
                assertTrue("Actual Code view should be visible", code.isShown());
                assertEquals(SOURCE, code.getText().toString());
            });
        } finally {
            getInstrumentation().removeMonitor(editorMonitor);
        }
    }

    public void testSelectionBelowEditorLimitStillDoesNotAutoOpen() throws Exception {
        verifyLongSelection(16000);
    }

    public void testVeryLongSelectionSavesWithoutOpeningPreview() throws Exception {
        verifyLongSelection(201000);
    }

    private void verifyLongSelection(int length) throws Exception {
        fixture = "<html><body>" + new String(new char[length]).replace('\0','a') + "</body></html>";
        Instrumentation.ActivityMonitor editorMonitor = monitor(EditorActivity.class);
        try {
            Activity scratch = start(fixture, "text/plain");
            ui(() -> {
                TextView excerpt = scratch.findViewById(R.id.selected_text_source);
                String visible = excerpt.getText().toString();
                assertTrue(visible.length() <= 8192);
                assertTrue(fixture.startsWith(visible));
                TextView status = scratch.findViewById(R.id.selected_text_status);
                assertEquals(context.getText(R.string.selection_temporary_excerpt), status.getText());
                scratch.findViewById(R.id.btn_save_selected_text).performClick();
            });
            assertTrue(waitFinishing(scratch));
            assertEquals(0, editorMonitor.getHits());
            assertEquals(1, copies().size());
        } finally {
            getInstrumentation().removeMonitor(editorMonitor);
        }
    }

    public void testRejectsWrongTypeAndMultibyteOverflow() throws Exception {
        Activity bad = startRejected("wrong MIME fixture", "text/html");
        assertTrue(waitFinishing(bad));
        fixture = new String(new char[90000]).replace('\0', '\u8bd5');
        Activity large = startRejected(fixture, "text/plain");
        assertTrue(waitFinishing(large));
        assertTrue(copies().isEmpty());
    }

    private Activity recreate(Activity original) {
        Instrumentation.ActivityMonitor monitor = monitor(SelectedTextActivity.class);
        try {
            ui(original::recreate);
            Activity recreated = getInstrumentation().waitForMonitorWithTimeout(monitor, 8000);
            assertNotNull(recreated);
            assertNotSame(original, recreated);
            activities.add(recreated);
            return recreated;
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private Instrumentation.ActivityMonitor monitor(Class<? extends Activity> type) {
        return getInstrumentation().addMonitor(type.getName(), null, false);
    }

    private Activity start(String content, String mime) {
        Activity started = getInstrumentation().startActivitySync(selectionIntent(content, mime));
        activities.add(started);
        getInstrumentation().waitForIdleSync();
        return started;
    }

    private Activity startRejected(String content, String mime) {
        Instrumentation.ActivityMonitor monitor = monitor(SelectedTextActivity.class);
        try {
            context.startActivity(selectionIntent(content, mime));
            Activity activity = getInstrumentation().waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull(activity);
            activities.add(activity);
            return activity;
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private Intent selectionIntent(String content, String mime) {
        return new Intent(context, SelectedTextActivity.class)
                .setAction(Intent.ACTION_PROCESS_TEXT)
                .setType(mime)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, content)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    private List<CanvasDocument> copies() throws Exception {
        List<CanvasDocument> result = new ArrayList<>();
        for (CanvasDocument document : repository.getAllDocuments()) {
            if (fixture.equals(document.getContent())) result.add(document);
        }
        return result;
    }

    private boolean waitFinishing(Activity activity) {
        AtomicBoolean value = new AtomicBoolean();
        for (int i = 0; i < 100; i++) {
            ui(() -> value.set(activity.isFinishing() || activity.isDestroyed()));
            if (value.get()) return true;
            SystemClock.sleep(60);
        }
        return false;
    }

    private void ui(Runnable action) {
        getInstrumentation().runOnMainSync(action);
    }
}
