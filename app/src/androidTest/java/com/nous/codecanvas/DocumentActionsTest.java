package com.nous.codecanvas;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.ListView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The list's long-press actions.
 *
 * <p>Long-press is easy to get subtly wrong on a {@code ListView}: a clickable child swallows the
 * touch stream, so the list never classifies the gesture as a long press and the menu silently
 * never appears — which looks identical to "the feature was never built". These tests drive the
 * real items and assert that long-press is wired on the list itself.</p>
 */
public class DocumentActionsTest extends InstrumentationTestCase {

    private MainActivity home;
    private final List<String> created = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestAppearance.resetToSystem(getInstrumentation().getTargetContext());
        DocumentRepository repo = new DocumentRepository(getInstrumentation().getTargetContext());
        String id = "actions-" + System.nanoTime();
        created.add(id);
        repo.saveDocument(new CanvasDocument(id, "长按测试.html",
                "<h1>长按我</h1>", System.currentTimeMillis()));

        home = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        DocumentRepository repo = new DocumentRepository(getInstrumentation().getTargetContext());
        for (String id : created) {
            repo.deleteDocument(id);
        }
        final MainActivity toFinish = home;
        getInstrumentation().runOnMainSync(() -> {
            if (!toFinish.isFinishing()) toFinish.finish();
        });
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    /** The list itself must own the long-press handler, otherwise the gesture never reaches it. */
    public void testListOwnsALongPressHandler() throws Throwable {
        runTestOnUiThread(() -> {
            ListView list = home.findViewById(R.id.list_documents);
            assertTrue("主页列表必须注册长按处理", list.isLongClickable());
        });
    }

    /** A row must still respond to a direct click, so tapping a card keeps opening the editor. */
    public void testRowStillStartsTheEditorOnClick() throws Throwable {
        final ListView list = home.findViewById(R.id.list_documents);
        assertNotNull("主页列表必须存在", list);
        assertTrue("列表必须至少有一行", list.getAdapter().getCount() > 0);

        getInstrumentation().waitForIdleSync();
        final View[] row = {null};
        runTestOnUiThread(() -> row[0] = list.getChildAt(0));
        assertNotNull("列表必须已经绑定出第一行", row[0]);

        final boolean[] clicked = {false};
        runTestOnUiThread(() -> {
            clicked[0] = row[0].performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertTrue("点击卡片行必须被消费（用于打开编辑器）", clicked[0]);
    }
}
