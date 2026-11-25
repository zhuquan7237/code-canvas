package com.nous.codecanvas;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.widget.EditText;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.data.DocumentSaveCoordinator;
import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.CanvasConfirmDialog;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.util.SourceWriter;
import com.nous.codecanvas.util.TextCodec;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public final class FunctionalSaveRegressionTest extends InstrumentationTestCase {
    private Context context;
    private DocumentRepository repository;
    private EditorActivity editor;
    private CanvasDocument document;
    private File source;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        repository = new DocumentRepository(context);
        source = File.createTempFile("functional-", ".txt", context.getFilesDir());
        Files.write(source.toPath(), TextCodec.encode("original\r\n", TextCodec.UTF8, true));
        document = FileImporter.importFrom(context, Uri.fromFile(source)).document;
        repository.saveDocument(document);
        startEditor();
    }

    private void startEditor() {
        editor = (EditorActivity) getInstrumentation().startActivitySync(new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EditorActivity.EXTRA_DOC_ID, document.getId()));
    }

    @Override protected void tearDown() throws Exception {
        if (editor != null && !editor.isFinishing()) getInstrumentation().runOnMainSync(editor::finish);
        DocumentSaveCoordinator.drainAndWait(8000);
        if (document != null) repository.deleteDocument(document.getId());
        if (source != null) Files.deleteIfExists(source.toPath());
        super.tearDown();
    }

    private void editText(String text) {
        ((EditText) editor.findViewById(R.id.edit_code)).setText(text);
    }

    private void awaitSaved(String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        do {
            getInstrumentation().waitForIdleSync();
            CanvasDocument stored = repository.findById(document.getId());
            if (expected.equals(stored.getContent())) {
                assertTrue(DocumentSaveCoordinator.drainAndWait(5000));
                getInstrumentation().waitForIdleSync();
                return;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        fail("Expected the edited text in the app store");
    }

    public void testAutosaveLeavesOriginalAloneAndExplicitSavePreservesBom() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath());
        final String changed = "应用内\nsecond\n";
        getInstrumentation().runOnMainSync(() -> editText(changed));
        awaitSaved(changed);
        assertTrue(Arrays.equals(original, Files.readAllBytes(source.toPath())));
        CanvasDocument reopened = repository.findById(document.getId());
        assertTrue(reopened.hasByteOrderMark());
        byte[] expected = TextCodec.encode("应用内\r\nsecond\r\n", TextCodec.UTF8, true);
        assertTrue(Arrays.equals(expected, SourceWriter.payloadBytes(context, reopened, reopened.getContent())));
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_save).performClick());
        assertTrue(DocumentSaveCoordinator.drainAndWait(8000));
        assertTrue(Arrays.equals(expected, Files.readAllBytes(source.toPath())));
    }

    public void testDiscardDoesNotSaveOnPause() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            editText("discard me");
            editor.findViewById(R.id.btn_back).performClick();
            CanvasConfirmDialog.cancelButton(editor.getActiveDialogForTest()).performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertTrue(DocumentSaveCoordinator.drainAndWait(5000));
        assertEquals("original\r\n", repository.findById(document.getId()).getContent());
    }

    public void testSaveAndLeaveRetainsLocalEditWithoutWritingSource() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath());
        getInstrumentation().runOnMainSync(() -> {
            editText("keep only locally");
            editor.findViewById(R.id.btn_back).performClick();
            CanvasConfirmDialog.confirmButton(editor.getActiveDialogForTest()).performClick();
        });
        awaitSaved("keep only locally");
        assertTrue(editor.isFinishing());
        assertTrue(Arrays.equals(original, Files.readAllBytes(source.toPath())));
    }

    public void testRenameWithoutEditingPersistsForReopen() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            editor.findViewById(R.id.layout_title_clickable).performClick();
            AlertDialog dialog = editor.getActiveDialogForTest();
            ((EditText) dialog.findViewById(R.id.dialog_edit_input)).setText("renamed.md");
            dialog.findViewById(R.id.dialog_btn_positive).performClick();
        });
        assertTrue(DocumentSaveCoordinator.drainAndWait(8000));
        CanvasDocument fresh = repository.findById(document.getId());
        assertEquals("renamed.md", fresh.getTitle());
        assertEquals("original\r\n", fresh.getContent());
        assertTrue(Arrays.equals(TextCodec.encode("original\r\n", TextCodec.UTF8, true),
                Files.readAllBytes(source.toPath())));
    }

    public void testBatchShareKeepsTwoIndependentSourceFilesAndTheirBytes() throws Exception {
        CanvasDocument html = new CanvasDocument("share-html", "page.html", "<p>内容</p>\n", 1);
        html.setEncoding(TextCodec.UTF16LE);
        html.setByteOrderMark(true);
        CanvasDocument svg = new CanvasDocument("share-svg", "image.svg", "<svg/>", 1);
        Intent chooser = SourceWriter.shareDocuments(context, context.getPackageName()
                + com.nous.codecanvas.provider.CanvasFileProvider.AUTHORITY_SUFFIX,
                Arrays.asList(html, svg));
        Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.getAction());
        assertEquals(2, send.getClipData().getItemCount());
        Uri first = send.getClipData().getItemAt(0).getUri();
        Uri second = send.getClipData().getItemAt(1).getUri();
        assertEquals("page.html", first.getLastPathSegment());
        assertEquals("image.svg", second.getLastPathSegment());
        try (java.io.InputStream a = context.getContentResolver().openInputStream(first);
                java.io.InputStream b = context.getContentResolver().openInputStream(second)) {
            assertTrue(Arrays.equals(TextCodec.encode(html.getContent(), TextCodec.UTF16LE, true),
                    TextCodec.readAll(a)));
            assertEquals("<svg/>", TextCodec.decodeToString(TextCodec.readAll(b)));
        }
    }
}
