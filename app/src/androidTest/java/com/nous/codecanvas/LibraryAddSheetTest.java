package com.nous.codecanvas;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.TextView;

import com.nous.codecanvas.ui.CanvasBottomSheet;
import com.nous.codecanvas.ui.MainActivity;

/** Use the real + affordance, native naming dialog, and Storage Access Framework request. */
public class LibraryAddSheetTest extends InstrumentationTestCase {
    private MainActivity main;

    @Override protected void setUp() throws Exception {
        super.setUp();
        main = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }

    @Override protected void tearDown() throws Exception {
        if (main != null) getInstrumentation().runOnMainSync(main::finish);
        super.tearDown();
    }

    public void testCreationSheetNamesTwoActionsAndDismisses() throws Throwable {
        openSheet();
        runTestOnUiThread(() -> {
            Dialog sheet = main.getActiveSheetForTest();
            int touch = main.getResources().getDimensionPixelSize(R.dimen.touch_min);
            assertEquals(2, CanvasBottomSheet.actionCount(sheet));
            assertEquals("添加到作品库", ((TextView) sheet.findViewById(R.id.sheet_title)).getText());
            for (int i = 0; i < 2; i++) {
                View action = CanvasBottomSheet.actionRowAt(sheet, i);
                assertTrue("Rows expose full descriptive touch targets", action.getHeight() >= touch
                        && action.getWidth() >= touch && action.isFocusable());
                assertTrue(action.getContentDescription().length() > 8);
                assertEquals("android.widget.Button", action.createAccessibilityNodeInfo().getClassName());
            }
            View close = sheet.findViewById(R.id.sheet_handle);
            assertTrue(close.getHeight() >= touch && close.getWidth() >= touch);
            close.performClick();
            assertFalse(sheet.isShowing());
        });
    }

    public void testAddNewRoutesToExistingNamingDialog() throws Throwable {
        openSheet();
        runTestOnUiThread(() -> {
            Dialog sheet = main.getActiveSheetForTest();
            CanvasBottomSheet.actionRowAt(sheet, 0).performClick();
            assertFalse(sheet.isShowing());
            assertNotNull(main.getActiveDialogForTest());
            assertTrue(main.getActiveDialogForTest().isShowing());
            assertNotNull(main.getActiveDialogForTest().findViewById(R.id.dialog_edit_input));
        });
    }

    public void testAddImportUsesSystemFilePicker() throws Throwable {
        IntentFilter picker = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.addDataType("*/*");
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(picker,
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        try {
            openSheet();
            runTestOnUiThread(() -> {
                Dialog sheet = main.getActiveSheetForTest();
                CanvasBottomSheet.actionRowAt(sheet, 1).performClick();
                assertFalse(sheet.isShowing());
            });
            assertEquals(1, monitor.getHits());
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    private void openSheet() throws Throwable {
        runTestOnUiThread(() -> main.findViewById(R.id.btn_add_menu).performClick());
        getInstrumentation().waitForIdleSync();
        assertNotNull(main.getActiveSheetForTest());
        assertTrue(main.getActiveSheetForTest().isShowing());
    }
}
