package com.nous.codecanvas.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.RenderKind;
import com.nous.codecanvas.util.RenderKindDetector;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** A user-invoked Android text selection action. The source app's selection is never replaced. */
public final class SelectedTextActivity extends Activity {
    static final int MAX_BYTES = 256 * 1024;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { finish(); return; }
        final String source;
        try {
            Intent incoming = getIntent();
            if (incoming == null || !Intent.ACTION_PROCESS_TEXT.equals(incoming.getAction())
                    || !"text/plain".equals(Intent.normalizeMimeType(incoming.getType()))) {
                finish();
                return;
            }
            CharSequence selection = incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (selection == null || selection.length() == 0) { fail(R.string.selection_empty); return; }
            if (selection.length() > MAX_BYTES) { fail(R.string.selection_too_large); return; }
            source = selection.toString();
        } catch (RuntimeException malformed) {
            fail(R.string.selection_empty);
            return;
        }
        if (source.trim().isEmpty()) { fail(R.string.selection_empty); return; }
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            fail(R.string.selection_too_large);
            return;
        }

        // Disk work can rewrite an existing library. Keep it off the UI thread. The isolated
        // entry point does not access the clipboard, read any URI, or render the selected code.
        new Thread(() -> {
            try {
                long now = System.currentTimeMillis();
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date(now));
                CanvasDocument doc = new CanvasDocument(UUID.randomUUID().toString(),
                        getString(R.string.selection_file_prefix) + "_" + stamp + extension(source), source, now);
                new DocumentRepository(getApplicationContext()).saveDocument(doc);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Intent editor = new Intent(this, EditorActivity.class)
                            .putExtra(EditorActivity.EXTRA_DOC_ID, doc.getId())
                            .putExtra(EditorActivity.EXTRA_START_PREVIEW, false)
                            .putExtra(EditorActivity.EXTRA_SELECTED_TEXT, true);
                    startActivity(editor);
                    finish();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) fail(R.string.selection_save_failed);
                });
            }
        }, "canvas-selected-text").start();
    }

    static String extension(String source) {
        RenderKind kind = RenderKindDetector.detect("", source);
        if (kind == RenderKind.SVG) return ".svg";
        if (kind == RenderKind.HTML) return ".html";
        if (kind == RenderKind.XML) return ".xml";
        return ".txt";
    }

    private void fail(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }
}
