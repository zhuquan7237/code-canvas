package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.webkit.WebView;

import com.nous.codecanvas.data.DocumentKind;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A saved notebook opens offline from a blob, not from the empty editor text field. */
public class NotebookPreviewDeviceTest extends InstrumentationTestCase {
    public void testOpenReopenOfflineAndPreserveOriginal() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        DocumentRepository repository = new DocumentRepository(context);
        File source = File.createTempFile("analysis", ".ipynb", context.getCacheDir());
        byte[] bytes = ("{\r\n \"nbformat\":4,\"nbformat_minor\":0,\"cells\":["
                + "{\"cell_type\":\"markdown\",\"source\":[\"# Bench\", \"mark sample\"]},"
                + "{\"cell_type\":\"code\",\"source\":\"print(123)\",\"outputs\":["
                + "{\"output_type\":\"stream\",\"text\":\"SAVED_OUTPUT_123\"},"
                + "{\"output_type\":\"display_data\",\"data\":{\"image/png\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAEElEQVR4AQEFAPr/AMhQQf8FlgJZf4NzlwAAAABJRU5ErkJggg==\"}},"
                + "{\"output_type\":\"display_data\",\"data\":{\"text/html\":\""
                + "<script>document.body.textContent='UNSAFE_SHOULD_NOT_APPEAR'</script>\"}}]}]}\r\n")
                .getBytes(StandardCharsets.UTF_8);
        CanvasDocument doc = null;
        EditorActivity editor = null;
        try {
            Files.write(source.toPath(), bytes);
            FileImporter.Imported imported = FileImporter.importFrom(context, Uri.fromFile(source));
            assertEquals(DocumentKind.NOTEBOOK, imported.kind);
            doc = imported.document;
            assertTrue(doc.isBlob());
            assertEquals("", doc.getContent());
            assertEquals("application/x-ipynb+json", com.nous.codecanvas.util.SourceWriter.mimeFor(doc));
            assertEquals("application/x-ipynb+json", com.nous.codecanvas.util.ShareExporter.mimeTypeFor(doc.getTitle()));
            FileImporter.writeBlob(context, doc.getId(), imported.bytes);
            repository.saveDocument(doc);
            assertTrue(source.delete()); // opening must never depend on the original provider
            editor = open(context, doc);
            assertPreview(editor);
            close(editor);
            editor = open(context, repository.findById(doc.getId()));
            assertPreview(editor);
            assertTrue(Arrays.equals(bytes, FileImporter.readBlob(context, doc.getId())));
        } finally {
            if (editor != null) close(editor);
            if (doc != null) {
                repository.deleteDocument(doc.getId());
                Files.deleteIfExists(FileImporter.blobFile(context, doc.getId()).toPath());
            }
            Files.deleteIfExists(source.toPath());
        }
    }

    private EditorActivity open(Context context, CanvasDocument doc) {
        Intent intent = new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId());
        return (EditorActivity) getInstrumentation().startActivitySync(intent);
    }

    private void close(EditorActivity editor) {
        getInstrumentation().runOnMainSync(() -> {
            if (!editor.isFinishing()) editor.finish();
        });
        getInstrumentation().waitForIdleSync();
    }

    private void assertPreview(EditorActivity editor) throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            WebView view = editor.findViewById(R.id.web_preview);
            assertNotNull(view);
            assertFalse(view.getSettings().getJavaScriptEnabled());
            assertTrue(view.getSettings().getBlockNetworkLoads());
            assertFalse(view.getSettings().getDomStorageEnabled());
            assertEquals(View.GONE, editor.findViewById(R.id.tab_code).getVisibility());
        });
        WebView web = editor.findViewById(R.id.web_preview);
        long until = android.os.SystemClock.uptimeMillis() + 15_000;
        String result = null;
        try {
            // evaluateJavascript requires the test to temporarily enable WebView evaluation.
            // First assert production settings above. The generated page CSP still blocks page scripts.
            getInstrumentation().runOnMainSync(() -> web.getSettings().setJavaScriptEnabled(true));
            while (android.os.SystemClock.uptimeMillis() < until) {
                CountDownLatch ready = new CountDownLatch(1);
                AtomicReference<String> contents = new AtomicReference<>();
                getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                        "document.body && document.body.innerText + ' IMG:' + (document.querySelector('img') || {}).naturalWidth", value -> { contents.set(value); ready.countDown(); }));
                if (ready.await(1, TimeUnit.SECONDS)) result = contents.get();
                if (result != null && result.contains("Benchmark sample") && result.contains("IMG:1")) break;
                Thread.sleep(200);
            }
        } finally {
            getInstrumentation().runOnMainSync(() -> web.getSettings().setJavaScriptEnabled(false));
        }
        assertNotNull(result);
        assertTrue("joined source missing from real WebView: " + result, result.contains("Benchmark sample"));
        assertTrue("saved output missing from real WebView", result.contains("SAVED_OUTPUT_123"));
        assertTrue("image must decode", result.contains("IMG:1"));
        assertFalse(result.contains("UNSAFE_SHOULD_NOT_APPEAR"));
    }
}
