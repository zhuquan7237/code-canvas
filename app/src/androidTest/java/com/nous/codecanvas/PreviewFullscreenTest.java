package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.Button;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

public class PreviewFullscreenTest extends InstrumentationTestCase {

    public void testFullscreenToggleAndOrientation() throws Throwable {
        final Context c = getInstrumentation().getTargetContext();
        DocumentRepository repo = new DocumentRepository(c);
        CanvasDocument doc = new CanvasDocument("fs-" + System.nanoTime(), "test.svg",
                "<svg viewBox=\"0 0 1280 800\"><circle/></svg>", System.currentTimeMillis());
        repo.saveDocument(doc);

        Intent intent = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(intent);

        try {
            getInstrumentation().waitForIdleSync();
            final Button btnFullscreen = activity.findViewById(R.id.btn_preview_fullscreen);
            final View btnExitFullscreen = activity.findViewById(R.id.btn_exit_fullscreen);
            final View header = activity.findViewById(R.id.header_editor);

            assertNotNull("fullscreen button must exist in preview controls", btnFullscreen);
            assertNotNull("exit fullscreen button must exist", btnExitFullscreen);

            // Initially not fullscreen
            assertEquals("exit button hidden initially", View.GONE, btnExitFullscreen.getVisibility());
            assertEquals("header visible initially", View.VISIBLE, header.getVisibility());

            // Click fullscreen: turns to landscape and hides chrome
            runTestOnUiThread(btnFullscreen::performClick);
            getInstrumentation().waitForIdleSync();

            assertEquals("exit button visible in fullscreen", View.VISIBLE, btnExitFullscreen.getVisibility());
            assertEquals("header hidden in fullscreen", View.GONE, header.getVisibility());
            assertTrue("requested orientation should be landscape or sensor landscape",
                    activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    || activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

            // Click exit: turns back to portrait and restores chrome
            runTestOnUiThread(btnExitFullscreen::performClick);
            getInstrumentation().waitForIdleSync();

            assertEquals("exit button hidden after exit", View.GONE, btnExitFullscreen.getVisibility());
            assertEquals("header restored after exit", View.VISIBLE, header.getVisibility());
            assertEquals("orientation restored to portrait",
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());

        } finally {
            final EditorActivity toFinish = activity;
            getInstrumentation().runOnMainSync(toFinish::finish);
            repo.deleteDocument(doc.getId());
        }
    }
}
