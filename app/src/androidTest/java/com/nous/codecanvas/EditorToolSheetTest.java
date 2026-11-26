package com.nous.codecanvas;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.CanvasBottomSheet;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.util.CanvasPrefs;

/** The actual overflow sheet: desktop-sized labels are not a substitute for real Android targets. */
public class EditorToolSheetTest extends InstrumentationTestCase {
    private Context context;
    private DocumentRepository repository;
    private CanvasDocument document;
    private EditorActivity editor;
    private boolean originalScripts;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
        originalScripts = CanvasPrefs.scriptsAllowed(context);
        CanvasPrefs.setScriptsAllowed(context, true);
        document = new CanvasDocument("editor-tools-" + System.nanoTime(), "tools.html",
                "<!doctype html><p>Editor tool marker</p>", System.currentTimeMillis());
        repository.saveDocument(document);
    }

    @Override protected void tearDown() throws Exception {
        if (editor != null) getInstrumentation().runOnMainSync(editor::finish);
        if (context != null) CanvasPrefs.setScriptsAllowed(context, originalScripts);
        if (repository != null && document != null) repository.deleteDocument(document.getId());
        super.tearDown();
    }

    public void testCodeSheetHasAccessibleTargetsAndFindWorks() throws Throwable {
        open(false);
        showTools();
        runTestOnUiThread(() -> {
            Dialog sheet = editor.getActiveSheetForTest();
            assertTrue(sheet.isShowing());
            int target = editor.getResources().getDimensionPixelSize(R.dimen.touch_min);
            for (int id : new int[]{R.id.btn_editor_overflow, R.id.btn_save}) {
                View button = editor.findViewById(id);
                assertTrue(button.getWidth() >= target && button.getHeight() >= target);
                assertEquals(R.id.editor_header_actions, ((View) button.getParent()).getId());
            }
            View find = action(sheet, R.string.editor_action_find);
            assertNotNull(find);
            assertTrue(find.getHeight() >= target);
            assertEquals("android.widget.Button", find.createAccessibilityNodeInfo().getClassName());
            assertNull(action(sheet, R.string.editor_action_fullscreen));
            find.performClick();
            assertFalse(sheet.isShowing());
            assertEquals(View.VISIBLE, editor.findViewById(R.id.search_bar).getVisibility());
        });
    }

    public void testPreviewShowsActualScriptStateAndKeepsNetworkAction() throws Throwable {
        open(true);
        showTools();
        runTestOnUiThread(() -> {
            Dialog sheet = editor.getActiveSheetForTest();
            assertNotNull(action(sheet, R.string.editor_action_refresh));
            assertNotNull(action(sheet, R.string.editor_action_fullscreen));
            assertTrue(action(sheet, R.string.editor_action_offline) != null
                    || action(sheet, R.string.editor_action_online) != null);
            View disable = action(sheet, R.string.editor_action_scripts_off);
            assertNotNull(disable);
            assertTrue(disable.getContentDescription().toString()
                    .contains(editor.getString(R.string.editor_action_script_enabled)));
            disable.performClick();
        });
        showTools();
        runTestOnUiThread(() -> {
            Dialog sheet = editor.getActiveSheetForTest();
            View enable = action(sheet, R.string.editor_action_scripts_on);
            assertNotNull(enable);
            assertTrue(enable.getContentDescription().toString()
                    .contains(editor.getString(R.string.editor_action_script_disabled)));
            sheet.findViewById(R.id.sheet_handle).performClick();
            assertFalse(sheet.isShowing());
        });
    }

    private void open(boolean preview) {
        Intent intent = new Intent(context, EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, document.getId())
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, preview);
        editor = (EditorActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
    }

    private void showTools() throws Throwable {
        runTestOnUiThread(() -> editor.findViewById(R.id.btn_editor_overflow).performClick());
        getInstrumentation().waitForIdleSync();
    }

    private View action(Dialog sheet, int label) {
        String expected = editor.getString(label);
        for (int i=0; i<CanvasBottomSheet.actionCount(sheet); i++) {
            View row = CanvasBottomSheet.actionRowAt(sheet, i);
            TextView text = row.findViewById(R.id.action_label);
            if (expected.contentEquals(text.getText())) return row;
        }
        return null;
    }
}
