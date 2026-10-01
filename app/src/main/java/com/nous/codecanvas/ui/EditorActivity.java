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
import java.util.Locale;

public class EditorActivity extends Activity {

    public static final String EXTRA_DOC_ID = "extra_doc_id";
    public static final String EXTRA_START_PREVIEW = "extra_start_preview";
    private static final int REQ_EXPORT_SAF = 1002;

    private DocumentRepository repository;
    private AlertDialog activeDialog;
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
    private boolean isAllowNetwork = false;

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

        boolean startPreview = getIntent().getBooleanExtra(EXTRA_START_PREVIEW, false);
        if (startPreview) {
            switchToTab(1);
        }

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
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setGeolocationEnabled(false);

        // Responsive Zoom
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webPreview.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                activeDialog = new AlertDialog.Builder(EditorActivity.this)
                        .setTitle("页面提示")
                        .setMessage(message)
                        .setPositiveButton("确定", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                return super.onConsoleMessage(consoleMessage);
            }
        });

        webPreview.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                final String renderedContent = editCode.getText().toString();
                final boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                final String key = com.nous.codecanvas.util.PreviewKey.forDocument(currentDocument.getId(), renderedContent, dark);
                view.postVisualStateCallback(System.nanoTime(), new WebView.VisualStateCallback() {
                    @Override public void onComplete(long requestId) {
                        if (isDestroyed() || isFinishing() || viewFlipper.getDisplayedChild()!=1 || !renderedContent.equals(editCode.getText().toString())) return;
                        android.graphics.Bitmap image = PreviewThumbnailCache.capture(view);
                        if(image!=null && !saveExecutor.isShutdown()) saveExecutor.execute(() -> PreviewThumbnailCache.store(getApplicationContext(),key,image));
                    }
                });
            }
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
        findViewById(R.id.btn_preview_refresh).setOnClickListener(v -> renderPreview());
        findViewById(R.id.btn_preview_network).setOnClickListener(v -> {
            if (isAllowNetwork) { setNetworkAllowed(false); return; }
            activeDialog = new AlertDialog.Builder(this).setTitle("允许此页面联网？")
                    .setMessage("用于加载 HTTPS 图片、字体和外部脚本。页面可能向第三方网站发送请求；启用交互后，脚本也可联网。仅对当前打开的页面生效，离开后恢复离线。只开启你信任的代码。")
                    .setPositiveButton("允许本页联网", (d,w) -> setNetworkAllowed(true))
                    .setNegativeButton("保持离线",null).show();
        });
        tabCode.setOnLongClickListener(v -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("代码",editCode.getText().toString()));
            Toast.makeText(this,"已复制全部代码",Toast.LENGTH_SHORT).show();
            return true;
        });

        btnExportSaf.setOnClickListener(v -> startSafExport());
        btnShare.setOnClickListener(v -> shareFile());

        btnUndo.setOnClickListener(v -> performUndo());
        btnRedo.setOnClickListener(v -> performRedo());

        btnPaste.setOnClickListener(v -> performPaste());

        switchAllowJs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAllowJs = isChecked;
            webPreview.getSettings().setJavaScriptEnabled(isAllowJs);
            if (isAllowJs) {
                Toast.makeText(this, "已启用页面按钮与动态效果；仅运行你信任的代码", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已关闭脚本，页面仍可静态预览", Toast.LENGTH_SHORT).show();
            }
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
                CharSequence rawText = clip.getItemAt(0).coerceToText(this);
                if (rawText != null) {
                    if (rawText.length() > 2 * 1024 * 1024) { // 2MB cap
                        Toast.makeText(this, "剪贴板代码过大 (超过 2MB 限制)", Toast.LENGTH_LONG).show();
                        return;
                    }
                    final String rawString = rawText.toString();
                    java.util.List<com.nous.codecanvas.util.AiCodeExtractor.CodeBlock> blocks =
                            com.nous.codecanvas.util.AiCodeExtractor.extract(rawString);

                    if (blocks.size() > 1) {
                        // Multi-block picker
                        showMultiBlockPicker(blocks, rawString);
                    } else {
                        String textToInsert = blocks.isEmpty() ? rawString : blocks.get(0).code;
                        promptAppendOrReplace(textToInsert);
                    }
                }
            }
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMultiBlockPicker(final java.util.List<com.nous.codecanvas.util.AiCodeExtractor.CodeBlock> blocks, final String originalRaw) {
        String[] items = new String[blocks.size() + 1];
        for (int i = 0; i < blocks.size(); i++) {
            com.nous.codecanvas.util.AiCodeExtractor.CodeBlock b = blocks.get(i);
            String label = (b.language.isEmpty() ? "代码块 " + (i + 1) : b.language.toUpperCase(Locale.ROOT) + " 块 " + (i + 1));
            String snippet = b.code.length() > 30 ? b.code.substring(0, 30).replace("\n", " ") + "..." : b.code.replace("\n", " ");
            items[i] = label + " (" + snippet + ")";
        }
        items[blocks.size()] = "完整原始剪贴板文本";

        activeDialog = new AlertDialog.Builder(this)
                .setTitle("检测到多段代码块，请选择")
                .setItems(items, (dialog, which) -> {
                    if (which < blocks.size()) {
                        promptAppendOrReplace(blocks.get(which).code);
                    } else {
                        promptAppendOrReplace(originalRaw);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void promptAppendOrReplace(final String text) {
        String currentContent = editCode.getText().toString();
        if (currentContent.trim().isEmpty()) {
            insertTextIntoEditor(text, true);
            return;
        }

        // If selection exists, replace the selection directly
        int start = editCode.getSelectionStart();
        int end = editCode.getSelectionEnd();
        if (start != end && start >= 0 && end >= 0) {
            insertTextIntoEditor(text, false);
            return;
        }

        activeDialog = new AlertDialog.Builder(this)
                .setTitle("粘贴方式选择")
                .setMessage("当前画布已有代码，请选择操作：")
                .setPositiveButton("追加到末尾", (dialog, which) -> {
                    editCode.setSelection(editCode.length());
                    insertTextIntoEditor("\n\n" + text, false);
                    editCode.setSelection(editCode.length());
                })
                .setNeutralButton("覆盖替换 (可撤销)", (dialog, which) -> insertTextIntoEditor(text, true))
                .setNegativeButton("取消", null)
                .show();
    }

    private void insertTextIntoEditor(String text, boolean replaceAll) {
        if (replaceAll) {
            editCode.setText(text);
            editCode.setSelection(text.length());
        } else {
            int start = Math.max(0, editCode.getSelectionStart());
            int end = Math.max(0, editCode.getSelectionEnd());
            editCode.getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());
        }
        Toast.makeText(this, "已粘贴代码", Toast.LENGTH_SHORT).show();
    }

    private void applySyntaxHighlight() {
        int selStart = editCode.getSelectionStart();
        int selEnd = editCode.getSelectionEnd();
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        isProgrammaticChange = true;
        SyntaxHighlighter.highlight(editCode.getText(), isDark);
        isProgrammaticChange = false;
        int len = editCode.length();
        if (selStart >= 0 && selEnd >= 0 && selStart <= len && selEnd <= len) {
            editCode.setSelection(selStart, selEnd);
        } else if (selStart >= 0 && selStart <= len) {
            editCode.setSelection(selStart);
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

            findViewById(R.id.txt_preview_advice).setVisibility(View.GONE);
            toolbarEditorActions.setVisibility(View.VISIBLE);
            toolbarPreviewControls.setVisibility(View.GONE);
            viewFlipper.setDisplayedChild(0);
        } else {
            // Switch to Preview Canvas
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }

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

    private void setNetworkAllowed(boolean allowed) {
        isAllowNetwork=allowed;
        webPreview.getSettings().setBlockNetworkLoads(!allowed);
        ((Button)findViewById(R.id.btn_preview_network)).setText(allowed ? "切回离线" : "联网加载");
        renderPreview();
    }

    private void renderPreview() {
        String content = editCode.getText().toString();
        ((TextView)findViewById(R.id.txt_preview_advice)).setVisibility(View.GONE);
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

            // Display persistent, plain-language diagnostics rather than a disappearing toast
            java.util.List<String> warnings = com.nous.codecanvas.util.RenderingAdvisor.inspect(content);
            if (!warnings.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String w : warnings) {
                    sb.append("• ").append(w).append("\n");
                }
                TextView advice = findViewById(R.id.txt_preview_advice);
                advice.setText(sb.toString().trim());
                advice.setVisibility(View.VISIBLE);
            }

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
        AlertDialog dialog = UiDialogHelper.createThemedInputDialog(
                this,
                "重命名画布 (可包含任意后缀)",
                "输入文件名 (如 my_page.html, icon.svg)",
                currentDocument.getTitle(),
                newTitle -> {
                    currentDocument.setTitle(newTitle);
                    txtEditorTitle.setText(newTitle);
                    saveCurrentDocument();
                    Toast.makeText(EditorActivity.this, "已重命名为: " + newTitle, Toast.LENGTH_SHORT).show();
                }
        );
        activeDialog=dialog;
        dialog.show();
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

    public AlertDialog getActiveDialogForTest() { return activeDialog; }

    @Override
    protected void onDestroy() {
        if(activeDialog!=null && activeDialog.isShowing()) activeDialog.dismiss();
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
