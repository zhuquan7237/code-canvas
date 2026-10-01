package com.nous.codecanvas.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.editor.SyntaxHighlighter;
import com.nous.codecanvas.editor.UndoRedoManager;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.provider.CanvasFileProvider;
import com.nous.codecanvas.util.FileUtils;
import com.nous.codecanvas.util.RenderKind;
import com.nous.codecanvas.util.RenderKindDetector;
import com.nous.codecanvas.util.SvgWrapper;
import com.nous.codecanvas.util.XmlFormatter;
import com.nous.codecanvas.util.XmlValidator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class EditorActivity extends Activity {

    public static final String EXTRA_DOC_ID = "extra_doc_id";
    private static final int REQ_EXPORT_SAF = 1002;

    private DocumentRepository repository;
    private CanvasDocument currentDocument;

    // Views
    private TextView txtEditorTitle;
    private View layoutTitleClickable;
    private TextView tabCode;
    private TextView tabPreview;
    private ImageButton btnExportSaf;
    private ImageButton btnShare;
    private ImageButton btnBack;

    private View toolbarEditorActions;
    private ImageButton btnUndo;
    private ImageButton btnRedo;
    private Button btnPaste;
    private TextView txtEditorStats;
    private TextView txtSaveIndicator;

    private View toolbarPreviewControls;
    private TextView txtRenderModeBadge;
    private Switch switchAllowJs;

    private ViewFlipper viewFlipper;
    private EditText editCode;
    private WebView webPreview;
    private View layoutXmlView;
    private TextView txtXmlStatus;
    private TextView txtXmlContent;

    // State & Helpers
    private final UndoRedoManager undoRedoManager = new UndoRedoManager();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService saveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean isProgrammaticChange = false;
    private boolean isUndoRedoAction = false;
    private boolean isAllowJs = false;

    // Debounce Runnables
    private final Runnable autoSaveRunnable = new Runnable() {
        @Override
        public void run() {
            saveCurrentDocument();
        }
    };

    private final Runnable highlightRunnable = new Runnable() {
        @Override
        public void run() {
            applySyntaxHighlight();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        View rootView = findViewById(R.id.root_editor);
        if (rootView != null) {
            rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                );
                return insets;
            });
        }

        repository = new DocumentRepository(this);
        initViews();
        setupWebView();
        setupListeners();

        String docId = getIntent().getStringExtra(EXTRA_DOC_ID);
        if (docId != null) {
            try {
                currentDocument = repository.findById(docId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (currentDocument == null) {
            currentDocument = new CanvasDocument(String.valueOf(System.currentTimeMillis()), "untitled.html", "", System.currentTimeMillis());
        }

        bindDocumentData();

        // Restore if savedInstanceState
        if (savedInstanceState != null) {
            isAllowJs = savedInstanceState.getBoolean("key_allow_js", false);
            switchAllowJs.setChecked(isAllowJs);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Persist code content into currentDocument and repository rather than packing large strings in Binder bundle
        saveCurrentDocument();
        outState.putBoolean("key_allow_js", isAllowJs);
    }

    private void initViews() {
        txtEditorTitle = findViewById(R.id.txt_editor_title);
        layoutTitleClickable = findViewById(R.id.layout_title_clickable);
        tabCode = findViewById(R.id.tab_code);
        tabPreview = findViewById(R.id.tab_preview);
        btnExportSaf = findViewById(R.id.btn_export_saf);
        btnShare = findViewById(R.id.btn_share);
        btnBack = findViewById(R.id.btn_back);

        toolbarEditorActions = findViewById(R.id.toolbar_editor_actions);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnPaste = findViewById(R.id.btn_paste);
        txtEditorStats = findViewById(R.id.txt_editor_stats);
        txtSaveIndicator = findViewById(R.id.txt_save_indicator);

        toolbarPreviewControls = findViewById(R.id.toolbar_preview_controls);
        txtRenderModeBadge = findViewById(R.id.txt_render_mode_badge);
        switchAllowJs = findViewById(R.id.switch_allow_js);

        viewFlipper = findViewById(R.id.view_flipper);
        viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.tab_in));
        viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.tab_out));

        editCode = findViewById(R.id.edit_code);
        webPreview = findViewById(R.id.web_preview);
        layoutXmlView = findViewById(R.id.layout_xml_view);
        txtXmlStatus = findViewById(R.id.txt_xml_status);
        txtXmlContent = findViewById(R.id.txt_xml_content);
    }

    private void setupWebView() {
        WebSettings settings = webPreview.getSettings();
        // Strict Sandbox Defaults
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setGeolocationEnabled(false);

        // Responsive Zoom
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webPreview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Prevent navigation escaping sandbox
                return true;
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        layoutTitleClickable.setOnClickListener(v -> showRenameDialog());

        tabCode.setOnClickListener(v -> switchToTab(0));
        tabPreview.setOnClickListener(v -> switchToTab(1));

        btnExportSaf.setOnClickListener(v -> startSafExport());
        btnShare.setOnClickListener(v -> shareFile());

        btnUndo.setOnClickListener(v -> performUndo());
        btnRedo.setOnClickListener(v -> performRedo());

        btnPaste.setOnClickListener(v -> performPaste());

        switchAllowJs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAllowJs = isChecked;
            webPreview.getSettings().setJavaScriptEnabled(isAllowJs);
            renderPreview();
        });

        editCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isProgrammaticChange) return;

                if (!isUndoRedoAction) {
                    undoRedoManager.pushState(s.toString(), editCode.getSelectionStart());
                    updateUndoRedoButtons();
                }

                updateStats(s.toString());

                // Debounce Auto-Save (800ms)
                txtSaveIndicator.setText("保存中...");
                handler.removeCallbacks(autoSaveRunnable);
                handler.postDelayed(autoSaveRunnable, 800);

                // Debounce Syntax Highlighter (300ms)
                handler.removeCallbacks(highlightRunnable);
                handler.postDelayed(highlightRunnable, 300);
            }
        });
    }

    private void bindDocumentData() {
        txtEditorTitle.setText(currentDocument.getTitle());
        isProgrammaticChange = true;
        editCode.setText(currentDocument.getContent());
        isProgrammaticChange = false;

        undoRedoManager.pushState(currentDocument.getContent(), 0);
        updateUndoRedoButtons();
        updateStats(currentDocument.getContent());
        applySyntaxHighlight();
    }

    private void updateStats(String text) {
        int charCount = text.length();
        int lineCount = text.isEmpty() ? 1 : text.split("\r\n|\r|\n", -1).length;
        txtEditorStats.setText(lineCount + " 行 · " + charCount + " 字符");
    }

    private void updateUndoRedoButtons() {
        btnUndo.setEnabled(undoRedoManager.canUndo());
        btnUndo.setAlpha(undoRedoManager.canUndo() ? 1.0f : 0.35f);

        btnRedo.setEnabled(undoRedoManager.canRedo());
        btnRedo.setAlpha(undoRedoManager.canRedo() ? 1.0f : 0.35f);
    }

    private void performUndo() {
        UndoRedoManager.EditSnapshot snapshot = undoRedoManager.undo();
        if (snapshot != null) {
            isUndoRedoAction = true;
            editCode.setText(snapshot.text);
            if (snapshot.cursorPosition <= snapshot.text.length()) {
                editCode.setSelection(Math.max(0, snapshot.cursorPosition));
            }
            isUndoRedoAction = false;
            updateUndoRedoButtons();
        }
    }

    private void performRedo() {
        UndoRedoManager.EditSnapshot snapshot = undoRedoManager.redo();
        if (snapshot != null) {
            isUndoRedoAction = true;
            editCode.setText(snapshot.text);
            if (snapshot.cursorPosition <= snapshot.text.length()) {
                editCode.setSelection(Math.max(0, snapshot.cursorPosition));
            }
            isUndoRedoAction = false;
            updateUndoRedoButtons();
        }
    }

    private void performPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(this);
                if (text != null) {
                    if (text.length() > 2 * 1024 * 1024) { // 2MB cap
                        Toast.makeText(this, "剪贴板代码过大 (超过 2MB 限制)", Toast.LENGTH_LONG).show();
                        return;
                    }
                    int start = Math.max(0, editCode.getSelectionStart());
                    int end = Math.max(0, editCode.getSelectionEnd());
                    editCode.getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());
                    Toast.makeText(this, "已粘贴剪贴板代码", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void applySyntaxHighlight() {
        int cursor = editCode.getSelectionStart();
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        isProgrammaticChange = true;
        SyntaxHighlighter.highlight(editCode.getText(), isDark);
        isProgrammaticChange = false;
        if (cursor >= 0 && cursor <= editCode.length()) {
            editCode.setSelection(cursor);
        }
    }

    private void switchToTab(int tabIndex) {
        if (viewFlipper.getDisplayedChild() == tabIndex) return;

        if (tabIndex == 0) {
            // Switch to Code Editor
            tabCode.setTextColor(0xFFFFFFFF);
            tabCode.setBackgroundResource(R.color.canvas_teal_primary);
            tabPreview.setTextColor(getResources().getColor(R.color.canvas_ink_secondary));
            tabPreview.setBackgroundColor(0x00000000);

            toolbarEditorActions.setVisibility(View.VISIBLE);
            toolbarPreviewControls.setVisibility(View.GONE);
            viewFlipper.setDisplayedChild(0);
        } else {
            // Switch to Preview Canvas
            tabPreview.setTextColor(0xFFFFFFFF);
            tabPreview.setBackgroundResource(R.color.canvas_teal_primary);
            tabCode.setTextColor(getResources().getColor(R.color.canvas_ink_secondary));
            tabCode.setBackgroundColor(0x00000000);

            toolbarEditorActions.setVisibility(View.GONE);
            toolbarPreviewControls.setVisibility(View.VISIBLE);
            viewFlipper.setDisplayedChild(1);
            renderPreview();
        }
    }

    private void renderPreview() {
        String content = editCode.getText().toString();
        String title = currentDocument.getTitle();
        RenderKind kind = RenderKindDetector.detect(title, content);

        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        if (kind == RenderKind.XML) {
            webPreview.setVisibility(View.GONE);
            layoutXmlView.setVisibility(View.VISIBLE);
            txtRenderModeBadge.setText("XML 结构视图");

            XmlValidator.ValidationResult result = XmlValidator.validateSecurely(content);
            if (result.isValid()) {
                txtXmlStatus.setText("✓ XML 结构完整有效（已启用防 XXE 安全沙箱）");
                txtXmlStatus.setTextColor(getResources().getColor(R.color.canvas_teal_dark));
            } else {
                txtXmlStatus.setText("⚠ XML 解析异常: " + result.getErrorMessage());
                txtXmlStatus.setTextColor(getResources().getColor(R.color.canvas_danger));
            }
            txtXmlContent.setText(XmlFormatter.formatForDisplay(content));
        } else {
            layoutXmlView.setVisibility(View.GONE);
            webPreview.setVisibility(View.VISIBLE);

            if (kind == RenderKind.SVG) {
                txtRenderModeBadge.setText("SVG 矢量图");
                String html = SvgWrapper.wrapSvgInResponsiveHtml(content, isDark);
                webPreview.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null);
            } else if (kind == RenderKind.HTML) {
                txtRenderModeBadge.setText("HTML5 渲染");
                webPreview.loadDataWithBaseURL("about:blank", content, "text/html", "UTF-8", null);
            } else {
                txtRenderModeBadge.setText("纯文本视图");
                String escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                String plainHtml = "<html><body style=\"font-family:monospace;padding:16px;white-space:pre-wrap;background:" +
                        (isDark ? "#121417;color:#F8FAFC;" : "#F8FAFC;color:#0F172A;") + "\">" + escaped + "</body></html>";
                webPreview.loadDataWithBaseURL("about:blank", plainHtml, "text/html", "UTF-8", null);
            }
        }
    }

    private void saveCurrentDocument() {
        if (currentDocument == null || editCode == null) return;
        currentDocument.setContent(editCode.getText().toString());
        currentDocument.setUpdatedAt(System.currentTimeMillis());
        final CanvasDocument snapshotDoc = currentDocument.snapshot();
        saveExecutor.execute(() -> {
            try {
                repository.saveDocument(snapshotDoc);
                runOnUiThread(() -> {
                    if (txtSaveIndicator != null) {
                        txtSaveIndicator.setText("已自动保存");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (txtSaveIndicator != null) {
                        txtSaveIndicator.setText("保存失败: " + e.getMessage());
                    }
                    Toast.makeText(EditorActivity.this, "保存文档失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showRenameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名画布 (可包含任意后缀)");
        final EditText input = new EditText(this);
        input.setText(currentDocument.getTitle());
        input.setSelection(currentDocument.getTitle().length());
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newTitle = FileUtils.sanitizeFileName(input.getText().toString());
            currentDocument.setTitle(newTitle);
            txtEditorTitle.setText(newTitle);
            saveCurrentDocument();
            Toast.makeText(EditorActivity.this, "已重命名为: " + newTitle, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void startSafExport() {
        saveCurrentDocument();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, currentDocument.getTitle());
        try {
            startActivityForResult(intent, REQ_EXPORT_SAF);
        } catch (Exception e) {
            Toast.makeText(this, "无法启动系统文件导出器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT_SAF && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                writeToSafUri(uri);
            }
        }
    }

    private void writeToSafUri(Uri uri) {
        final String content = editCode.getText().toString();
        new Thread(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    runOnUiThread(() -> Toast.makeText(EditorActivity.this, "成功导出文件", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(EditorActivity.this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void shareFile() {
        saveCurrentDocument();
        final String docTitle = currentDocument.getTitle();
        final String docContent = editCode.getText().toString();
        new Thread(() -> {
            try {
                File shareDir = new File(getCacheDir(), "shares");
                if (!shareDir.exists()) shareDir.mkdirs();
                File targetFile = new File(shareDir, docTitle);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(docContent.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                }

                Uri contentUri = Uri.parse("content://" + getPackageName() + CanvasFileProvider.AUTHORITY_SUFFIX + "/" + targetFile.getName());

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("*/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                runOnUiThread(() -> startActivity(Intent.createChooser(shareIntent, "分享代码画布文件")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(EditorActivity.this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentDocument();
    }

    public CanvasDocument getCurrentDocumentForTest() {
        return currentDocument;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoSaveRunnable);
        handler.removeCallbacks(highlightRunnable);
        if (webPreview != null) {
            webPreview.setWebViewClient(null);
            webPreview.loadDataWithBaseURL("about:blank", "", "text/html", "utf-8", null);
            webPreview.clearHistory();
            webPreview.removeAllViews();
            webPreview.destroy();
            webPreview = null;
        }
        saveExecutor.shutdown();
        super.onDestroy();
    }
}
