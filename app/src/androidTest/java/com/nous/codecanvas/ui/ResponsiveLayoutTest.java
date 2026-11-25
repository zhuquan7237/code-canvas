package com.nous.codecanvas.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.test.InstrumentationTestCase;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.ScrollView;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;

public final class ResponsiveLayoutTest extends InstrumentationTestCase {
    public void testGridUsesAvailableWidthAndGalleryDoesNotClipItsPadding() {
        Context context = getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(() -> {
            View home = LayoutInflater.from(context).inflate(R.layout.activity_main, null);
            GridView grid = home.findViewById(R.id.grid_documents);
            grid.setVisibility(View.VISIBLE);
            grid.measure(exactDp(context, 360), exactDp(context, 420));
            assertEquals(2, grid.getNumColumns());
            grid.measure(exactDp(context, 720), exactDp(context, 240));
            assertEquals(4, grid.getNumColumns());
            assertFalse(grid.getClipToPadding());
            ListView list = home.findViewById(R.id.list_documents);
            assertFalse(list.getClipToPadding());
            assertTrue(home.findViewById(R.id.view_empty) instanceof ScrollView);
            assertEquals(dp(context, 88), list.getPaddingBottom());
        });
    }

    public void testFullscreenKeepsCanvasFlushAfterConfigurationAndRestoresOrientationPolicy()
            throws Exception {
        Context context = getInstrumentation().getTargetContext();
        DocumentRepository repository = new DocumentRepository(context);
        String id = "layout-test-" + System.nanoTime();
        repository.saveDocument(new CanvasDocument(id, "layout.html",
                "<!doctype html><body style='background:coral'>layout</body>",
                System.currentTimeMillis()));
        Intent launch = new Intent(context, EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, id)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(launch);
        try {
            getInstrumentation().runOnMainSync(() -> {
                int originalOrientation = activity.getRequestedOrientation();
                View preview = activity.findViewById(R.id.container_preview_canvas);
                activity.findViewById(R.id.btn_preview_fullscreen).performClick();
                activity.onConfigurationChanged(new Configuration(activity.getResources().getConfiguration()));
                assertEquals(0, preview.getPaddingLeft());
                assertEquals(0, preview.getPaddingBottom());
                assertEquals(View.GONE, activity.findViewById(R.id.header_editor).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.toolbar_preview_controls).getVisibility());
                activity.findViewById(R.id.btn_exit_fullscreen).performClick();
                assertEquals(originalOrientation, activity.getRequestedOrientation());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.header_editor).getVisibility());
                assertTrue(preview.getPaddingLeft() > 0);
            });
        } finally {
            getInstrumentation().runOnMainSync(activity::finish);
            repository.deleteDocument(id);
        }
    }

    private static int exactDp(Context c, int n) {
        return View.MeasureSpec.makeMeasureSpec(dp(c, n), View.MeasureSpec.EXACTLY);
    }

    private static int dp(Context c, int n) {
        return Math.round(n * c.getResources().getDisplayMetrics().density);
    }
}
