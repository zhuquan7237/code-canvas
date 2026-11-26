package com.nous.codecanvas;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;

public class LibraryFilterRecoveryTest extends InstrumentationTestCase {
    public void testNoResultsOffersImmediateRecoveryAndSearchBackDoesNotExit() throws Throwable {
        android.content.Context context = getInstrumentation().getTargetContext();
        DocumentRepository repo = new DocumentRepository(context);
        String id = "filter-recovery-" + System.nanoTime();
        repo.saveDocument(new CanvasDocument(id, id + ".xml", "<ok/>", System.currentTimeMillis()));
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        EditText search = main.findViewById(R.id.edit_search);
        View noMatchReset = main.findViewById(R.id.btn_empty_reset);
        try {
            runTestOnUiThread(() -> {
                main.findViewById(R.id.btn_layout_list).performClick();
                main.loadDocumentsForTest();
                search.setText("no-match-" + id.substring("filter-recovery-".length()));
                // Explicit IME submit must apply the current query without waiting for debounce.
                search.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                assertEquals(View.VISIBLE, noMatchReset.getVisibility());
                assertEquals(0, ((ListView) main.findViewById(R.id.list_documents))
                        .getAdapter().getCount());
                assertTrue(noMatchReset.performClick());
                assertEquals(0, search.length());
                assertEquals(View.GONE, noMatchReset.getVisibility());
                assertTrue(((ListView) main.findViewById(R.id.list_documents))
                        .getAdapter().getCount() > 0);
                search.setText("not-found");
                search.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            });
            getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertFalse("Back must clear search before leaving", main.isFinishing());
                assertEquals(0, search.length());
                assertEquals(View.GONE, noMatchReset.getVisibility());
            });
        } finally {
            getInstrumentation().runOnMainSync(main::finish);
            repo.deleteDocument(id);
        }
    }
}
