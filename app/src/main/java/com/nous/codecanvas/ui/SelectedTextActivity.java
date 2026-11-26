package com.nous.codecanvas.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.AppearanceManager;
import com.nous.codecanvas.util.RenderKind;
import com.nous.codecanvas.util.RenderKindDetector;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Temporary source inspection. The caller's selection is never replaced. */
public final class SelectedTextActivity extends Activity {
    static final int MAX_BYTES = 256 * 1024;

    // Bound native text layout: a single very long line can exhaust device memory.
    private static final int MAX_DISPLAY_CHARS = 8192;

    private Scratch scratch;
    private Button save;
    private TextView status;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppearanceManager.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // The selection can contain secrets; exclude it from task snapshots/capture.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof Scratch) {
            scratch = (Scratch) retained;
        } else if (state != null) {
            fail(R.string.selection_reopen);
            return;
        } else {
            String source = readSelection(getIntent());
            setIntent(new Intent());
            if (source == null) return;
            scratch = new Scratch(source);
        }
        setContentView(R.layout.activity_selected_text);
        View root = findViewById(R.id.root_selected_text);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            SystemBarInsets.apply(v, insets);
            return insets;
        });
        TextView source = findViewById(R.id.selected_text_source);
        int end = Math.min(MAX_DISPLAY_CHARS, scratch.source.length());
        if (end < scratch.source.length() && Character.isHighSurrogate(scratch.source.charAt(end - 1))
                && Character.isLowSurrogate(scratch.source.charAt(end))) {
            end--;
        }
        source.setText(scratch.source.substring(0, end));
        status = findViewById(R.id.selected_text_status);
        save = findViewById(R.id.btn_save_selected_text);
        findViewById(R.id.btn_close_selected_text).setOnClickListener(v -> finish());
        save.setOnClickListener(v -> {
            scratch.save(getApplicationContext(), getString(R.string.selection_file_prefix));
            updateSaveUi();
        });

        // Reapply the save status when Android recreates this window.
        updateSaveUi();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::finish);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return scratch;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scratch != null) {
            scratch.owner = this;
            updateSaveUi();
        }
    }

    @Override
    protected void onPause() {
        if (scratch != null && scratch.owner == this) scratch.owner = null;
        super.onPause();
    }

    private String readSelection(Intent incoming) {
        try {
            if (incoming == null || !Intent.ACTION_PROCESS_TEXT.equals(incoming.getAction())
                    || !"text/plain".equals(Intent.normalizeMimeType(incoming.getType()))) {
                finish();
                return null;
            }
            CharSequence selection = incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (selection == null || selection.length() == 0) {
                fail(R.string.selection_empty);
                return null;
            }
            if (selection.length() > MAX_BYTES) {
                fail(R.string.selection_too_large);
                return null;
            }
            String source = selection.toString();
            if (source.trim().isEmpty()) {
                fail(R.string.selection_empty);
                return null;
            }
            if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                fail(R.string.selection_too_large);
                return null;
            }
            return source;
        } catch (RuntimeException malformed) {
            fail(R.string.selection_empty);
            return null;
        }
    }

    private void updateSaveUi() {
        if (isFinishing()) return;
        save.setEnabled(!scratch.saving && !scratch.saved);
        status.setText(scratch.saving ? R.string.selection_saving
                : scratch.failed ? R.string.selection_save_failed
                : scratch.source.length() > MAX_DISPLAY_CHARS
                    ? R.string.selection_temporary_excerpt : R.string.selection_temporary);
        if (!scratch.saved || scratch.owner != this) return;

        if (scratch.source.length() <= MAX_DISPLAY_CHARS) {
            startActivity(new Intent(this, EditorActivity.class)
                    .putExtra(EditorActivity.EXTRA_DOC_ID, scratch.id)
                    .putExtra(EditorActivity.EXTRA_START_PREVIEW, false)
                    .putExtra(EditorActivity.EXTRA_SELECTED_TEXT, true));
        } else {
            // Do not push a long selection into a more expensive editor automatically.
            Toast.makeText(this, R.string.selection_saved_large, Toast.LENGTH_LONG).show();
        }
        finish();
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

    /** Main-thread state only. The worker captures this state and an application context. */
    private static final class Scratch {
        final String id = UUID.randomUUID().toString();
        final String source;
        SelectedTextActivity owner;
        boolean saving, saved, failed;

        Scratch(String source) {
            this.source = source;
        }

        void save(Context context, String prefix) {
            if (saving || saved) return;
            failed = false;
            saving = true;
            new Thread(() -> {
                boolean committed;
                try {
                    long now = System.currentTimeMillis();
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date(now));
                    CanvasDocument copy = new CanvasDocument(id, prefix + "_" + stamp + extension(source), source, now);
                    new DocumentRepository(context).saveDocument(copy);
                    committed = true;
                } catch (Exception failure) {
                    committed = false;
                }
                boolean result = committed;
                new Handler(Looper.getMainLooper()).post(() -> {
                    saving = false;
                    saved = result;
                    failed = !result;
                    if (owner != null) owner.updateSaveUi();
                });
            }, "canvas-save-selected-text").start();
        }
    }
}
