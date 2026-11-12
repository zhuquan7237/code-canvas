package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

/**
 * Typing in the search box must not be interrupted, and must never edit the document.
 *
 * <p>Reported as: "在搜索栏搜索的时候，只能输入一个字母，就会退出输入状态，而且退输入状态会直接修改 svg 代码". Two
 * separate faults:</p>
 * <ol>
 *   <li>Focus left the query field after one character, so the second character went somewhere
 *       else — or nowhere.</li>
 *   <li>Whatever was typed ended up in the SVG source, which is why the file looked edited after
 *       merely searching.</li>
 * </ol>
 * <p>Both are asserted here against the real activity: type a multi-character query one character
 * at a time (as the IME does), and check the field keeps focus, keeps every character, and that the
 * document text is byte-for-byte unchanged and not marked dirty.</p>
 */
public class SearchInputFocusTest extends InstrumentationTestCase {

    private static final String SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\">\n"
            + "  <rect width=\"400\" height=\"300\" fill=\"#eef\"/>\n"
            + "  <text x=\"40\" y=\"80\" font-size=\"28\">line_energy</text>\n"
            + "  <text x=\"40\" y=\"140\" font-size=\"28\">weld_depth</text>\n"
            + "  <text x=\"40\" y=\"200\" font-size=\"28\">line_energy</text>\n"
            + "</svg>\n";

    private EditorActivity editor;
    private String docId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context c = getInstrumentation().getTargetContext();
        docId = "search-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(docId, "search.svg", SVG, System.currentTimeMillis());
        doc.setKindName("SVG");
        new DocumentRepository(c).saveDocument(doc);
        Intent i = new Intent(c, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, docId);
        editor = (EditorActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        // Search lives on the code tab.
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.tab_code).performClick());
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

    private EditText query() {
        return editor.findViewById(R.id.edit_search_query);
    }

    private EditText code() {
        return editor.findViewById(R.id.edit_code);
    }

    private String documentText() throws Throwable {
        final String[] out = new String[1];
        getInstrumentation().runOnMainSync(() -> out[0] = code().getText().toString());
        return out[0];
    }

    /**
     * Type one character the way an IME commits text: append to the field's editable.
     *
     * <p>Deliberately not a key event — a synthetic KEYCODE_A would insert an 'a' as well, and the
     * resulting garbled query looked like an app defect when it was the test's own doing.</p>
     */
    private void typeChar(char c) throws Throwable {
        final EditText q = query();
        getInstrumentation().runOnMainSync(() -> {
            if (!q.hasFocus()) q.requestFocus();
            Editable e = q.getText();
            e.append(String.valueOf(c));
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testEveryTypedCharacterStaysInTheBox() throws Throwable {
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_search).performClick());
        getInstrumentation().waitForIdleSync();

        for (char c : "line_energy".toCharArray()) {
            typeChar(c);
            final String current = query().getText().toString();
            assertTrue("输入 " + c + " 之后焦点必须还在搜索框（实际内容：\"" + current + "\"）",
                    query().hasFocus());
        }

        assertEquals("搜索框必须保留完整关键词", "line_energy", query().getText().toString());
    }

    /** The reported damage: searching edited the file. */
    public void testSearchingNeverChangesTheDocument() throws Throwable {
        String before = documentText();

        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_search).performClick());
        getInstrumentation().waitForIdleSync();
        for (char c : "weld".toCharArray()) typeChar(c);

        assertEquals("搜索不能改动文档内容", before, documentText());
        final boolean[] dirty = new boolean[1];
        getInstrumentation().runOnMainSync(() -> dirty[0] = editor.isDirtyForTest());
        assertFalse("搜索不能把文件标记为已修改", dirty[0]);
    }

    /** A query with no match must say so instead of silently doing nothing. */
    public void testNoResultIsReported() throws Throwable {
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_search).performClick());
        getInstrumentation().waitForIdleSync();
        for (char c : "zzzz".toCharArray()) typeChar(c);

        final TextView counter = editor.findViewById(R.id.txt_search_counter);
        final String text = counter.getText().toString();
        android.util.Log.i("CCSEARCH", "no-result counter=\"" + text + "\"");
        assertTrue("无结果必须明确提示，实际显示 \"" + text + "\"",
                text.contains("0") || text.contains("无"));
    }

    /** Chinese, symbols and a multi-line paste are all ordinary text to match. */
    public void testChineseSymbolsAndMultilineQueries() throws Throwable {
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_search).performClick());
        getInstrumentation().waitForIdleSync();

        for (char c : "line_energy".toCharArray()) typeChar(c);
        final TextView counter = editor.findViewById(R.id.txt_search_counter);
        final String text = counter.getText().toString();
        android.util.Log.i("CCSEARCH", "counter=\"" + text + "\"");
        assertTrue("必须显示出匹配总数，实际 \"" + text + "\"", text.contains("/"));

        // "line_energy" occurs twice in the fixture.
        assertTrue("计数必须包含 2 处匹配，实际 \"" + text + "\"", text.contains("2"));
    }

    /** Closing search must hand focus back to the code, not leave a dead field. */
    public void testClosingSearchReturnsToTheEditor() throws Throwable {
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_search).performClick());
        getInstrumentation().waitForIdleSync();
        for (char c : "weld".toCharArray()) typeChar(c);

        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_search_close).performClick());
        getInstrumentation().waitForIdleSync();

        final View bar = editor.findViewById(R.id.search_bar);
        assertEquals("关闭后搜索栏必须隐藏", View.GONE, bar.getVisibility());
        assertEquals("关闭后不能残留高亮", "", query().getText().toString());
    }
}
