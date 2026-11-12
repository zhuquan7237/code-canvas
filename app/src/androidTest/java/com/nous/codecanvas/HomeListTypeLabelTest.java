package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.widget.ListView;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The home list must show each document's real type.
 *
 * <p>Reported as "一个都打不开" after a release build. The files did open — but the home list tagged
 * every one of them "RAW", because the list was still reading the old four-value {@code RenderKind}
 * (HTML/SVG/XML/RAW) instead of the sixteen-value {@code DocumentKind}. A Markdown file, a CSV, a
 * PNG all showed "RAW", so the app looked like it had lost its format support entirely.</p>
 */
public class HomeListTypeLabelTest extends InstrumentationTestCase {

    private static final String[][] CASES = {
            {"l.md", "# 标题\n\n正文\n", "Markdown"},
            {"l.mmd", "graph TD\nA-->B\n", "Mermaid"},
            {"l.json", "{\"a\":1}\n", "JSON"},
            {"l.jsonl", "{\"a\":1}\n", "JSONL"},
            {"l.xml", "<?xml version=\"1.0\"?><r><i>1</i></r>\n", "XML"},
            {"l.yaml", "a: 1\n", "YAML"},
            {"l.csv", "a,b\n1,2\n", "CSV"},
            {"l.txt", "纯文本\n", "文本"},
            {"l.py", "print(1)\n", "代码"},
            {"l.html", "<!DOCTYPE html><html><body><p>x</p></body></html>\n", "HTML"},
            {"l.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>", "SVG"},
    };

    private final List<String> created = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        Context c = getInstrumentation().getTargetContext();
        DocumentRepository repository = new DocumentRepository(c);
        for (String id : created) {
            try {
                repository.deleteDocument(id);
            } catch (Exception ignored) {
            }
        }
        super.tearDown();
    }

    public void testEachDocumentShowsItsRealType() throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        DocumentRepository repository = new DocumentRepository(c);

        for (String[] testCase : CASES) {
            String id = "label-" + System.nanoTime();
            CanvasDocument doc = new CanvasDocument(id, testCase[0], testCase[1], System.currentTimeMillis());
            repository.saveDocument(doc);
            created.add(id);
        }

        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final MainActivity activity = (MainActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(1200);
        getInstrumentation().waitForIdleSync();

        final ListView list = activity.findViewById(R.id.list_documents);
        assertNotNull(list);

        // Read the tag cell of every visible row and match it against the file it belongs to.
        StringBuilder seen = new StringBuilder();
        final int count = list.getChildCount();
        for (int row = 0; row < count; row++) {
            android.view.View child = list.getChildAt(row);
            if (child == null) continue;
            TextView title = child.findViewById(R.id.txt_doc_title);
            TextView tag = child.findViewById(R.id.txt_doc_tag);
            if (title == null || tag == null) continue;
            String name = title.getText().toString();
            String label = tag.getText().toString();
            seen.append(name).append('=').append(label).append("; ");
            android.util.Log.i("CCLABEL", name + " -> " + label);

            for (String[] testCase : CASES) {
                if (testCase[0].equals(name)) {
                    assertEquals(name + " 的类型标签必须是 " + testCase[2] + "，实际 " + label,
                            testCase[2], label);
                }
            }
        }
        android.util.Log.i("CCLABEL", "rows=" + count + " [" + seen + "]");
        assertTrue("列表里必须能看到刚建的文件", count > 0);

        getInstrumentation().runOnMainSync(() -> {
            if (!activity.isFinishing()) activity.finish();
        });
        getInstrumentation().waitForIdleSync();
    }
}
