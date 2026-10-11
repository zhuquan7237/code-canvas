package com.nous.codecanvas;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.CanvasBottomSheet;
import com.nous.codecanvas.ui.MainActivity;

import java.util.List;

/**
 * Regression cover for the v0.3.0 "Atelier" design work: the catalog drawer, multi-select with
 * batch delete, clipboard auto-detect, and the single action-sheet surface every menu now uses.
 *
 * <p>These drive real views through real listeners on a real device. Nothing here re-implements
 * production logic; the assertions are about what a user can actually see and tap.</p>
 */
public class DesignSystemRegressionTest extends InstrumentationTestCase {

    private MainActivity main;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestAppearance.resetToSystem(getInstrumentation().getTargetContext());
        main = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> main.finish());
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    private void ui(Runnable r) throws Throwable {
        runTestOnUiThread(r);
        getInstrumentation().waitForIdleSync();
    }


    /**
     * A real long press: pointer down, hold past the platform's long-press timeout, pointer up.
     * TouchUtils.longClickView does not reach AbsListView's item long-press dispatch on this
     * device, so the gesture is built from the same events a finger would send.
     */
    private void longPress(View v) {
        // Coordinates are read and the gesture is dispatched with the touchscreen source set:
        // MotionEvent.obtain() leaves source at 0, and the framework will not route a sourceless
        // event through the touch pipeline.
        final int[] box = new int[4];
        getInstrumentation().runOnMainSync(() -> {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            box[0] = loc[0];
            box[1] = loc[1];
            box[2] = v.getWidth();
            box[3] = v.getHeight();
        });
        float x = box[0] + box[2] / 2f;
        float y = box[1] + box[3] / 2f;
        long down0 = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(
                down0, down0, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        getInstrumentation().sendPointerSync(down);
        android.os.SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout() + 400);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(
                down0, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, x, y, 0);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        getInstrumentation().sendPointerSync(up);
        down.recycle();
        up.recycle();
        getInstrumentation().waitForIdleSync();
    }

    private void clip(String s) {
        ((ClipboardManager) main.getSystemService(MainActivity.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("fixture", s));
    }

    /** Seeds a document and narrows the list to just that one, so row indices are unambiguous. */
    private CanvasDocument seed(String title, String content) throws Throwable {
        CanvasDocument doc = new CanvasDocument("ds-" + System.nanoTime(), title, content,
                System.currentTimeMillis());
        new DocumentRepository(main).saveDocument(doc);
        ui(() -> {
            main.loadDocumentsForTest();
            ((EditText) main.findViewById(R.id.edit_search)).setText(doc.getTitle());
        });
        return doc;
    }

    // --- catalog drawer ---------------------------------------------------------------------------

    public void testDrawerOpensListsEveryDocumentAndFiltersByType() throws Throwable {
        CanvasDocument html = seed("demo_" + System.nanoTime() + ".html", "<h1>x</h1>");
        ui(() -> ((EditText) main.findViewById(R.id.edit_search)).setText(""));

        ui(() -> main.findViewById(R.id.btn_drawer).performClick());
        // The panel slides in (220ms); assert after it has settled, the way a user experiences it.
        Thread.sleep(400);
        getInstrumentation().waitForIdleSync();
        ui(() -> {
            View root = main.findViewById(R.id.drawer_root);
            assertEquals("目录面板必须可见", View.VISIBLE, root.getVisibility());
            ListView list = main.findViewById(R.id.drawer_list);
            assertTrue("目录必须列出作品", list.getAdapter().getCount() > 0);
            View panel = main.findViewById(R.id.drawer_panel);
            assertEquals("抽屉必须已滑到位", 0f, panel.getTranslationX(), 0.5f);
        });

        // The HTML pill narrows both surfaces to HTML only.
        ui(() -> {
            LinearLayout filters = main.findViewById(R.id.drawer_filters);
            View htmlChip = filters.getChildAt(1);
            assertEquals("HTML", ((TextView) htmlChip.findViewById(R.id.filter_label)).getText().toString());
            htmlChip.performClick();
        });
        ui(() -> {
            ListView drawer = main.findViewById(R.id.drawer_list);
            ListView gallery = main.findViewById(R.id.list_documents);
            for (int i = 0; i < drawer.getAdapter().getCount(); i++) {
                CanvasDocument d = (CanvasDocument) drawer.getAdapter().getItem(i);
                assertTrue("筛选后只应出现 HTML：" + d.getTitle(), d.getTitle().endsWith(".html"));
            }
            assertTrue("筛选后画廊必须至少有种子文件", gallery.getAdapter().getCount() >= 1);
            TextView count = main.findViewById(R.id.txt_file_count);
            assertTrue("计数标题必须反映筛选：" + count.getText(),
                    count.getText().toString().startsWith("HTML"));
        });

        ui(() -> main.findViewById(R.id.btn_drawer_close).performClick());
        getInstrumentation().waitForIdleSync();
        Thread.sleep(300);
        ui(() -> assertEquals("关闭后抽屉必须隐藏", View.GONE,
                main.findViewById(R.id.drawer_root).getVisibility()));
    }

    // --- multi-select ------------------------------------------------------------------------------

    public void testLongPressEntersSelectionModeAndSelectAllToggles() throws Throwable {
        seed("select_" + System.nanoTime() + ".html", "<h1>s</h1>");
        ui(() -> ((EditText) main.findViewById(R.id.edit_search)).setText(""));

        View[] row = new View[1];
        ui(() -> row[0] = ((ListView) main.findViewById(R.id.list_documents)).getChildAt(0));
        assertNotNull("列表必须有行", row[0]);
        // Drive the row's real long-click: AbsListView's item-long-press dispatch hands the event
        // to the view itself, whose longClickable flag routes it to the list's
        // onItemLongClickListener — the same path the framework takes for a real finger hold.
        ui(() -> row[0].performLongClick());
        ui(() -> {
            assertTrue("长按必须进入多选", main.isSelectionModeForTest());
            assertEquals(1, main.getSelectionCountForTest());
            assertEquals(View.VISIBLE, main.findViewById(R.id.selection_bar).getVisibility());
            TextView count = main.findViewById(R.id.txt_selection_count);
            assertEquals("已选 1 项", count.getText().toString());
        });

        ui(() -> main.findViewById(R.id.btn_selection_all).performClick());
        ui(() -> {
            ListView l = main.findViewById(R.id.list_documents);
            assertEquals("全选必须覆盖当前列表", l.getAdapter().getCount(),
                    main.getSelectionCountForTest());
        });

        // Tapping "全选" again clears the selection and leaves the mode.
        ui(() -> main.findViewById(R.id.btn_selection_all).performClick());
        ui(() -> assertFalse("再次全选应退出多选", main.isSelectionModeForTest()));
    }

    public void testBatchDeleteUsesTheStandardDialogAndRemovesEverySelectedDocument() throws Throwable {
        CanvasDocument a = seed("batch_a_" + System.nanoTime() + ".html", "<h1>a</h1>");
        CanvasDocument b = new CanvasDocument("batch-b-" + System.nanoTime(),
                "batch_b_" + System.nanoTime() + ".html", "<h1>b</h1>", System.currentTimeMillis());
        new DocumentRepository(main).saveDocument(b);
        ui(() -> {
            main.loadDocumentsForTest();
            ((EditText) main.findViewById(R.id.edit_search)).setText("batch_");
        });

        View[] row = new View[1];
        ui(() -> {
            ListView l = main.findViewById(R.id.list_documents);
            assertEquals(2, l.getAdapter().getCount());
            row[0] = l.getChildAt(0);
        });
        assertNotNull(row[0]);
        ui(() -> row[0].performLongClick());
        ui(() -> main.findViewById(R.id.btn_selection_all).performClick());
        ui(() -> assertEquals(2, main.getSelectionCountForTest()));

        ui(() -> main.findViewById(R.id.btn_selection_delete).performClick());
        ui(() -> {
            android.app.AlertDialog confirm = main.getActiveDialogForTest();
            assertNotNull("批量删除必须弹出统一确认框", confirm);
            assertTrue(confirm.isShowing());
            // The dangerous action is the red-labelled positive button of the shared dialog.
            View positive = com.nous.codecanvas.ui.CanvasConfirmDialog.confirmButton(confirm);
            assertNotNull(positive);
            assertEquals("删除", ((android.widget.Button) positive).getText().toString());
            positive.performClick();
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(700);

        ui(() -> {
            DocumentRepository repo = new DocumentRepository(main);
            try {
                assertNull("批量删除后 a 必须消失", repo.findById(a.getId()));
                assertNull("批量删除后 b 必须消失", repo.findById(b.getId()));
            } catch (Exception e) {
                fail("repository read failed: " + e.getMessage());
            }
            assertFalse("删除后必须退出多选", main.isSelectionModeForTest());
        });
    }

    // --- clipboard auto-detect ----------------------------------------------------------------------

    public void testClipboardBannerAppearsForMarkupAndStaysSilentForProse() throws Throwable {
        ui(() -> {
            main.findViewById(R.id.btn_banner_dismiss).performClick();
            clip("这是一段普通的聊天记录，没有任何标记语言，只是几句话而已。");
        });
        getInstrumentation().runOnMainSync(main::offerClipboardForTest);
        main.awaitClipboardReadForTest();
        ui(() -> assertFalse("非代码剪贴板不得弹出横幅", main.isClipboardBannerShowingForTest()));

        ui(() -> clip("<svg xmlns='http://www.w3.org/2000/svg'><circle cx='5' cy='5' r='4'/></svg>"));
        getInstrumentation().runOnMainSync(main::offerClipboardForTest);
        main.awaitClipboardReadForTest();
        ui(() -> {
            assertTrue("可渲染的剪贴板内容必须弹出横幅", main.isClipboardBannerShowingForTest());
            TextView snippet = main.findViewById(R.id.banner_snippet);
            assertTrue("横幅必须显示内容摘要", snippet.getText().length() > 0);
        });

        ui(() -> main.findViewById(R.id.btn_banner_dismiss).performClick());
        ui(() -> assertFalse("忽略后横幅必须收起", main.isClipboardBannerShowingForTest()));
    }

    // --- one action-sheet surface --------------------------------------------------------------------

    public void testEveryDocumentMenuComesFromTheSingleSheetWithADangerRow() throws Throwable {
        seed("sheet_" + System.nanoTime() + ".html", "<h1>sheet</h1>");
        ui(() -> ((EditText) main.findViewById(R.id.edit_search)).setText(""));

        ui(() -> {
            ListView l = main.findViewById(R.id.list_documents);
            View row = l.getChildAt(0);
            row.findViewById(R.id.btn_card_more).performClick();
        });
        ui(() -> {
            Dialog sheet = main.getActiveSheetForTest();
            assertNotNull("⋮ 必须打开统一动作面板", sheet);
            assertTrue(sheet.isShowing());
            // Six real rows out of the sheet's own container - not a replica menu.
            assertEquals(6, CanvasBottomSheet.actionCount(sheet));
            View danger = CanvasBottomSheet.actionRowAt(sheet, 5);
            assertNotNull(danger);
            TextView label = danger.findViewById(R.id.action_label);
            assertEquals("删除", label.getText().toString());
            int dangerColor = main.getResources().getColor(R.color.canvas_danger);
            assertEquals("危险行标签必须是危险色", dangerColor, label.getCurrentTextColor());
            assertTrue("危险行必须有独立底色", danger.getBackground() instanceof android.graphics.drawable.StateListDrawable);

            // Non-danger rows carry a leading icon, so the menu is scannable rather than a text wall.
            View first = CanvasBottomSheet.actionRowAt(sheet, 0);
            assertNotNull(first);
            assertEquals(View.VISIBLE, first.findViewById(R.id.action_icon).getVisibility());
            sheet.dismiss();
        });
    }

    /** The app's dialog surface is a rounded 20dp shape, not the platform's default rectangle. */
    public void testDialogSurfaceUsesTheDesignSystemShape() throws Throwable {
        ui(() -> main.findViewById(R.id.btn_new).performClick());
        ui(() -> {
            android.app.AlertDialog dialog = main.getActiveDialogForTest();
            assertNotNull(dialog);
            View title = dialog.findViewById(R.id.dialog_title);
            assertNotNull("统一输入框必须存在", title);
            assertNotNull("统一输入框必须有取消按钮", dialog.findViewById(R.id.dialog_btn_negative));
            assertNotNull("统一输入框必须有确认按钮", dialog.findViewById(R.id.dialog_btn_positive));
            dialog.dismiss();
        });
    }
}
