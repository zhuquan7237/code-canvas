package com.nous.codecanvas;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.ui.MainActivity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CodeCanvasActivityE2ETest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MainActivity mainActivity;
    private Instrumentation instrumentation;

    public CodeCanvasActivityE2ETest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        instrumentation = getInstrumentation();
        mainActivity = getActivity();
    }

    public void testE2ECreateWithArbitrarySuffixEditUndoRedoSaveReopen() throws Throwable {
        final DocumentRepository repo = new DocumentRepository(mainActivity);
        final String arbitraryTitle = "custom_test_" + System.currentTimeMillis() + ".shader.glsl.custom";
        final String initialCode = "// Vertex Shader initial code\nvoid main() {\n  gl_Position = vec4(0.0);\n}";
        final String editedCode = "// Vertex Shader edited code\nvoid main() {\n  gl_Position = vec4(1.0);\n}";

        // Step 1: Open creation dialog or programmatically create document with arbitrary suffix
        CanvasDocument doc = new CanvasDocument(
                "e2e-suffix-" + System.currentTimeMillis(),
                arbitraryTitle,
                initialCode,
                System.currentTimeMillis()
        );
        repo.saveDocument(doc);

        // Step 2: Start EditorActivity targeting this document
        Intent intent = new Intent(mainActivity, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(EditorActivity.class.getName(), null, false);
        mainActivity.startActivity(intent);
        final EditorActivity editorActivity = (EditorActivity) monitor.waitForActivityWithTimeout(5000);
        assertNotNull("EditorActivity should launch", editorActivity);

        // Verify title displayed
        final TextView txtTitle = editorActivity.findViewById(R.id.txt_editor_title);
        final EditText editCode = editorActivity.findViewById(R.id.edit_code);
        final ImageButton btnUndo = editorActivity.findViewById(R.id.btn_undo);
        final ImageButton btnRedo = editorActivity.findViewById(R.id.btn_redo);
        final Button btnPaste = editorActivity.findViewById(R.id.btn_paste);

        instrumentation.waitForIdleSync();
        assertEquals(arbitraryTitle, txtTitle.getText().toString());
        assertEquals(initialCode, editCode.getText().toString());

        // Step 3: Test Clipboard Paste button
        runTestOnUiThread(() -> {
            ClipboardManager cm = (ClipboardManager) editorActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("test_clip", "\n// Pasted by Test");
            cm.setPrimaryClip(clip);

            editCode.setSelection(editCode.getText().length());
            btnPaste.performClick();
        });
        instrumentation.waitForIdleSync();
        assertTrue("Editor must contain pasted content", editCode.getText().toString().contains("// Pasted by Test"));

        // Step 4: Test Edit, Undo, Redo
        runTestOnUiThread(() -> {
            editCode.setText(editedCode);
        });
        instrumentation.waitForIdleSync();
        assertEquals(editedCode, editCode.getText().toString());

        // Test Undo
        runTestOnUiThread(() -> {
            assertTrue("Undo should be enabled", btnUndo.isEnabled());
            btnUndo.performClick();
        });
        instrumentation.waitForIdleSync();

        // Test Redo
        runTestOnUiThread(() -> {
            assertTrue("Redo should be enabled", btnRedo.isEnabled());
            btnRedo.performClick();
        });
        instrumentation.waitForIdleSync();
        assertEquals(editedCode, editCode.getText().toString());

        // Step 5: Save, Back, and Reopen to verify persistence
        // Trigger save and wait
        runTestOnUiThread(() -> {
            editorActivity.finish();
        });
        instrumentation.waitForIdleSync();
        Thread.sleep(1200); // Wait for background executor

        CanvasDocument persisted = repo.findById(doc.getId());
        assertNotNull("Document must be persisted in repository", persisted);
        assertEquals(editedCode, persisted.getContent());

        // Clean up
        repo.deleteDocument(doc.getId());
        instrumentation.removeMonitor(monitor);
    }

    public void testWebViewHtmlSvgEvaluationAndJsGating() throws Throwable {
        final DocumentRepository repo = new DocumentRepository(mainActivity);
        final String htmlDocId = "e2e-html-" + System.currentTimeMillis();
        final String htmlContent = "<!DOCTYPE html><html><body><h1 id=\"headline\">CodeCanvas E2E Ready</h1><script>document.getElementById('headline').innerText='JS Executed';</script></body></html>";

        CanvasDocument doc = new CanvasDocument(htmlDocId, "test.html", htmlContent, System.currentTimeMillis());
        repo.saveDocument(doc);

        Intent intent = new Intent(mainActivity, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(EditorActivity.class.getName(), null, false);
        mainActivity.startActivity(intent);
        final EditorActivity editorActivity = (EditorActivity) monitor.waitForActivityWithTimeout(5000);
        assertNotNull("EditorActivity should launch", editorActivity);
        instrumentation.waitForIdleSync();

        final TextView tabPreview = editorActivity.findViewById(R.id.tab_preview);
        final TextView tabCode = editorActivity.findViewById(R.id.tab_code);
        final WebView webView = editorActivity.findViewById(R.id.web_preview);
        final Switch switchJs = editorActivity.findViewById(R.id.switch_allow_js);

        // Switch to preview tab with JS disabled (default)
        runTestOnUiThread(() -> {
            tabPreview.performClick();
        });
        instrumentation.waitForIdleSync();
        Thread.sleep(1500); // Allow webview to render

        // Check DOM: with JS disabled, evaluateJavascript cannot run if JavaScript is fully disabled on WebView!
        // In Android WebView, evaluateJavascript requires javascript enabled, OR when JS is disabled it returns null.
        // Let's verify that when JS is disabled, evaluateJavascript returns null (or script didn't execute).
        final AtomicReference<String> resultJsDisabled = new AtomicReference<>();
        final CountDownLatch latch1 = new CountDownLatch(1);
        runTestOnUiThread(() -> {
            webView.evaluateJavascript("document.getElementById('headline') ? document.getElementById('headline').innerText : 'NO_ELEMENT'", value -> {
                resultJsDisabled.set(value);
                latch1.countDown();
            });
        });
        assertTrue(latch1.await(3, TimeUnit.SECONDS));
        String jsVal = resultJsDisabled.get();
        android.util.Log.i("CodeCanvasTest", "jsVal is " + (jsVal == null ? "NULL_OBJ" : ("LEN_" + jsVal.length() + "_VAL_" + jsVal)));
        assertEquals("null", jsVal);

        // Enable JS switch and re-render
        runTestOnUiThread(() -> {
            switchJs.setChecked(true);
        });
        instrumentation.waitForIdleSync();
        Thread.sleep(1500); // Allow re-render with JS

        final AtomicReference<String> resultJsEnabled = new AtomicReference<>();
        final CountDownLatch latch2 = new CountDownLatch(1);
        runTestOnUiThread(() -> {
            webView.evaluateJavascript("document.getElementById('headline').innerText", value -> {
                resultJsEnabled.set(value);
                latch2.countDown();
            });
        });
        assertTrue(latch2.await(3, TimeUnit.SECONDS));
        assertEquals("\"JS Executed\"", resultJsEnabled.get());

        // Now test SVG DOM
        final String svgDocId = "e2e-svg-" + System.currentTimeMillis();
        final String svgContent = "<svg id=\"my-svg\" width=\"200\" height=\"200\"><circle id=\"my-circle\" cx=\"100\" cy=\"100\" r=\"50\" fill=\"red\"/></svg>";
        runTestOnUiThread(() -> {
            tabCode.performClick();
            EditText editCode = editorActivity.findViewById(R.id.edit_code);
            TextView titleView = editorActivity.findViewById(R.id.txt_editor_title);
            titleView.setText("test.svg");
            editorActivity.getCurrentDocumentForTest().setTitle("test.svg");
            editCode.setText(svgContent);
            tabPreview.performClick();
        });
        instrumentation.waitForIdleSync();
        Thread.sleep(1500);

        final AtomicReference<String> svgDomResult = new AtomicReference<>();
        final CountDownLatch latch3 = new CountDownLatch(1);
        runTestOnUiThread(() -> {
            webView.evaluateJavascript("document.getElementById('my-circle') ? 'FOUND' : 'NOT_FOUND'", value -> {
                svgDomResult.set(value);
                latch3.countDown();
            });
        });
        assertTrue(latch3.await(3, TimeUnit.SECONDS));
        assertEquals("\"FOUND\"", svgDomResult.get());

        // Step 6: Test XML error view
        final String malformedXml = "<root><unclosedTag>test</root>";
        final View xmlLayout = editorActivity.findViewById(R.id.layout_xml_view);
        final TextView txtXmlStatus = editorActivity.findViewById(R.id.txt_xml_status);

        runTestOnUiThread(() -> {
            tabCode.performClick();
            EditText editCode = editorActivity.findViewById(R.id.edit_code);
            TextView titleView = editorActivity.findViewById(R.id.txt_editor_title);
            titleView.setText("test.xml");
            editorActivity.getCurrentDocumentForTest().setTitle("test.xml");
            editCode.setText(malformedXml);
            tabPreview.performClick();
        });
        instrumentation.waitForIdleSync();

        assertEquals(View.VISIBLE, xmlLayout.getVisibility());
        assertEquals(View.GONE, webView.getVisibility());
        assertTrue("XML error must be surfaced", txtXmlStatus.getText().toString().contains("解析异常") || txtXmlStatus.getText().toString().contains("⚠"));

        // Finish activity & cleanup
        runTestOnUiThread(editorActivity::finish);
        repo.deleteDocument(htmlDocId);
        instrumentation.removeMonitor(monitor);
    }
}
