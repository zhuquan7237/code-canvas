package com.nous.codecanvas;

import android.app.AlertDialog;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.TextView;

import com.nous.codecanvas.ui.CanvasConfirmDialog;

/**
 * A dialog whose message is longer than the screen must keep its buttons reachable.
 *
 * <p>This is not cosmetic. The v0.5.1 update note is seven numbered items; the dialog's message was
 * an unbounded {@code TextView}, so the buttons were pushed below the screen and the only way out
 * was the back key. Found by driving the real update flow, not by a unit test — which is exactly
 * why it is pinned here.</p>
 */
public class ConfirmDialogLayoutTest extends InstrumentationTestCase {

    public void testLongMessageKeepsButtonsOnScreen() throws Throwable {
        final StringBuilder longNote = new StringBuilder("更新说明：\n\n");
        for (int i = 1; i <= 12; i++) {
            longNote.append(i).append(". 这一条故意写得很长，用来把对话框撑到超过屏幕高度，")
                    .append("验证按钮不会被挤出可视区域。\n");
        }

        final android.app.Activity activity = getInstrumentation().startActivitySync(
                new android.content.Intent(getInstrumentation().getTargetContext(),
                        com.nous.codecanvas.ui.MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().waitForIdleSync();
            final AlertDialog[] holder = new AlertDialog[1];
            getInstrumentation().runOnMainSync(() -> holder[0] = CanvasConfirmDialog.show(
                    activity, "更新到 9.9.9", longNote.toString(), "下载并安装", "稍后", false, null, null));
            getInstrumentation().waitForIdleSync();
            final AlertDialog dialog = holder[0];
            assertNotNull(dialog);

            final View positive = CanvasConfirmDialog.confirmButton(dialog);
            final View negative = CanvasConfirmDialog.cancelButton(dialog);
            assertNotNull("positive button must exist", positive);
            assertNotNull("negative button must exist", negative);

            // Measure where the buttons actually land relative to the window.
            final int[] positiveLocation = new int[2];
            final int[] negativeLocation = new int[2];
            final int[] windowSize = new int[2];
            getInstrumentation().runOnMainSync(() -> {
                positive.getLocationInWindow(positiveLocation);
                negative.getLocationInWindow(negativeLocation);
                View decor = dialog.getWindow().getDecorView();
                windowSize[0] = decor.getWidth();
                windowSize[1] = decor.getHeight();
            });
            getInstrumentation().waitForIdleSync();

            android.util.Log.i("CCDIALOG", "window=" + windowSize[0] + "x" + windowSize[1]
                    + " positive=" + positiveLocation[0] + "," + positiveLocation[1]
                    + " negative=" + negativeLocation[0] + "," + negativeLocation[1]);

            assertTrue("对话框必须有实际尺寸", windowSize[1] > 0);
            assertTrue("「下载并安装」按钮必须在屏幕内，实际 y=" + positiveLocation[1]
                            + " 窗口高=" + windowSize[1],
                    positiveLocation[1] > 0 && positiveLocation[1] < windowSize[1]);
            assertTrue("「稍后」按钮必须在屏幕内，实际 y=" + negativeLocation[1],
                    negativeLocation[1] > 0 && negativeLocation[1] < windowSize[1]);
            assertTrue("按钮必须可见", positive.isShown() && negative.isShown());

            // The message itself must be scrollable rather than clipped away entirely.
            final View scroll = dialog.findViewById(R.id.dialog_message_scroll);
            assertNotNull("正文必须有滚动容器", scroll);
            final TextView message = dialog.findViewById(R.id.dialog_message);
            assertTrue("正文必须仍然完整存在", message.getText().length() > 200);

            getInstrumentation().runOnMainSync(dialog::dismiss);
        } finally {
            final android.app.Activity toFinish = activity;
            getInstrumentation().runOnMainSync(() -> {
                if (!toFinish.isFinishing()) toFinish.finish();
            });
            getInstrumentation().waitForIdleSync();
        }
    }

    public void testShortMessageStillShowsButtons() throws Throwable {
        final android.app.Activity activity = getInstrumentation().startActivitySync(
                new android.content.Intent(getInstrumentation().getTargetContext(),
                        com.nous.codecanvas.ui.MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().waitForIdleSync();
            final AlertDialog[] holder = new AlertDialog[1];
            getInstrumentation().runOnMainSync(() -> holder[0] = CanvasConfirmDialog.show(
                    activity, "删除这个文件？", "删除后不能恢复。", "删除", "取消", true, null, null));
            getInstrumentation().waitForIdleSync();
            final AlertDialog dialog = holder[0];
            final View positive = CanvasConfirmDialog.confirmButton(dialog);
            final int[] location = new int[2];
            final int[] windowSize = new int[2];
            getInstrumentation().runOnMainSync(() -> {
                positive.getLocationInWindow(location);
                View decor = dialog.getWindow().getDecorView();
                windowSize[1] = decor.getHeight();
            });
            assertTrue("短消息也必须显示按钮", location[1] > 0 && location[1] < windowSize[1]);
            getInstrumentation().runOnMainSync(dialog::dismiss);
        } finally {
            final android.app.Activity toFinish = activity;
            getInstrumentation().runOnMainSync(() -> {
                if (!toFinish.isFinishing()) toFinish.finish();
            });
            getInstrumentation().waitForIdleSync();
        }
    }
}
