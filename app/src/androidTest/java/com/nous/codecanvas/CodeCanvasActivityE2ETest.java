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

public class CodeCanvasActivityE2ETest extends android.test.InstrumentationTestCase {

    private MainActivity mainActivity;
    private Instrumentation instrumentation;

    private final java.util.List<EditorActivity> opened = new java.util.ArrayList<>();
    private final java.util.List<Instrumentation.ActivityMonitor> monitors = new java.util.ArrayList<>();
    private Instrumentation.ActivityMonitor watchEditor() {
        Instrumentation.ActivityMonitor m=instrumentation.addMonitor(EditorActivity.class.getName(),null,false);
        monitors.add(m); return m;
    }
    @Override protected void tearDown() throws Exception {
        for(Instrumentation.ActivityMonitor m:monitors) instrumentation.removeMonitor(m);
        instrumentation.runOnMainSync(() -> { for(EditorActivity e:opened) if(!e.isFinishing())e.finish(); if(mainActivity!=null)mainActivity.finish(); });
        instrumentation.waitForIdleSync(); super.tearDown();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        instrumentation = getInstrumentation();
        mainActivity = (MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
    }

    public void testE2ECreateWithArbitrarySuffixEditUndoRedoSaveReopen() throws Throwable {
        final DocumentRepository repo = new DocumentRepository(mainActivity);
        final String arbitraryTitle = "custom_test_" + System.currentTimeMillis() + ".shader.glsl.custom";
        final String initialCode = "// Vertex Shader initial code\nvoid main() {\n  gl_Position = vec4(0.0);\n}";
        final String editedCode = "// Vertex Shader edited code\nvoid main() {\n  gl_Position = vec4(1.0);\n}";

        // Step 1: Create document with arbitrary suffix and verify dialog helper confirms input
        final AtomicReference<String> confirmedName = new AtomicReference<>();
        final CountDownLatch dialogLatch = new CountDownLatch(1);
        runTestOnUiThread(() -> {
            AlertDialog dialog = com.nous.codecanvas.ui.UiDialogHelper.createThemedInputDialog(
                    mainActivity,
                    "新建画布",
                    "输入文件名",
                    arbitraryTitle,
                    name -> {
                        confirmedName.set(name);
                        dialogLatch.countDown();
                    }
            );
            dialog.show();
            Button btnConfirm = dialog.findViewById(R.id.dialog_btn_positive);
            assertNotNull("dialog_btn_positive must exist in naming dialog", btnConfirm);
            btnConfirm.performClick();
        });
        assertTrue("Naming dialog positive button click must confirm name", dialogLatch.await(3, TimeUnit.SECONDS));
        assertEquals(arbitraryTitle, confirmedName.get());

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

        Instrumentation.ActivityMonitor monitor = watchEditor();
        mainActivity.startActivity(intent);
        final EditorActivity editorActivity = (EditorActivity) monitor.waitForActivityWithTimeout(30000);
        assertNotNull("EditorActivity should launch", editorActivity);
        opened.add(editorActivity);

        // Verify title displayed
        final TextView txtTitle = editorActivity.findViewById(R.id.txt_editor_title);
        final EditText editCode = editorActivity.findViewById(R.id.edit_code);
        final ImageButton btnUndo = editorActivity.findViewById(R.id.btn_undo);
        final ImageButton btnRedo = editorActivity.findViewById(R.id.btn_redo);
        final Button btnPaste = editorActivity.findViewById(R.id.btn_paste);

        instrumentation.waitForIdleSync();
        assertEquals(arbitraryTitle, txtTitle.getText().toString());
        assertEquals(initialCode, editCode.getText().toString());

        // Step 3: Test Clipboard Paste button with selection preservation and replace+undo
        // When selection exists (start != end), btnPaste directly replaces selection without popup!
        runTestOnUiThread(() -> {
            ClipboardManager cm = (ClipboardManager) editorActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("test_clip", "// Pasted by Test");
            cm.setPrimaryClip(clip);

            // Select initialCode to test direct replace
            editCode.setSelection(0, editCode.length());
            btnPaste.performClick();
        });
        instrumentation.waitForIdleSync();
        assertTrue("Editor must contain pasted content", editCode.getText().toString().contains("// Pasted by Test"));

        // Test replace selection directly and preserve selection / undo
        runTestOnUiThread(() -> {
            editCode.setText("1234567890");
            editCode.setSelection(2, 5); // selects "345"
            ClipboardManager cm = (ClipboardManager) editorActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("replace_clip", "REPLACED");
            cm.setPrimaryClip(clip);
            btnPaste.performClick();
        });
        instrumentation.waitForIdleSync();
        assertEquals("12REPLACED67890", editCode.getText().toString());

        // Test undo of replaced text
        runTestOnUiThread(() -> {
            assertTrue("Undo should be enabled after replace", btnUndo.isEnabled());
            btnUndo.performClick();
        });
        instrumentation.waitForIdleSync();
        assertEquals("1234567890", editCode.getText().toString());

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
        // Reopen the actual Activity, not only the storage object.
        EditorActivity reopened = (EditorActivity) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), EditorActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId()));
        opened.add(reopened);
        instrumentation.waitForIdleSync();
        runTestOnUiThread(() -> {
            assertEquals(arbitraryTitle, ((TextView)reopened.findViewById(R.id.txt_editor_title)).getText().toString());
            assertEquals(editedCode, ((EditText)reopened.findViewById(R.id.edit_code)).getText().toString());
        });
        runTestOnUiThread(reopened::finish);
        instrumentation.waitForIdleSync();

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

        // Set the app-wide script preference before the editor reads it, so this test asserts the
        // default path rather than whatever a previous run left behind.
        com.nous.codecanvas.util.CanvasPrefs.setScriptsAllowed(mainActivity, true);

        Intent intent = new Intent(mainActivity, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Instrumentation.ActivityMonitor monitor = watchEditor();
        mainActivity.startActivity(intent);
        final EditorActivity editorActivity = (EditorActivity) monitor.waitForActivityWithTimeout(30000);
        assertNotNull("EditorActivity should launch", editorActivity);
        opened.add(editorActivity);
        instrumentation.waitForIdleSync();

        final TextView tabPreview = editorActivity.findViewById(R.id.tab_preview);
        final TextView tabCode = editorActivity.findViewById(R.id.tab_code);
        final WebView webView = editorActivity.findViewById(R.id.web_preview);
        final Switch switchJs = editorActivity.findViewById(R.id.switch_allow_js);

        // Scripts ship ON by default: code that needs JS (charts, animation, calculators) must
        // render without the user first hunting for a toggle. Assert both the default and the
        // switch's default position.
        runTestOnUiThread(() -> {
            tabPreview.performClick();
        });
        instrumentation.waitForIdleSync();
        Thread.sleep(1500); // Allow webview to render

        final AtomicReference<String> resultJsByDefault = new AtomicReference<>();
        final CountDownLatch latchDefault = new CountDownLatch(1);
        runTestOnUiThread(() -> {
            webView.evaluateJavascript("document.getElementById('headline') ? document.getElementById('headline').innerText : 'NO_ELEMENT'", value -> {
                resultJsByDefault.set(value);
                latchDefault.countDown();
            });
        });
        assertTrue(latchDefault.await(3, TimeUnit.SECONDS));
        runTestOnUiThread(() -> assertTrue("the switch shows the default (on)", switchJs.isChecked()));
        assertEquals("scripts run without being asked", "\"JS Executed\"", resultJsByDefault.get());

        // Turning it off must still cut the page's script off. Which way the default points is a
        // product decision; that the switch still works is the safety property, so it stays tested.
        runTestOnUiThread(() -> {
            switchJs.setChecked(false);
        });
        instrumentation.waitForIdleSync();
        Thread.sleep(800);
        final AtomicReference<String> resultJsDisabled = new AtomicReference<>();
        final CountDownLatch latch1 = new CountDownLatch(1);
        runTestOnUiThread(() -> {
            webView.evaluateJavascript("document.getElementById('headline') ? document.getElementById('headline').innerText : 'NO_ELEMENT'", value -> {
                resultJsDisabled.set(value);
                latch1.countDown();
            });
        });
        assertTrue(latch1.await(3, TimeUnit.SECONDS));
        assertEquals("with the switch off the page cannot run script", "null", resultJsDisabled.get());

        // Switch it back on and re-render; the choice is remembered app-wide
        runTestOnUiThread(() -> {
            switchJs.setChecked(true);
        });
        instrumentation.waitForIdleSync();

        final AtomicReference<String> resultJsEnabled = new AtomicReference<>();
        final CountDownLatch latch2 = new CountDownLatch(1);
        long jsDeadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < jsDeadline) {
            final CountDownLatch pollLatch = new CountDownLatch(1);
            runTestOnUiThread(() -> {
                webView.evaluateJavascript("document.getElementById('headline') ? document.getElementById('headline').innerText : 'NO_ELEM'", value -> {
                    if (value != null && value.contains("JS Executed")) {
                        resultJsEnabled.set(value);
                        latch2.countDown();
                    }
                    pollLatch.countDown();
                });
            });
            pollLatch.await(500, TimeUnit.MILLISECONDS);
            if (latch2.getCount() == 0) break;
            Thread.sleep(300);
        }
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

        // Step 7: Test Network switch gating (default blockNetworkLoads=true, confirm dialog allows, click disable, new Editor reverts)
        runTestOnUiThread(() -> {
            TextView titleView = editorActivity.findViewById(R.id.txt_editor_title);
            EditText editCode = editorActivity.findViewById(R.id.edit_code);
            titleView.setText("test.html");
            editorActivity.getCurrentDocumentForTest().setTitle("test.html");
            editCode.setText("<html><body><h1>Online Test</h1></body></html>");
            tabPreview.performClick();
        });
        instrumentation.waitForIdleSync();

        // 1. Initial state: blockNetworkLoads must be true by default
        final AtomicReference<Boolean> blockLoadsInitial = new AtomicReference<>();
        runTestOnUiThread(() -> blockLoadsInitial.set(webView.getSettings().getBlockNetworkLoads()));
        assertTrue("blockNetworkLoads must be true by default", blockLoadsInitial.get());
        final Button btnNetwork = editorActivity.findViewById(R.id.btn_preview_network);
        assertNotNull("btn_preview_network must exist in preview toolbar", btnNetwork);
        assertEquals("联网加载", btnNetwork.getText().toString());

        // Actual user confirmation; never call the private implementation instead.
        runTestOnUiThread(() -> {
            btnNetwork.performClick();
            AlertDialog confirmation=editorActivity.getActiveDialogForTest();
            assertNotNull("Network confirmation must appear",confirmation);
            assertTrue("Network stays blocked before consent",webView.getSettings().getBlockNetworkLoads());
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        instrumentation.waitForIdleSync();

        final AtomicReference<Boolean> blockLoadsAllowed = new AtomicReference<>();
        runTestOnUiThread(() -> blockLoadsAllowed.set(webView.getSettings().getBlockNetworkLoads()));
        assertFalse("blockNetworkLoads must be false when allowed", blockLoadsAllowed.get());
        assertEquals("切回离线", btnNetwork.getText().toString());

        // 3. Click btn_preview_network when allowed -> directly switches back to offline
        runTestOnUiThread(() -> {
            btnNetwork.performClick();
        });
        instrumentation.waitForIdleSync();

        final AtomicReference<Boolean> blockLoadsReverted = new AtomicReference<>();
        runTestOnUiThread(() -> blockLoadsReverted.set(webView.getSettings().getBlockNetworkLoads()));
        assertTrue("blockNetworkLoads must be true after clicking to disable", blockLoadsReverted.get());
        assertEquals("联网加载", btnNetwork.getText().toString());

        // Finish activity & cleanup
        runTestOnUiThread(editorActivity::finish);
        instrumentation.waitForIdleSync();
        instrumentation.removeMonitor(monitor);

        // 4. Launch a brand new EditorActivity targeting a document, verify network is reverted to default false (blockNetworkLoads=true)
        Intent newIntent = new Intent(mainActivity, EditorActivity.class);
        newIntent.putExtra(EditorActivity.EXTRA_DOC_ID, htmlDocId);
        newIntent.putExtra(EditorActivity.EXTRA_START_PREVIEW, true);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Instrumentation.ActivityMonitor monitor2 = watchEditor();
        mainActivity.startActivity(newIntent);
        final EditorActivity newEditorActivity = (EditorActivity) monitor2.waitForActivityWithTimeout(30000);
        assertNotNull("New EditorActivity should launch", newEditorActivity);
        opened.add(newEditorActivity);
        instrumentation.waitForIdleSync();

        WebView newWebView = newEditorActivity.findViewById(R.id.web_preview);
        final AtomicReference<Boolean> newBlockLoads = new AtomicReference<>();
        runTestOnUiThread(() -> newBlockLoads.set(newWebView.getSettings().getBlockNetworkLoads()));
        assertTrue("Brand new EditorActivity must revert blockNetworkLoads to true", newBlockLoads.get());

        runTestOnUiThread(newEditorActivity::finish);
        instrumentation.waitForIdleSync();
        repo.deleteDocument(htmlDocId);
        instrumentation.removeMonitor(monitor2);
    }
}
