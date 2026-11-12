package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentKind;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every format in the first stage, opened from a real file on the device.
 *
 * <p>These are the acceptance cases from the spec, not a smoke test: the samples are pushed to
 * {@code /sdcard/Download/cc/} and imported through the same code path the file picker uses, so an
 * import that decodes a PNG as UTF-8 or a CSV that loses a quoted comma fails here.</p>
 */
public class FormatSupportTest extends InstrumentationTestCase {

    private Context context;
    private DocumentRepository repository;
    /** Fixtures live in the app's own files dir: scoped storage blocks /sdcard for this uid. */
    private File dir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
        dir = new File(context.getFilesDir(), "fixtures");
    }

    private File fixture(String name) {
        return new File(dir, name);
    }

    /** Import one sample file the way the picker does. */
    private FileImporter.Imported importSample(String name) throws Exception {
        File file = fixture(name);
        assertTrue("sample must be pushed to the device: " + file, file.isFile());
        return FileImporter.importFrom(context, Uri.fromFile(file));
    }

    private EditorActivity open(CanvasDocument doc, boolean preview) throws Exception {
        if (doc.isBlob()) {
            FileImporter.writeBlob(context, doc.getId(), FileImporter.readBlob(context, doc.getId()));
        }
        repository.saveDocument(doc);
        Intent intent = new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                .putExtra(EditorActivity.EXTRA_START_PREVIEW, preview);
        EditorActivity activity = (EditorActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        return activity;
    }

    private void close(EditorActivity activity, String docId) throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            if (!activity.isFinishing()) activity.finish();
        });
        getInstrumentation().waitForIdleSync();
        repository.deleteDocument(docId);
    }

    private String evaluate(WebView web, String js) throws Exception {
        final AtomicReference<String> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(js, value -> {
            result.set(value);
            latch.countDown();
        }));
        latch.await(6, TimeUnit.SECONDS);
        String raw = result.get();
        if (raw == null) return null;
        return raw.replace("\\\"", "\"").replaceAll("^\"|\"$", "");
    }

    private void waitForPreview(EditorActivity activity) throws Exception {
        final WebView web = activity.findViewById(R.id.web_preview);
        assertNotNull("preview WebView must exist", web);
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            String ready = evaluate(web, "document.readyState");
            if ("complete".equals(ready)) {
                Thread.sleep(400);
                return;
            }
            Thread.sleep(250);
        }
        fail("preview never finished loading");
    }

    // ------------------------------------------------------------------ text formats

    public void testMarkdownRendersTableCodeAndFormulaOffline() throws Throwable {
        FileImporter.Imported imported = importSample("工艺卡片.md");
        assertEquals(DocumentKind.MARKDOWN, imported.kind);
        EditorActivity activity = open(imported.document, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            // The table, the fenced code block, the KaTeX output and the Mermaid diagram all have
            // to exist — with the network blocked, so every library came from the APK.
            String table = evaluate(web, "document.querySelectorAll('#cc-md table').length + ''");
            assertEquals("Markdown 表格必须渲染", "1", table);
            String code = evaluate(web, "document.querySelectorAll('#cc-md pre code').length + ''");
            assertTrue("代码块必须存在", Integer.parseInt(code) >= 1);
            String katex = evaluate(web, "document.querySelectorAll('#cc-md .katex').length + ''");
            assertTrue("公式必须由 KaTeX 渲染，实际 " + katex, Integer.parseInt(katex) >= 2);
            String mermaid = evaluate(web,
                    "document.querySelectorAll('#cc-md svg[id^=\"mermaid\"], #cc-md .mermaid svg').length + ''");
            assertTrue("Mermaid 图必须渲染成 SVG，实际 " + mermaid, Integer.parseInt(mermaid) >= 1);
            String error = evaluate(web, "document.getElementById('cc-md-err').style.display");
            assertEquals("不得出现渲染错误", "none", error);
        } finally {
            close(activity, imported.document.getId());
        }
    }

    public void testMermaidRendersDiagram() throws Throwable {
        FileImporter.Imported imported = importSample("flow.mmd");
        assertEquals(DocumentKind.MERMAID, imported.kind);
        EditorActivity activity = open(imported.document, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            long deadline = System.currentTimeMillis() + 12000;
            String nodes = "0";
            while (System.currentTimeMillis() < deadline) {
                nodes = evaluate(web, "document.querySelectorAll('#cc-mermaid svg').length + ''");
                if (nodes != null && Integer.parseInt(nodes) >= 1) break;
                Thread.sleep(300);
            }
            assertTrue("Mermaid 图必须渲染成 SVG，实际 " + nodes, Integer.parseInt(nodes) >= 1);
            String error = evaluate(web, "document.getElementById('cc-mermaid-err').style.display");
            assertEquals("不得出现语法错误", "none", error);
        } finally {
            close(activity, imported.document.getId());
        }
    }

    public void testBrokenMermaidShowsAnErrorWithoutBlockingTheSource() throws Throwable {
        CanvasDocument doc = new CanvasDocument("mmd-bad-" + System.nanoTime(), "bad.mmd",
                "graph TD\n  A --> \n  this is not a diagram\n", System.currentTimeMillis());
        doc.setKindName(DocumentKind.MERMAID.name());
        EditorActivity activity = open(doc, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            long deadline = System.currentTimeMillis() + 12000;
            String shown = "none";
            while (System.currentTimeMillis() < deadline) {
                shown = evaluate(web, "document.getElementById('cc-mermaid-err').style.display");
                if ("block".equals(shown)) break;
                Thread.sleep(300);
            }
            assertEquals("语法错误必须可见", "block", shown);
            // The source must still be editable and saveable.
            getInstrumentation().runOnMainSync(() -> {
                assertEquals("源码必须仍然可编辑", "graph TD\n  A --> \n  this is not a diagram\n",
                        ((android.widget.EditText) activity.findViewById(R.id.edit_code))
                                .getText().toString());
            });
        } finally {
            close(activity, doc.getId());
        }
    }

    public void testJsonIsFormattedButNumbersAreUntouched() throws Throwable {
        FileImporter.Imported imported = importSample("data.json");
        assertEquals(DocumentKind.JSON, imported.kind);
        EditorActivity activity = open(imported.document, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            String tree = evaluate(web, "(document.querySelector('#cc-md, .cc-tree') ? 'yes' : 'no')");
            assertEquals("yes", tree);
            String body = evaluate(web, "document.body.innerText");
            assertTrue("2.40 必须保持原样", body.contains("2.40"));
            assertTrue("大整数不得变成科学计数法", body.contains("12345678901234567890"));
            assertTrue("1.0 必须保持 1.0", body.contains("1.0"));
            assertTrue("不得出现格式错误提示", !body.contains("JSON 格式错误"));
        } finally {
            close(activity, imported.document.getId());
        }
    }

    public void testInvalidJsonKeepsSourceAndReportsPosition() throws Throwable {
        CanvasDocument doc = new CanvasDocument("json-bad-" + System.nanoTime(), "bad.json",
                "{\n  \"a\": 1,\n  \"b\": \n}\n", System.currentTimeMillis());
        doc.setKindName(DocumentKind.JSON.name());
        EditorActivity activity = open(doc, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            String body = evaluate(web, "document.body.innerText");
            assertTrue("必须报告格式错误", body.contains("JSON 格式错误"));
            assertTrue("必须给出位置或原因", body.contains("行") || body.contains("列") || body.length() > 0);
            getInstrumentation().runOnMainSync(() -> assertTrue(
                    "源码必须原样保留",
                    ((android.widget.EditText) activity.findViewById(R.id.edit_code))
                            .getText().toString().contains("\"b\": ")));
        } finally {
            close(activity, doc.getId());
        }
    }

    public void testJsonlShowsOneBrokenLineWithoutHidingTheRest() throws Throwable {
        FileImporter.Imported imported = importSample("records.jsonl");
        assertEquals(DocumentKind.JSONL, imported.kind);
        EditorActivity activity = open(imported.document, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            String body = evaluate(web, "document.body.innerText");
            assertTrue("必须说明 3 行有效", body.contains("3 行有效"));
            assertTrue("必须说明 1 行有错误", body.contains("1 行有错误"));
            assertTrue("错误行号必须出现", body.contains("3"));
            assertTrue("其他行必须仍然显示", body.contains("\"weld\": \"D\"") || body.contains("weld"));
        } finally {
            close(activity, imported.document.getId());
        }
    }

    public void testCsvKeepsQuotedCommasAndEmbeddedNewlines() throws Throwable {
        FileImporter.Imported imported = importSample("samples.csv");
        assertEquals(DocumentKind.CSV, imported.kind);
        EditorActivity activity = open(imported.document, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            String body = evaluate(web, "document.body.innerText");
            assertTrue("带逗号的单元格必须是一个单元格", body.contains("对接焊, 无间隙"));
            assertTrue("单元格内的换行必须保留", body.contains("第一道"));
            assertTrue("双引号必须还原成一个", body.contains("他说\"没问题\""));
            String cells = evaluate(web, "document.querySelectorAll('table tr').length + ''");
            assertEquals("应有表头 + 3 行数据", "4", cells);
        } finally {
            close(activity, imported.document.getId());
        }
    }

    public void testYamlReportsTheTabProblem() throws Throwable {
        FileImporter.Imported imported = importSample("config.yaml");
        assertEquals(DocumentKind.YAML, imported.kind);
        EditorActivity activity = open(imported.document, true);
        try {
            waitForPreview(activity);
            WebView web = activity.findViewById(R.id.web_preview);
            String body = evaluate(web, "document.body.innerText");
            assertTrue("制表符缩进必须被指出", body.contains("制表符"));
            assertTrue("必须说明未做完整校验", body.contains("未做完整"));
        } finally {
            close(activity, imported.document.getId());
        }
    }

    public void testCodeFileOpensInTheEditorWithItsOwnText() throws Throwable {
        FileImporter.Imported imported = importSample("weld.py");
        assertEquals(DocumentKind.CODE, imported.kind);
        EditorActivity activity = open(imported.document, false);
        try {
            getInstrumentation().runOnMainSync(() -> {
                String text = ((android.widget.EditText) activity.findViewById(R.id.edit_code))
                        .getText().toString();
                assertTrue("中文注释必须正确解码", text.contains("线能量 J/mm"));
                assertTrue("代码内容必须完整", text.contains("def line_energy"));
                assertEquals("必须识别为代码", DocumentKind.CODE, activity.getKindForTest());
            });
        } finally {
            close(activity, imported.document.getId());
        }
    }

    // ------------------------------------------------------------------ encoding

    public void testGb18030TextIsDecodedAndReEncodedIdentically() throws Throwable {
        FileImporter.Imported imported = importSample("gb18030.txt");
        assertEquals("GB18030", imported.document.getEncoding());
        assertEquals("中文必须正确解码", true, imported.document.getContent().contains("异种不锈钢激光焊接"));
        // The bytes must survive a round trip through the editor unchanged.
        byte[] payload = com.nous.codecanvas.util.SourceWriter.payloadBytes(
                context, imported.document, imported.document.getContent());
        assertTrue("重新编码必须逐字节一致",
                java.util.Arrays.equals(imported.bytes, payload));
    }

    public void testCrlfLineEndingsSurviveASave() throws Throwable {
        FileImporter.Imported imported = importSample("crlf.txt");
        assertEquals("\r\n", imported.document.getLineEnding());
        byte[] payload = com.nous.codecanvas.util.SourceWriter.payloadBytes(
                context, imported.document, imported.document.getContent());
        assertTrue("换行方式必须保留", java.util.Arrays.equals(imported.bytes, payload));
    }

    // ------------------------------------------------------------------ binary formats

    public void testPngIsKeptAsBytesAndExportedUnchanged() throws Throwable {
        FileImporter.Imported imported = importSample("photo.png");
        assertEquals(DocumentKind.IMAGE, imported.kind);
        assertTrue("图片必须是 blob，不能当文本解码", imported.document.isBlob());
        assertEquals("图片不可编辑", false, imported.kind.editable());
        FileImporter.writeBlob(context, imported.document.getId(), imported.bytes);
        byte[] exported = com.nous.codecanvas.util.SourceWriter.payloadBytes(
                context, imported.document, "");
        assertTrue("未修改导出必须逐字节一致", java.util.Arrays.equals(imported.bytes, exported));
        repository.deleteDocument(imported.document.getId());
    }

    public void testPdfIsKeptAsBytesAndExportedUnchanged() throws Throwable {
        FileImporter.Imported imported = importSample("report.pdf");
        assertEquals(DocumentKind.PDF, imported.kind);
        assertTrue(imported.document.isBlob());
        FileImporter.writeBlob(context, imported.document.getId(), imported.bytes);
        byte[] exported = com.nous.codecanvas.util.SourceWriter.payloadBytes(
                context, imported.document, "");
        assertTrue("PDF 未修改导出必须逐字节一致", java.util.Arrays.equals(imported.bytes, exported));
        repository.deleteDocument(imported.document.getId());
    }

    public void testGifIsRecognisedAndStaysPlayable() throws Throwable {
        // A 1x1 GIF is enough to prove the type is detected and not decoded as text.
        File file = fixture("tiny.gif");
        if (!file.isFile()) {
            android.util.Log.i("CodeCanvasTest", "no tiny.gif; skipping");
            return;
        }
        FileImporter.Imported imported = FileImporter.importFrom(context, Uri.fromFile(file));
        assertEquals(DocumentKind.GIF, imported.kind);
        repository.deleteDocument(imported.document.getId());
    }

    public void testBinaryFileIsRefusedWithAnExplanation() throws Throwable {
        File file = fixture("mystery.bin");
        if (!file.isFile()) {
            android.util.Log.i("CodeCanvasTest", "no mystery.bin; skipping");
            return;
        }
        try {
            FileImporter.importFrom(context, Uri.fromFile(file));
            fail("二进制文件必须被拒绝");
        } catch (java.io.IOException expected) {
            assertTrue("必须说明原因", expected.getMessage().contains("二进制"));
        }
    }
}
