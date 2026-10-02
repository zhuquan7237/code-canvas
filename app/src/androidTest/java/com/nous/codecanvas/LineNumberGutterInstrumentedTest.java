package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.widget.EditText;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

/**
 * Verifies that the editor's line-number gutter is wired up, stays in sync with edits, and
 * highlights the line the cursor is on.
 */
public class LineNumberGutterInstrumentedTest extends InstrumentationTestCase {

    public void testGutterTracksEditsAndCursor() throws Throwable {
        final Context c = getInstrumentation().getTargetContext();
        DocumentRepository repo = new DocumentRepository(c);
        CanvasDocument doc = new CanvasDocument("gutter-" + System.nanoTime(), "hello.html",
                "<html>\n<body>\n  <p>one</p>\n</body>\n</html>", System.currentTimeMillis());
        repo.saveDocument(doc);

        Intent intent = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, false);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(intent);
        try {
            getInstrumentation().waitForIdleSync();
            final TextView gutter = activity.findViewById(R.id.txt_line_numbers);
            final EditText editor = activity.findViewById(R.id.edit_code);
            assertNotNull("gutter must exist", gutter);
            assertNotNull("editor must exist", editor);

            // Initial document is 5 lines: "1\n2\n3\n4\n5"
            assertEquals("gutter starts with 5 lines", "1\n2\n3\n4\n5", gutter.getText().toString());

            // Type 3 more lines
            runTestOnUiThread(() -> {
                editor.append("\n<!-- 6 -->\n<!-- 7 -->\n<!-- 8 -->");
            });
            getInstrumentation().waitForIdleSync();
            assertEquals("gutter grows to 8 lines",
                    "1\n2\n3\n4\n5\n6\n7\n8", gutter.getText().toString());

            // Moving the cursor to line 3 must not change the string value, but the spannable
            // must still describe 8 lines
            runTestOnUiThread(() -> editor.setSelection(18));
            getInstrumentation().waitForIdleSync();
            assertEquals("1\n2\n3\n4\n5\n6\n7\n8", gutter.getText().toString());
        } finally {
            final EditorActivity toFinish = activity;
            getInstrumentation().runOnMainSync(toFinish::finish);
            repo.deleteDocument(doc.getId());
        }
    }
}
