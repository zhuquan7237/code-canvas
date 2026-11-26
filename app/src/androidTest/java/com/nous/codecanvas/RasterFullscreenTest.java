package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.ImageView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.File;
import java.io.FileOutputStream;

public class RasterFullscreenTest extends InstrumentationTestCase {
    public void testTallImageFitsAndZoomControlsStayReachableInFullscreen() throws Throwable {
        verifyImage(200, 1280);
    }

    public void testWideImageFitsAndZoomControlsStayReachableInFullscreen() throws Throwable {
        verifyImage(1280, 200);
    }

    private void verifyImage(int width, int height) throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        File fixture = File.createTempFile("image-view", ".png", c.getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff20698b);
        try (FileOutputStream out = new FileOutputStream(fixture)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally {
            bitmap.recycle();
        }
        FileImporter.Imported imported = FileImporter.importFrom(c, Uri.fromFile(fixture));
        assertEquals("IMAGE", imported.kind.name());
        DocumentRepository repo = new DocumentRepository(c);
        FileImporter.writeBlob(c, imported.document.getId(), imported.bytes);
        repo.saveDocument(imported.document);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(
                new Intent(c, EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EditorActivity.EXTRA_DOC_ID, imported.document.getId())
                    .putExtra(EditorActivity.EXTRA_START_PREVIEW, true));
        try {
            getInstrumentation().waitForIdleSync();
            int orientation = activity.getRequestedOrientation();
            runTestOnUiThread(() -> assertFitted(activity));
            runTestOnUiThread(() -> activity.findViewById(R.id.btn_preview_fullscreen).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertEquals("Photos keep the current rotation policy", orientation, activity.getRequestedOrientation());
                View exit = activity.findViewById(R.id.btn_exit_fullscreen);
                assertTrue(exit.isShown());
                assertNotSame("Fullscreen exit may not overlay the artwork", exit.getParent(),
                        activity.findViewById(R.id.preview_canvas_card));
                assertFitted(activity);
                View plus = activity.findViewById(R.id.btn_viewport_zoom_in);
                View minus = activity.findViewById(R.id.btn_viewport_zoom_out);
                assertTrue(plus.isShown());
                assertTrue(minus.isShown());
                assertTrue(plus.performClick());
                assertTrue(activity.getViewportForTest().getZoomFactor() > 1f);
                assertTrue(minus.performClick());
                assertEquals(1f, activity.getViewportForTest().getZoomFactor(), .02f);
                assertTrue(minus.performClick());
                assertTrue(activity.getViewportForTest().getZoomFactor() < 1f);
                activity.getViewportForTest().pinchForTest(2f, 100f, 100f);
            });
            runTestOnUiThread(() -> {
                assertTrue(activity.getViewportForTest().getZoomFactor() > 1f);
                activity.findViewById(R.id.btn_viewport_fit).performClick();
            });
            android.os.SystemClock.sleep(420);
            runTestOnUiThread(() -> assertFitted(activity));
            runTestOnUiThread(() -> activity.findViewById(R.id.btn_exit_fullscreen).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertEquals(View.GONE, activity.findViewById(R.id.btn_exit_fullscreen).getVisibility());
                assertFitted(activity);
            });
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
            repo.deleteDocument(imported.document.getId());
            fixture.delete();
        }
    }

    private static void assertFitted(EditorActivity editor) {
        ImageView image = editor.findViewById(R.id.preview_image);
        assertTrue(image.isShown());
        assertNotNull(image.getDrawable());
        assertEquals(1f, editor.getViewportForTest().getZoomFactor(), .02f);
        RectF pixels = new RectF(0, 0, image.getDrawable().getIntrinsicWidth(),
                image.getDrawable().getIntrinsicHeight());
        image.getImageMatrix().mapRect(pixels);
        assertTrue("Full raster must fit in both axes",
                pixels.left >= -1 && pixels.top >= -1
                && pixels.right <= image.getWidth() + 1 && pixels.bottom <= image.getHeight() + 1);
        assertTrue("At fit, one axis should extend to the viewport edge",
                Math.abs(pixels.width() - image.getWidth()) <= 1f
                || Math.abs(pixels.height() - image.getHeight()) <= 1f);
    }
}
