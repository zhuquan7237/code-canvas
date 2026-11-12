package com.nous.codecanvas;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.widget.ListView;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * A document's type must survive a process restart.
 *
 * <p>Reported as "所有格式都打不开了" against a release build. The files opened, but the home list
 * tagged every one of them "RAW": the stored {@code kind} string could not be resolved back to an
 * enum constant, so each document fell back to the unknown kind. Two separate causes, both fixed
 * here:</p>
 * <ol>
 *   <li>R8 renamed the enum constants, so {@code DocumentKind.fromName("MARKDOWN")} returned null
 *       for data written by an earlier build.</li>
 *   <li>The list still displayed the old four-value {@code RenderKind}, which has no idea what a
 *       Markdown or a CSV is.</li>
 * </ol>
 */
public class TypePersistsAcrossRestartTest extends InstrumentationTestCase {

    private static final String[][] CASES = {
            {"p.md", "# 标题\n\n正文\n", "Markdown"},
            {"p.mmd", "graph TD\nA-->B\n", "Mermaid"},
            {"p.json", "{\"a\":1}\n", "JSON"},
            {"p.jsonl", "{\"a\":1}\n", "JSONL"},
            {"p.xml", "<?xml version=\"1.0\"?><r><i>1</i></r>\n", "XML"},
            {"p.yaml", "a: 1\n", "YAML"},
            {"p.csv", "a,b\n1,2\n", "CSV"},
            {"p.txt", "纯文本\n", "文本"},
            {"p.py", "print(1)\n", "代码"},
            {"p.html", "<!DOCTYPE html><html><body><p>x</p></body></html>\n", "HTML"},
            {"p.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>", "SVG"},
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
        created.clear();
        super.tearDown();
    }

    public void testTypeSurvivesReload() throws Throwable {
        Context c = getInstrumentation().getTargetContext();

        // Write through one repository instance...
        DocumentRepository writer = new DocumentRepository(c);
        for (String[] testCase : CASES) {
            String id = "persist-" + System.nanoTime();
            CanvasDocument doc = new CanvasDocument(id, testCase[0], testCase[1], System.currentTimeMillis());
            doc.setKindName(doc.kind().name());
            writer.saveDocument(doc);
            created.add(id);
        }

        // ...and read back through a fresh one, which is what a process restart looks like.
        DocumentRepository reader = new DocumentRepository(c);
        for (String[] testCase : CASES) {
            CanvasDocument found = null;
            for (CanvasDocument doc : reader.getAllDocuments()) {
                if (testCase[0].equals(doc.getTitle())) {
                    found = doc;
                    break;
                }
            }
            assertNotNull(testCase[0] + " 必须能读回来", found);
            assertEquals(testCase[0] + " 的类型在重新读取后必须还是 " + testCase[2]
                            + "，实际 " + found.kind().label(),
                    testCase[2], found.kind().label());
        }
    }

    /** And the home list must render that type, not the old four-value tag. */
    public void testHomeListShowsThePersistedType() throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        DocumentRepository repository = new DocumentRepository(c);
        String id = "persist-label-" + System.nanoTime();
        CanvasDocument doc = new CanvasDocument(id, "label-check.md", "# 标题\n", System.currentTimeMillis());
        doc.setKindName(doc.kind().name());
        repository.saveDocument(doc);
        created.add(id);

        android.content.Intent i = new android.content.Intent(c, MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        final MainActivity activity = (MainActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(1000);
        getInstrumentation().waitForIdleSync();

        final ListView list = activity.findViewById(R.id.list_documents);
        assertNotNull(list);
        String found = null;
        for (int row = 0; row < list.getChildCount(); row++) {
            android.view.View child = list.getChildAt(row);
            if (child == null) continue;
            TextView title = child.findViewById(R.id.txt_doc_title);
            TextView tag = child.findViewById(R.id.txt_doc_tag);
            if (title == null || tag == null) continue;
            if ("label-check.md".equals(title.getText().toString())) {
                found = tag.getText().toString();
                break;
            }
        }
        android.util.Log.i("CCPERSIST", "label-check.md tag=" + found);
        assertEquals("主页必须显示 Markdown，不能是 RAW", "Markdown", found);

        getInstrumentation().runOnMainSync(() -> {
            if (!activity.isFinishing()) activity.finish();
        });
        getInstrumentation().waitForIdleSync();
    }
}
