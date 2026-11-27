package com.nous.codecanvas;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentKind;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class LibraryFilterDeviceTest extends InstrumentationTestCase {
    private static final String MARKER = "qa079categories";
    private Context context;
    private DocumentRepository repository;
    private MainActivity home;
    private CanvasDocument csv, json, notebook, legacy;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
        csv = save(".csv", "name,value\nitem,42\n", DocumentKind.CSV);
        json = save(".json", "{\"value\":42}", DocumentKind.JSON);
        notebook = save(".ipynb", "{\"nbformat\":4,\"cells\":[]}", DocumentKind.NOTEBOOK);
        legacy = save("-legacy.ipynb", "old unspecialized notebook record", null);
        assertEquals(DocumentKind.TEXT, legacy.kind());
        home = (MainActivity) getInstrumentation().startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitFixtures(R.id.list_documents, csv, json, notebook, legacy);
    }

    @Override protected void tearDown() throws Exception {
        ui(() -> {
            if (home != null && !home.isDestroyed()) home.finishAndRemoveTask();
        });
        for (CanvasDocument fixture : Arrays.asList(csv, json, notebook, legacy)) {
            if (fixture != null) repository.deleteDocument(fixture.getId());
        }
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    public void testCatalogDisclosesAndClearsInheritedSearchAndCategory() {
        choose(R.id.btn_library_filter, R.string.library_category_data);
        awaitFixtures(R.id.list_documents, csv, json);
        setSearch("nonexistent079");
        ui(() -> home.findViewById(R.id.btn_drawer).performClick());
        awaitFixtures(R.id.drawer_list);
        ui(() -> {
            TextView query = home.findViewById(R.id.drawer_active_query);
            assertTrue(query.isShown());
            assertTrue(query.getText().toString().contains("nonexistent079"));
            assertTrue(((TextView) home.findViewById(R.id.drawer_subtitle)).getText().toString().endsWith("0"));
            assertTrue(home.findViewById(R.id.btn_drawer_reset).isShown());
        });
        // All is category-only: the retained search remains visible and active.
        choose(R.id.btn_drawer_filter, R.string.library_category_all);
        awaitFixtures(R.id.drawer_list);
        ui(() -> assertTrue(home.findViewById(R.id.drawer_active_query).isShown()));
        ui(() -> home.findViewById(R.id.btn_drawer_reset).performClick());
        awaitFixtures(R.id.drawer_list, csv, json, notebook, legacy);
        awaitFixtures(R.id.list_documents, csv, json, notebook, legacy);
        ui(() -> {
            assertEquals(View.VISIBLE, home.findViewById(R.id.drawer_root).getVisibility());
            assertEquals(View.GONE, home.findViewById(R.id.drawer_active_query).getVisibility());
            assertEquals(View.GONE, home.findViewById(R.id.btn_drawer_reset).getVisibility());
            assertEquals("", ((EditText) home.findViewById(R.id.edit_search)).getText().toString());
        });
    }

    public void testRecordedNotebookAndLegacyTextStayDistinctAfterRecreation() {
        setSearch(MARKER);
        choose(R.id.btn_library_filter, R.string.library_category_notebooks);
        awaitFixtures(R.id.list_documents, notebook);
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(
                MainActivity.class.getName(), null, false);
        try {
            MainActivity original = home;
            ui(original::recreate);
            Activity recreated = getInstrumentation().waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull(recreated);
            assertNotSame(original, recreated);
            home = (MainActivity) recreated;
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
        awaitFixtures(R.id.list_documents, notebook);
        ui(() -> home.findViewById(R.id.btn_drawer).performClick());
        choose(R.id.btn_drawer_filter, R.string.library_category_source);
        awaitFixtures(R.id.drawer_list, legacy);
        awaitFixtures(R.id.list_documents, legacy);
        ui(() -> {
            assertTrue(((TextView) home.findViewById(R.id.drawer_active_query)).getText().toString().contains(MARKER));
            assertEquals(1, ((ListView) home.findViewById(R.id.drawer_list)).getAdapter().getCount());
        });
    }

    private CanvasDocument save(String suffix, String body, DocumentKind kind) throws Exception {
        CanvasDocument doc = new CanvasDocument(UUID.randomUUID().toString(), MARKER + suffix,
                body, System.currentTimeMillis());
        if (kind != null) doc.setKindName(kind.name());
        repository.saveDocument(doc);
        return doc;
    }

    private void choose(int anchor, int label) {
        String expected = home.getString(label);
        ui(() -> assertTrue(home.findViewById(anchor).performClick()));
        for (int i = 0; i < 75; i++) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(expected)) {
                    if (expected.contentEquals(node.getText())) {
                        for (AccessibilityNodeInfo candidate = node; candidate != null;
                             candidate = candidate.getParent()) {
                            if (candidate.isClickable()
                                    && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                getInstrumentation().waitForIdleSync();
                                return;
                            }
                        }
                    }
                }
            }
            SystemClock.sleep(120);
        }
        fail("Could not select format: " + expected);
    }

    private void setSearch(String query) {
        ui(() -> ((EditText) home.findViewById(R.id.edit_search)).setText(query));
    }

    private void awaitFixtures(int listId, CanvasDocument... wanted) {
        Set<String> expected = new HashSet<>();
        for (CanvasDocument fixture : wanted) expected.add(fixture.getId());
        Set<String> observed = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            ui(() -> {
                observed.clear();
                ListView list = home.findViewById(listId);
                for (int row = 0; row < list.getAdapter().getCount(); row++) {
                    CanvasDocument doc = (CanvasDocument) list.getAdapter().getItem(row);
                    if (doc.getTitle().startsWith(MARKER)) observed.add(doc.getId());
                }
            });
            if (expected.equals(observed)) return;
            SystemClock.sleep(140);
        }
        assertEquals(expected, observed);
    }

    private void ui(Runnable callback) {
        getInstrumentation().runOnMainSync(callback);
    }
}
