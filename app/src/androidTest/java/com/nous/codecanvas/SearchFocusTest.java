package com.nous.codecanvas;

import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.widget.EditText;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

/**
 * Typing in the search box must never take focus away from it, and must never change the document.
 *
 * <p>The reported symptom was "只能输入一个字母就退出输入状态，而且退输入状态会直接修改 svg 代码".
 * Both halves come from one line: revealing a match called {@code editCode.requestFocus()}, which
 * moved focus (and the IME) into the code editor — so the next keystroke was inserted into the
 * document instead of the query. That is a data-corruption path, so it is pinned here.</p>
 */
public class SearchFocusTest extends InstrumentationTestCase {

    private static final String SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\">\n"
            + "  <rect x=\"10\" y=\"10\" width=\"100\" height=\"100\" fill=\"#2456C6\"/>\n"
            + "  <circle cx=\"200\" cy=\"150\" r=\"60\" fill=\"#B42318\"/>\n"
            + "  <text x=\"20\" y=\"280\">line_energy</text>\n"
            + "  <text x=\"20\" y=\"260\">line_energy again</text>\n"
            + "</svg>";

    private EditorActivity editor;
    private String docId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        android.content.Context c = getInstrumentation().getTargetContext();
        docId = "searchfocus-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(docId, "focus.svg", SVG, System.currentTimeMillis());
        doc.setKindName("SVG");
        new DocumentRepository(c).saveDocument(doc);
        Intent i = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, docId)
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, false);
        editor = (EditorActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        new DocumentRepository(getInstrumentation().getTargetContext()).deleteDocument(docId);
        getInstrumentation().runOnMainSync(() -> {
            if (!editor.isFinishing()) editor.finish();
        });
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    /**
     * The whole reported failure in one test: open search, type a multi-character query, and check
     * that (a) focus stayed in the query box and (b) the document is byte-identical.
     */
    public void testTypingAQueryKeepsFocusAndNeverEditsTheDocument() throws Throwable {
        runTestOnUiThread(() -> editor.findViewById(R.id.btn_search).performClick());
        getInstrumentation().waitForIdleSync();

        final EditText query = editor.findViewById(R.id.edit_search_query);
        final EditText code = editor.findViewById(R.id.edit_code);
        assertNotNull(query);
        assertNotNull(code);

        // Type one character at a time, exactly as the IME does, and check focus after each.
        for (char ch : "line_energy".toCharArray()) {
            final char toType = ch;
            runTestOnUiThread(() -> {
                query.getText().append(toType);
                query.requestFocus();
            });
            getInstrumentation().waitForIdleSync();
            Thread.sleep(120);

            final boolean queryFocused = query.hasFocus();
            final String codeNow = code.getText().toString();
            runTestOnUiThread(() -> {
                assertTrue("输入「" + toType + "」后焦点必须仍在搜索框，实际焦点在 "
                                + (query.hasFocus() ? "搜索框" : "别处"),
                        query.hasFocus());
                assertEquals("搜索过程中不得改动文档内容", SVG, code.getText().toString());
            });
            assertTrue("query focus must survive typing", queryFocused);
            assertEquals("document must be untouched while searching", SVG, codeNow);
        }

        runTestOnUiThread(() -> {
            assertEquals("查询内容必须完整保留", "line_energy", query.getText().toString());
            assertEquals("必须统计到两处匹配", 2, editor.getSearchResultForTest().count());
        });
    }

    /** Moving between matches must not steal focus either. */
    public void testSteppingBetweenMatchesKeepsFocusInTheSearchBox() throws Throwable {
        runTestOnUiThread(() -> {
            editor.findViewById(R.id.btn_search).performClick();
            ((EditText) editor.findViewById(R.id.edit_search_query)).setText("line_energy");
        });
        getInstrumentation().waitForIdleSync();

        final EditText query = editor.findViewById(R.id.edit_search_query);
        for (int i = 0; i < 3; i++) {
            runTestOnUiThread(() -> editor.findViewById(R.id.btn_search_next).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> {
                assertTrue("切换匹配时焦点必须留在搜索框", query.hasFocus());
                assertEquals("切换匹配不得改动文档", SVG,
                        ((EditText) editor.findViewById(R.id.edit_code)).getText().toString());
            });
        }
        for (int i = 0; i < 3; i++) {
            runTestOnUiThread(() -> editor.findViewById(R.id.btn_search_prev).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> assertTrue("上一处同样不得抢焦点", query.hasFocus()));
        }
    }

    /** The match must still be scrolled into view, even though focus stays in the query box. */
    public void testMatchIsStillRevealedWithoutStealingFocus() throws Throwable {
        // A document long enough that the last match is off-screen.
        final StringBuilder big = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\">\n");
        for (int i = 0; i < 200; i++) big.append("  <rect x=\"").append(i).append("\" y=\"1\" width=\"2\" height=\"2\"/>\n");
        big.append("  <text>NEEDLE_HERE</text>\n</svg>");
        final String body = big.toString();

        runTestOnUiThread(() -> {
            ((EditText) editor.findViewById(R.id.edit_code)).setText(body);
            editor.findViewById(R.id.btn_search).performClick();
            ((EditText) editor.findViewById(R.id.edit_search_query)).setText("NEEDLE_HERE");
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(600);

        runTestOnUiThread(() -> {
            assertTrue("搜索框必须保持焦点", editor.findViewById(R.id.edit_search_query).hasFocus());
            EditText code = editor.findViewById(R.id.edit_code);
            int start = code.getSelectionStart();
            assertTrue("必须把光标/选区定位到匹配处，实际 " + start,
                    start > 0 && code.getText().toString().startsWith("NEEDLE_HERE", start));
            assertEquals("文档不得被改动", body, code.getText().toString());
        });
    }
}
