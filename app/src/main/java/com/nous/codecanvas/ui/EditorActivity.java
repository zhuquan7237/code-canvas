package com.nous.codecanvas.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentKind;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.editor.SyntaxHighlighter;
import com.nous.codecanvas.editor.TextSearch;
import com.nous.codecanvas.editor.UndoRedoManager;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.provider.CanvasFileProvider;
import com.nous.codecanvas.util.PreviewAssets;
import com.nous.codecanvas.util.PreviewKey;
import com.nous.codecanvas.util.PreviewPage;
import com.nous.codecanvas.util.SourceWriter;
import com.nous.codecanvas.util.SvgGeometry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One document: its source, its preview, and every action that touches the file.
 *
 * <p>The separation this screen enforces is the one the design asks for — <b>file data, edit
 * state and view state are independent</b>. Zooming, panning or going fullscreen never changes
 * {@code editRevision}, so looking closely at a drawing can never make the file look modified.
 * Saving writes the document's own bytes: text in the charset and line ending it was read with,
 * and binary payloads byte-for-byte.</p>
 */
public class EditorActivity extends Activity {

    public static final String EXTRA_DOC_ID = "extra_doc_id";
    public static final String EXTRA_START_PREVIEW = "extra_start_preview";
    private static final int REQ_EXPORT_SAF = 1002;

    private DocumentRepository repository;
    private AlertDialog activeDialog;
    private Dialog activeSheet;
    private CanvasDocument currentDocument;
    private DocumentKind kind = DocumentKind.UNKNOWN;

    // --- chrome -------------------------------------------------------------------------------
    private TextView txtEditorTitle;
    private TextView txtUnsavedDot;
    private View layoutTitleClickable;
    private TextView tabCode;
    private TextView tabPreview;
    private ImageButton btnExportSaf;
    private ImageButton btnShare;
    private ImageButton btnBack;
    private ImageButton btnSearch;
    private ImageButton btnSave;

    private View toolbarEditorActions;
    private ImageButton btnUndo;
    private ImageButton btnRedo;
    private Button btnPaste;
    private TextView txtEditorStats;
    private TextView txtSaveIndicator;

    private LinearLayout toolbarPreviewControls;
    private View headerEditor;
    private View layoutEditorTabs;
    private View containerPreviewCanvas;
    private View editorHelpersView;
    private Button btnPreviewFullscreen;
    private Button btnExitFullscreen;
    private boolean isFullscreen = false;
    private TextView txtRenderModeBadge;
    private Switch switchAllowJs;
    private View headerModeSwitch;
    private TextView tabCodeLand;
    private TextView tabPreviewLand;
    private boolean applyingScriptPref;

    // --- viewport -----------------------------------------------------------------------------
    private SvgViewport previewViewport;
    private View viewportToolbar;
    private Button btnViewportFit;
    private Button btnViewportFitContent;
    private Button btnViewportZoomIn;
    private Button btnViewportZoomOut;
    private TextView txtViewportZoom;
    private ImageView previewImage;
    private View pdfPager;
    private TextView txtPdfPage;
    private int pdfPageIndex;
    private int pdfPageCount = 1;
    /** Preview-only viewBox for "fit to content"; null means the document's own. */
    private String svgViewBoxOverride;

    // --- search -------------------------------------------------------------------------------
    private View searchBar;
    private EditText editSearchQuery;
    private TextView txtSearchCounter;
    private TextView btnSearchCase;
    private boolean searchCaseSensitive = false;
    private TextSearch.Result searchResult = TextSearch.find("", "", false);
    private int searchIndex = -1;

    // --- content ------------------------------------------------------------------------------
    private ViewFlipper viewFlipper;
    private CodeEditText editCode;
    private TextView txtLineNumbers;
    private ScrollView scrollLineNumbers;
    private ScrollView scrollCode;
    private WebView webPreview;
    private View layoutXmlView;
    // --- state --------------------------------------------------------------------------------
    private final UndoRedoManager undoRedoManager = new UndoRedoManager();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService saveExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private long editRevision = 0L;
    private long persistedRevision = -1L;
    private boolean documentLoadFailed = false;
    private boolean documentIsNew = false;
    private boolean isProgrammaticChange = false;
    private boolean isUndoRedoAction = false;
    private boolean isAllowJs = true;
    private boolean isAllowNetwork = false;
    private final com.nous.codecanvas.editor.PreviewState previewState = new com.nous.codecanvas.editor.PreviewState();
    private int previewLoadCount;
    private View previewLoadingView;
    private String previewBaseUrl;
    private long previewTicket = -1L;
    private boolean previewRendererDead = false;
    /** Last text pushed into the preview, so an unchanged document is not re-parsed on a tab switch. */
    private String lastRenderedContent;
    private String lastRenderedViewBox;
    /** True when the user has unsaved edits (drives the dot and the leave prompt). */
    private boolean dirty = false;
    /** The text as last saved, for "discard changes". */
    private String savedContent = "";

    public int getPreviewLoadCountForTest() {
        return previewLoadCount;
    }

    private final Runnable autoSaveRunnable = new Runnable() {
        @Override
        public void run() {
            saveCurrentDocument(false);
        }
    };

    /** Typing within this window folds into one undo step. */
    private static final long UNDO_COALESCE_MS = 700;
    /** -1 means "no burst in progress". */
    private int pendingUndoCursor = -1;
    /**
     * The text as it was when the current typing burst began. Captured once per burst (not per
     * keystroke) so a large document is not copied on every character.
     */
    private String pendingUndoText;
    /** Text as it was immediately before a discrete edit, captured in beforeTextChanged. */
    private String preDiscreteText;
    private int preDiscreteCursor;
    /** Set for edits that must be their own undo step (symbol insert, paste, replace). */
    private boolean discreteEdit;
    private final Runnable undoCommitRunnable = new Runnable() {
        @Override
        public void run() {
            commitPendingUndo();
        }
    };

    private int highlightRetries;
    private final Runnable highlightRunnable = new Runnable() {
        @Override
        public void run() {
            applySyntaxHighlight();
        }
    };

    /** Debounced preview refresh after an edit, so typing does not re-parse on every keystroke. */
    private final Runnable previewRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewFlipper != null && viewFlipper.getDisplayedChild() == 1) {
                renderPreview(true);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(com.nous.codecanvas.util.AppearanceManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        setContentView(R.layout.activity_editor);

        View rootView = findViewById(R.id.root_editor);
        if (rootView != null) {
            rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                SystemBarInsets.apply(v, insets);
                return insets;
            });
        }

        repository = new DocumentRepository(this);
        initViews();
        setupListeners();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackInvoked);
        }

        String docId = getIntent().getStringExtra(EXTRA_DOC_ID);
        if (savedInstanceState != null) {
            // A recreation races the debounced save the previous instance queued. Wait briefly so
            // the reload below cannot read an older revision and then overwrite the last edits.
            com.nous.codecanvas.data.DocumentSaveCoordinator.drainAndWait(2500);
        }
        String loadFailure = null;
        if (docId != null) {
            try {
                currentDocument = repository.findById(docId);
            } catch (Exception e) {
                android.util.Log.e("CodeCanvas", "could not open document " + docId, e);
                loadFailure = "读取本地存储时出错：" + describe(e);
            }
            if (currentDocument == null && loadFailure == null) {
                loadFailure = "这个文件已经不在本地了，可能已在首页被删除。";
            }
        }
        if (loadFailure != null) {
            // Deliberately NOT the old behaviour: creating an empty untitled.html here turned a
            // read error into "all my code vanished", and the next save wrote that emptiness over
            // the real file. Fail visibly, and never save in this state.
            showLoadFailure(loadFailure);
            return;
        }
        if (currentDocument == null) {
            currentDocument = new CanvasDocument(String.valueOf(System.currentTimeMillis()),
                    "untitled.html", "", System.currentTimeMillis());
            persistedRevision = -1L;
            documentIsNew = true;
        } else {
            persistedRevision = currentDocument.getRevision();
            editRevision = currentDocument.getRevision();
        }
        kind = currentDocument.kind();
        savedContent = currentDocument.getContent();

        bindDocumentData();

        isAllowJs = com.nous.codecanvas.util.CanvasPrefs.scriptsAllowed(this);
        isAllowNetwork = com.nous.codecanvas.util.CanvasPrefs.networkAllowedByDefault(this);
        if (savedInstanceState != null) {
            isAllowJs = savedInstanceState.getBoolean("key_allow_js", isAllowJs);
            isAllowNetwork = savedInstanceState.getBoolean("key_allow_network", isAllowNetwork);
            searchCaseSensitive = savedInstanceState.getBoolean("key_search_case", false);
        }
        applyingScriptPref = true;
        switchAllowJs.setChecked(isAllowJs);
        applyingScriptPref = false;
        applyKindToChrome();
        if (sourceTooLarge) showSourceTooLarge(currentDocument.getContent().length());

        boolean startPreview = getIntent().getBooleanExtra(EXTRA_START_PREVIEW, false);
        if (!kind.hasSourceTab()) {
            // Images and PDFs have no editor tab at all; opening in "code" would show an empty
            // text box over a file the user cannot edit.
            startPreview = true;
        }
        switchToTab(startPreview ? 1 : 0);
        applyEditorChrome();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyEditorChrome();
    }

    /**
     * Portrait keeps a code/preview switch under the title. Landscape is one row:
     * back, name, the same switch, save, overflow. Undo, the symbol strip, the
     * script switch and the zoom bar fold away so the page keeps most of the height.
     */
    private void applyEditorChrome() {
        boolean land = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        boolean hasSource = kind != null && kind.hasSourceTab() && !sourceTooLarge;
        boolean preview = viewFlipper != null && viewFlipper.getDisplayedChild() == 1;
        boolean html = kind == DocumentKind.HTML;
        if (layoutEditorTabs != null) {
            layoutEditorTabs.setVisibility(!land && hasSource ? View.VISIBLE : View.GONE);
        }
        if (headerModeSwitch != null) {
            headerModeSwitch.setVisibility(land && hasSource ? View.VISIBLE : View.GONE);
        }
        if (toolbarEditorActions != null) {
            toolbarEditorActions.setVisibility(!land && !preview && hasSource ? View.VISIBLE : View.GONE);
        }
        if (editorHelpersView != null) {
            editorHelpersView.setVisibility(!land && !preview && hasSource ? View.VISIBLE : View.GONE);
        }
        View actions = findViewById(R.id.preview_action_row);
        if (actions != null) actions.setVisibility(!land && preview ? View.VISIBLE : View.GONE);
        boolean zoomable = kind == DocumentKind.SVG || kind == DocumentKind.MERMAID
                || isImageKind() || kind == DocumentKind.PDF;
        if (viewportToolbar != null) {
            viewportToolbar.setVisibility(!land && preview && zoomable ? View.VISIBLE : View.GONE);
        }
        if (switchAllowJs != null) {
            switchAllowJs.setVisibility(!land && preview && html ? View.VISIBLE : View.GONE);
        }
        View network = findViewById(R.id.btn_preview_network);
        if (network != null) {
            network.setVisibility(!land && preview && html ? View.VISIBLE : View.GONE);
        }
        if (containerPreviewCanvas != null) {
            int pad = land ? dp(4) : dp(8);
            containerPreviewCanvas.setPadding(pad, pad, pad, pad);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveCurrentDocument(false);
        outState.putBoolean("key_allow_js", isAllowJs);
        outState.putBoolean("key_allow_network", isAllowNetwork);
        outState.putBoolean("key_search_case", searchCaseSensitive);
    }

    private void initViews() {
        txtEditorTitle = findViewById(R.id.txt_editor_title);
        txtUnsavedDot = findViewById(R.id.txt_unsaved_dot);
        layoutTitleClickable = findViewById(R.id.layout_title_clickable);
        tabCode = findViewById(R.id.tab_code);
        tabPreview = findViewById(R.id.tab_preview);
        tabCode.setSelected(true);
        tabPreview.setSelected(false);
        btnExportSaf = findViewById(R.id.btn_export_saf);
        btnShare = findViewById(R.id.btn_share);
        btnBack = findViewById(R.id.btn_back);
        btnSearch = findViewById(R.id.btn_search);
        btnSave = findViewById(R.id.btn_save);

        toolbarEditorActions = findViewById(R.id.toolbar_editor_actions);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
        btnPaste = findViewById(R.id.btn_paste);
        txtEditorStats = findViewById(R.id.txt_editor_stats);
        txtSaveIndicator = findViewById(R.id.txt_save_indicator);

        toolbarPreviewControls = findViewById(R.id.toolbar_preview_controls);
        headerEditor = findViewById(R.id.header_editor);
        layoutEditorTabs = findViewById(R.id.layout_editor_tabs);
        containerPreviewCanvas = findViewById(R.id.container_preview_canvas);
        editorHelpersView = findViewById(R.id.editor_helpers);
        btnPreviewFullscreen = findViewById(R.id.btn_preview_fullscreen);
        btnExitFullscreen = findViewById(R.id.btn_exit_fullscreen);
        txtRenderModeBadge = findViewById(R.id.txt_render_mode_badge);
        switchAllowJs = findViewById(R.id.switch_allow_js);

        previewViewport = findViewById(R.id.preview_viewport);
        viewportToolbar = findViewById(R.id.viewport_toolbar);
        btnViewportFit = findViewById(R.id.btn_viewport_fit);
        btnViewportFitContent = findViewById(R.id.btn_viewport_fit_content);
        btnViewportZoomIn = findViewById(R.id.btn_viewport_zoom_in);
        btnViewportZoomOut = findViewById(R.id.btn_viewport_zoom_out);
        txtViewportZoom = findViewById(R.id.txt_viewport_zoom);
        previewImage = findViewById(R.id.preview_image);
        pdfPager = findViewById(R.id.pdf_pager);
        txtPdfPage = findViewById(R.id.txt_pdf_page);
        View pdfPrev = findViewById(R.id.btn_pdf_prev);
        View pdfNext = findViewById(R.id.btn_pdf_next);
        if (pdfPrev != null) pdfPrev.setOnClickListener(v -> showPdfPage(pdfPageIndex - 1));
        if (pdfNext != null) pdfNext.setOnClickListener(v -> showPdfPage(pdfPageIndex + 1));
        if (txtPdfPage != null) txtPdfPage.setOnClickListener(v -> askPdfPage());

        searchBar = findViewById(R.id.search_bar);
        editSearchQuery = findViewById(R.id.edit_search_query);
        txtSearchCounter = findViewById(R.id.txt_search_counter);
        btnSearchCase = findViewById(R.id.btn_search_case);

        viewFlipper = findViewById(R.id.view_flipper);
        viewFlipper.setInAnimation(null);
        viewFlipper.setOutAnimation(null);

        editCode = findViewById(R.id.edit_code);
        txtLineNumbers = findViewById(R.id.txt_line_numbers);
        scrollLineNumbers = findViewById(R.id.scroll_line_numbers);
        scrollCode = findViewById(R.id.scroll_code);
        if (editCode != null && txtLineNumbers != null) {
            // The gutter follows the cursor so the current line is findable at a glance.
            editCode.setOnCursorLineChanged(line -> refreshLineNumbers(line));
        }
        if (scrollCode != null && scrollLineNumbers != null) {
            scrollCode.setOnScrollChangeListener((v, sx, sy, ox, oy) -> scrollLineNumbers.scrollTo(0, sy));
        }
        // The WebView is configured lazily (see ensureWebViewReady): creating and configuring a
        // WebView costs well over a second on a cold start, and a file opened straight into the
        // code tab has no preview to show. Paying that cost on open is what made ordinary files
        // feel like they took seconds to appear.
        webPreview = findViewById(R.id.web_preview);
        layoutXmlView = findViewById(R.id.layout_xml_view);
    }

    /** True once {@link #setupWebView()} has run for the current WebView. */
    private boolean webViewConfigured = false;

    /**
     * Configure the WebView the first time a preview is needed.
     *
     * <p>Deferred on purpose: WebView construction plus settings plus client installation is the
     * single most expensive thing this screen does, and a text file opened into the code tab never
     * needs it. Doing it eagerly added roughly 1.5s to every open.</p>
     */
    private void ensureWebViewReady() {
        if (webViewConfigured || webPreview == null) return;
        webViewConfigured = true;
        setupWebView();
    }

    private void setupWebView() {
        WebSettings settings = webPreview.getSettings();
        // Strict sandbox: no file access, no content access, no bridge back into the app.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setJavaScriptEnabled(previewJavaScriptEnabled());
        settings.setBlockNetworkLoads(!isAllowNetwork);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setGeolocationEnabled(false);
        // The viewport owns pinch and pan for vector content; letting the WebView also zoom would
        // fight it and make the artwork jump between two scale systems.
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        webPreview.setBackgroundColor(getResources().getColor(R.color.canvas_surface));

        webPreview.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                activeDialog = CanvasConfirmDialog.show(EditorActivity.this,
                        "页面提示", message, "确定", "取消", false,
                        result::confirm, result::cancel);
                return true;
            }
        });

        webPreview.setWebViewClient(new PreviewWebViewClient());
    }

    /**
     * One client for the life of the screen. The old code replaced the whole client on every load,
     * which meant no callback could ever be about "the current page", and there was no
     * {@code onRenderProcessGone} at all — a renderer crash therefore took the app down with it.
     */
    private final class PreviewWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) return true;
            // Subresources stay inside the sandbox interceptor. Only a main-frame navigation
            // can leave this screen, and only for a real http(s) link.
            if (!request.isForMainFrame()) return false;
            return leavePreviewFor(request.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            return leavePreviewFor(url);
        }

        private boolean leavePreviewFor(String url) {
            if (jumpToSourceLine(url)) return true;
            // A local document must never navigate the preview away. http(s) links open in the
            // system browser; javascript: and file: are dropped so they cannot leave the sandbox.
            if (url != null && !url.startsWith("https://" + PreviewAssets.HOST)) {
                String lower = url.toLowerCase(java.util.Locale.ROOT);
                if (lower.startsWith("https://") || lower.startsWith("http://")) {
                    openExternally(url);
                }
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) return null;
            return PreviewAssets.respond(EditorActivity.this, request.getUrl().toString());
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (previewBaseUrl == null || !previewBaseUrl.equals(url)) return;
            final long ticket = previewTicket;
            if (ticket != previewState.generation) return;
            handler.removeCallbacks(previewTimeoutRunnable);
            view.postVisualStateCallback(ticket, new WebView.VisualStateCallback() {
                @Override
                public void onComplete(long id) {
                    if (isDestroyed() || isFinishing() || !previewState.ready(ticket)) return;
                    if (previewLoadingView != null) previewLoadingView.setVisibility(View.GONE);
                    boolean dark = isDark();
                    String key = PreviewKey.forDocument(currentDocument.getId(),
                            editorTextOrStored(), dark);
                    if (viewFlipper.getDisplayedChild() == 1 && supportsThumbnail()) {
                        Bitmap image = PreviewThumbnailCache.capture(view);
                        if (image != null && !saveExecutor.isShutdown()) {
                            saveExecutor.execute(() -> PreviewThumbnailCache.store(getApplicationContext(), key, image));
                        }
                    }
                }
            });
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    android.webkit.WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                markPreviewFailed("页面加载失败：" + (error == null ? "未知原因" : error.getDescription()));
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            return handleRendererGone();
        }
    }

    private final Runnable previewTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isDestroyed() || isFinishing()) return;
            if (previewTicket != previewState.generation || viewFlipper.getDisplayedChild() != 1) return;
            if (webPreview != null) webPreview.stopLoading();
            previewState.invalidate();
            if (previewLoadingView != null) previewLoadingView.setVisibility(View.GONE);
            Toast.makeText(EditorActivity.this, "预览加载超时，已停止加载。可点刷新重试。", Toast.LENGTH_LONG).show();
        }
    };

    private void markPreviewFailed(String reason) {
        if (isDestroyed() || isFinishing()) return;
        handler.removeCallbacks(previewTimeoutRunnable);
        previewState.invalidate();
        if (previewLoadingView != null) previewLoadingView.setVisibility(View.GONE);
        Toast.makeText(this, reason + "，可点刷新重试。", Toast.LENGTH_LONG).show();
    }

    /**
     * The renderer crashed or was reclaimed. Returning true keeps the app alive; the dead WebView is
     * torn down immediately, because leaving a destroyed renderer in the hierarchy is its own crash.
     */
    private boolean handleRendererGone() {
        previewRendererDead = true;
        handler.removeCallbacks(previewTimeoutRunnable);
        previewState.invalidate();
        if (previewLoadingView != null) previewLoadingView.setVisibility(View.GONE);
        detachDeadWebView();
        runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            activeDialog = CanvasConfirmDialog.show(this,
                    "预览已停止",
                    "渲染进程被系统回收或崩溃了。你的代码和编辑状态没有受影响，可以重建预览继续。",
                    "重建预览", "返回代码", false,
                    () -> {
                        rebuildPreviewRenderer();
                        renderPreview(true);
                    },
                    () -> switchToTab(0));
        });
        return true;
    }

    private void detachDeadWebView() {
        if (webPreview == null) return;
        if (webPreview.getParent() instanceof ViewGroup) {
            ((ViewGroup) webPreview.getParent()).removeView(webPreview);
        }
        try {
            webPreview.destroy();
        } catch (Exception ignored) {
        }
        webPreview = null;
    }

    private void rebuildPreviewRenderer() {
        if (webPreview != null) return;
        ViewGroup host = findViewById(R.id.preview_viewport);
        if (host == null) return;
        webPreview = new WebView(this);
        host.addView(webPreview, 0, new ViewGroup.LayoutParams(-1, -1));
        previewRendererDead = false;
        webViewConfigured = false;
        ensureWebViewReady();
    }

    private void loadPreview(String html, String fingerprint) {
        if (!previewState.request(fingerprint, false)) return;
        if (previewRendererDead || webPreview == null) rebuildPreviewRenderer();
        if (webPreview == null) return;
        ensureWebViewReady();
        final long ticket = previewState.generation;
        final String base = "https://" + PreviewAssets.HOST + "/preview/" + ticket;
        previewBaseUrl = base;
        previewTicket = ticket;
        previewLoadingView = findViewById(R.id.preview_loading);
        if (previewLoadingView != null) previewLoadingView.setVisibility(View.VISIBLE);
        previewLoadCount++;
        handler.removeCallbacks(previewTimeoutRunnable);
        handler.postDelayed(previewTimeoutRunnable, 15000);
        webPreview.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> confirmLeave());
        btnSave.setOnClickListener(v -> saveCurrentDocument(true));
        btnSearch.setOnClickListener(v -> toggleSearch());
        btnExportSaf.setOnClickListener(v -> startSafExport());
        btnShare.setOnClickListener(v -> shareFile());

        if (btnPreviewFullscreen != null) btnPreviewFullscreen.setOnClickListener(v -> setFullscreen(true));
        if (btnExitFullscreen != null) btnExitFullscreen.setOnClickListener(v -> setFullscreen(false));

        layoutTitleClickable.setOnClickListener(v -> showRenameDialog());

        tabCode.setOnClickListener(v -> switchToTab(0));
        tabPreview.setOnClickListener(v -> switchToTab(1));
        headerModeSwitch = findViewById(R.id.header_mode_switch);
        tabCodeLand = findViewById(R.id.tab_code_land);
        tabPreviewLand = findViewById(R.id.tab_preview_land);
        if (tabCodeLand != null) tabCodeLand.setOnClickListener(v -> switchToTab(0));
        if (tabPreviewLand != null) tabPreviewLand.setOnClickListener(v -> switchToTab(1));
        View overflow = findViewById(R.id.btn_editor_overflow);
        if (overflow != null) overflow.setOnClickListener(v -> showEditorOverflow(v));
        findViewById(R.id.btn_preview_refresh).setOnClickListener(v -> {
            if (previewRendererDead || webPreview == null) rebuildPreviewRenderer();
            previewState.invalidate();
            renderPreview(true);
        });
        setupEditorHelpers();
        findViewById(R.id.btn_preview_network).setOnClickListener(v -> {
            if (isAllowNetwork) {
                setNetworkAllowed(false);
                return;
            }
            activeDialog = CanvasConfirmDialog.show(this,
                    "允许此页面联网？",
                    "用于加载 HTTPS 图片、字体和外部脚本。页面可能向第三方网站发送请求；页面脚本默认是开的，联网之后脚本也能发请求。只对当前这一页生效，离开就恢复离线。只开启你信任的代码。",
                    "允许本页联网", "保持离线", false,
                    () -> setNetworkAllowed(true));
        });
        tabCode.setOnLongClickListener(v -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("代码", editCode.getText().toString()));
            Toast.makeText(this, "已复制全部代码", Toast.LENGTH_SHORT).show();
            return true;
        });

        btnUndo.setOnClickListener(v -> performUndo());
        btnRedo.setOnClickListener(v -> performRedo());
        btnPaste.setOnClickListener(v -> performPaste());

        switchAllowJs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAllowJs = isChecked;
            if (!applyingScriptPref) {
                com.nous.codecanvas.util.CanvasPrefs.setScriptsAllowed(this, isChecked);
            }
            if (webPreview != null) {
                webPreview.getSettings().setJavaScriptEnabled(previewJavaScriptEnabled());
            }
            if (applyingScriptPref || kind != DocumentKind.HTML) {
                return;
            }
            Toast.makeText(this, isAllowJs
                    ? "已启用页面按钮与动态效果；仅运行你信任的代码"
                    : "已关闭脚本，页面仍可静态预览", Toast.LENGTH_SHORT).show();
            previewState.invalidate();
            renderPreview(true);
        });

        setupViewportListeners();
        setupSearchListeners();

        editCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // A discrete edit needs the text as it was *before* the change: by the time
                // afterTextChanged runs the new text is already in the buffer, and recording that
                // would leave undo with nothing to go back to.
                if (discreteEdit && !isProgrammaticChange && !isUndoRedoAction) {
                    preDiscreteText = s.toString();
                    preDiscreteCursor = Math.max(0, start);
                } else if (!isProgrammaticChange && !isUndoRedoAction && start == 0 && count == s.length()) {
                    // A whole-buffer replacement is one deliberate step, not typing. Without this a
                    // setText followed by a paste collapses into a single history entry, and undo
                    // jumps past both to the document's loaded state.
                    preDiscreteText = s.toString();
                    preDiscreteCursor = 0;
                    discreteEdit = true;
                }
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isProgrammaticChange) return;
                editRevision++;
                markDirty(true);

                if (!isUndoRedoAction) {
                    // Coalesced: a burst of typing becomes one undo step. Pushing every keystroke
                    // made undo feel like it did nothing (one character back per press) and burned
                    // the whole history budget on a single sentence.
                    if (discreteEdit) {
                        // A symbol insert, a paste or a replace is one deliberate action, and it
                        // must be exactly one undo step. Commit any typing still inside the
                        // coalesce window first, then record the PRE-edit text captured in
                        // beforeTextChanged.
                        discreteEdit = false;
                        handler.removeCallbacks(undoCommitRunnable);
                        commitPendingUndo();
                        if (preDiscreteText != null) {
                            undoRedoManager.pushState(preDiscreteText, preDiscreteCursor);
                            preDiscreteText = null;
                        }
                        // The history must end with the text now on screen, otherwise undo() steps
                        // past it and lands on the state before the previous action.
                        undoRedoManager.pushState(s.toString(), editCode.getSelectionStart());
                        pendingUndoCursor = -1;
                    } else {
                        // Capture the burst-start text once, on the first keystroke of the burst.
                        // Reading the live buffer at commit time would record the state *after* a
                        // later paste and make that paste impossible to undo.
                        if (pendingUndoCursor < 0) {
                            pendingUndoCursor = Math.max(0, editCode.getSelectionStart() - 1);
                            pendingUndoText = editCode.getText().toString();
                        }
                        handler.removeCallbacks(undoCommitRunnable);
                        handler.postDelayed(undoCommitRunnable, UNDO_COALESCE_MS);
                    }
                    updateUndoRedoButtons();
                }

                updateStats(s.toString());
                refreshLineNumbers(currentCursorLine());

                // Debounce Auto-Save (800ms)
                txtSaveIndicator.setText("保存中…");
                handler.removeCallbacks(autoSaveRunnable);
                // A big document is serialised into the JSON store on every save; 800ms of typing
                // turned that into a steady stream of writes. Longer debounce for big files.
                handler.postDelayed(autoSaveRunnable, editCode.length() > 200_000 ? 2500 : 800);

                // Debounce Syntax Highlighter (300ms)
                handler.removeCallbacks(highlightRunnable);
                handler.postDelayed(highlightRunnable, editCode.length() > 200_000 ? 600 : 250);

                // Search results follow the text, and the preview follows the text.
                if (searchBar.getVisibility() == View.VISIBLE) refreshSearch(false);
                handler.removeCallbacks(previewRefreshRunnable);
                handler.postDelayed(previewRefreshRunnable, 450);
            }
        });
    }

    // ------------------------------------------------------------------ viewport

    private void setupViewportListeners() {
        // When the chrome around the canvas changes height (a toolbar row appearing, the keyboard
        // closing), the WebView's viewport changes but the page keeps centring against its old
        // innerHeight — the drawing ends up visibly above or below centre. Re-fit on every size
        // change that the user did not cause by zooming.
        previewViewport.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left == oldRight - oldLeft && bottom - top == oldBottom - oldTop) return;
            if (!isVectorKind() || webPreview == null) return;
            handler.removeCallbacks(refitRunnable);
            handler.postDelayed(refitRunnable, 120);
        });

        previewViewport.setOnViewportListener(new SvgViewport.OnViewportListener() {
            @Override
            public void onZoomChanged(float factor) {
                if (txtViewportZoom != null) {
                    txtViewportZoom.setText(Math.round(factor * 100) + "%");
                }
            }

            @Override
            public void onFitStateChanged(boolean fitted) {
                if (btnViewportFit != null) btnViewportFit.setSelected(fitted);
            }
        });

        btnViewportFit.setOnClickListener(v -> {
            previewViewport.fitToWindow();
            applyViewportToWeb();
        });
        btnViewportFitContent.setOnClickListener(v -> fitToContent());
        btnViewportZoomIn.setOnClickListener(v -> {
            previewViewport.zoomIn();
            applyViewportToWeb();
        });
        btnViewportZoomOut.setOnClickListener(v -> {
            previewViewport.zoomOut();
            applyViewportToWeb();
        });
        // Gestures change the transform without going through a button, so the page has to be told
        // about it as well; otherwise a pinch would scale the view and leave the SVG at 1x.
        previewViewport.setTransformSink((scale, dx, dy) -> {
            if (isVectorKind()) pushTransformToWeb(scale, dx, dy);
        });
        // Artwork can carry its own controls (play/pause, a speed toggle). The viewport owns pan
        // and pinch, so a tap that never moved is forwarded to the page as a synthetic click —
        // otherwise those buttons could never be pressed.
        previewViewport.setOnTapListener(this::forwardTapToPreview);
    }

    /**
     * Hand the current transform to the SVG page. Called only on discrete changes (a gesture end, a
     * button press), never per frame from the UI thread — the gesture stream itself is already
     * coalesced by the viewport.
     */
    private void applyViewportToWeb() {
        if (!isVectorKind()) return;
        android.graphics.RectF bounds = previewViewport.contentBoundsForTest();
        pushTransformToWeb(previewViewport.getZoomFactor(), bounds.left, bounds.top);
    }

    /**
     * Push the transform into the SVG page.
     *
     * <p>Posted and coalesced, never called inline: the viewport applies its transform from
     * {@code onSizeChanged}, which runs during layout, and calling {@code evaluateJavascript} on the
     * main thread from inside a layout pass blocks the very thread the WebView needs to answer on.
     * The fitted state (1, 0, 0) needs no message at all — that is what the page starts at.</p>
     */
    private void pushTransformToWeb(float scale, float dx, float dy) {
        if (webPreview == null || previewRendererDead) return;
        // No early return for the identity transform: "fit to window" *is* the identity, and
        // skipping it left the page stuck at whatever zoom the user had reached.
        final String js = "if(window.CCViewport){window.CCViewport.set(" + scale + "," + dx + "," + dy + ");}";
        handler.removeCallbacks(transformPushRunnable);
        transformPushRunnable = () -> {
            if (webPreview != null && !previewRendererDead) {
                webPreview.evaluateJavascript(js, null);
            }
        };
        handler.post(transformPushRunnable);
    }

    private Runnable transformPushRunnable;

    /**
     * Replay a tap into the preview page at the point the user actually touched.
     *
     * <p>The viewport's transform is applied to the page's stage, so the page coordinates of a
     * touch are the viewport coordinates run backwards through scale and translation. Dispatching a
     * synthetic {@code click} on the element under that point is what lets a drawing's own buttons
     * work while pan and pinch stay with the viewport.</p>
     */
    private void forwardTapToPreview(float x, float y) {
        if (webPreview == null || previewRendererDead) return;
        if (!isVectorKind() && !isImageKind()) return;
        float scale = previewViewport.getZoomFactor();
        if (scale <= 0f) scale = 1f;
        android.graphics.RectF bounds = previewViewport.contentBoundsForTest();
        final float pageX = (x - bounds.left) / scale;
        final float pageY = (y - bounds.top) / scale;
        final String js = "if(window.CCTap){window.CCTap(" + pageX + "," + pageY + ");}";
        handler.removeCallbacks(tapPushRunnable);
        tapPushRunnable = () -> {
            if (webPreview != null && !previewRendererDead) {
                webPreview.evaluateJavascript(js, null);
            }
        };
        handler.post(tapPushRunnable);
    }

    private Runnable tapPushRunnable;

    /** Re-apply the fitted transform after the canvas changed size. */
    private final Runnable refitRunnable = new Runnable() {
        @Override
        public void run() {
            if (webPreview == null || previewRendererDead) return;
            // Always re-push the viewport's current transform. When the user has zoomed, that keeps
            // their magnification and centre across the resize; when they have not, it is the
            // identity and the page re-centres. Resetting to identity here would throw away a zoom
            // the user had just made.
            applyViewportToWeb();
        }
    };

    private boolean isVectorKind() {
        return kind == DocumentKind.SVG || kind == DocumentKind.MERMAID;
    }

    private boolean isImageKind() {
        return kind == DocumentKind.IMAGE || kind == DocumentKind.GIF;
    }

    /**
     * "Fit to content": temporarily widen the preview's viewBox so a drawing whose own viewBox
     * clips it can be inspected. This is a preview adjustment only — the document's source is not
     * touched, and internal {@code clipPath}/{@code mask} definitions keep their meaning.
     */
    private void fitToContent() {
        if (kind != DocumentKind.SVG) {
            Toast.makeText(this, "只有 SVG 支持按内容边界适应", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = editorTextOrStored();
        SvgGeometry.Box box = SvgGeometry.measure(content);
        if (!box.isValid()) {
            Toast.makeText(this, "这个 SVG 没有可用的坐标范围（无 viewBox、宽高是百分比），只能用适应窗口", Toast.LENGTH_LONG).show();
            return;
        }
        String override = SvgGeometry.fitContentViewBox(content, 0.06);
        if (override == null) {
            Toast.makeText(this, "无法计算内容边界", Toast.LENGTH_SHORT).show();
            return;
        }
        svgViewBoxOverride = override;
        previewState.invalidate();
        renderPreview(true);
        previewViewport.fitToWindow();
        String note = "已按内容边界临时扩大预览范围（不会写回原文件）";
        if (SvgGeometry.hasInternalClipping(content)) {
            note += "。这个 SVG 内部有 clipPath/遮罩，扩大视图也无法还原被有意裁掉的部分。";
        }
        Toast.makeText(this, note, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ search

    private void setupSearchListeners() {
        editSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                refreshSearch(true);
            }
        });
        editSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            stepSearch(false);
            return true;
        });
        findViewById(R.id.btn_search_prev).setOnClickListener(v -> stepSearch(true));
        findViewById(R.id.btn_search_next).setOnClickListener(v -> stepSearch(false));
        findViewById(R.id.btn_search_close).setOnClickListener(v -> closeSearch());
        btnSearchCase.setOnClickListener(v -> {
            searchCaseSensitive = !searchCaseSensitive;
            btnSearchCase.setSelected(searchCaseSensitive);
            btnSearchCase.setTextColor(getResources().getColor(searchCaseSensitive
                    ? R.color.canvas_on_primary : R.color.canvas_ink_secondary));
            refreshSearch(false);
        });
        btnSearchCase.setSelected(false);
    }

    /** A preview link of the form codecanvas.invalid/jump?line=N opens that line in the editor. */
    private boolean jumpToSourceLine(String url) {
        String prefix = "https://" + com.nous.codecanvas.util.PreviewAssets.HOST + "/jump";
        if (url == null || !url.startsWith(prefix) || !kind.hasSourceTab()) return false;
        int line = 1;
        try {
            String query = android.net.Uri.parse(url).getQueryParameter("line");
            if (query != null) line = Integer.parseInt(query);
        } catch (Exception ignored) {
            line = 1;
        }
        final int target = Math.max(1, line);
        switchToTab(0);
        editCode.post(() -> {
            int offset = com.nous.codecanvas.editor.EditorCommands.lineOffset(
                    editCode.getText().toString(), target);
            editCode.requestFocus();
            editCode.setSelection(Math.min(offset, editCode.length()));
        });
        return true;
    }

    private void askPdfPage() {
        activeDialog = UiDialogHelper.createThemedInputDialog(this, "跳转到页",
                "页码", String.valueOf(pdfPageIndex + 1),
                android.text.InputType.TYPE_CLASS_NUMBER, "打开", false, false, raw -> {
                    try {
                        int page = Integer.parseInt(raw.trim());
                        if (page < 1 || page > pdfPageCount) {
                            Toast.makeText(this, "页码要在 1 到 " + pdfPageCount + " 之间",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showPdfPage(page - 1);
                    } catch (Exception ex) {
                        Toast.makeText(this, "请输入页码", Toast.LENGTH_SHORT).show();
                    }
                });
        activeDialog.show();
    }

    private void showFindReplace() {
        if (kind == null || !kind.hasSourceTab()) return;
        if (viewFlipper.getDisplayedChild() != 0) switchToTab(0);
        android.view.View content = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_find_replace, null);
        android.widget.EditText query = content.findViewById(R.id.find_query);
        android.widget.EditText replacement = content.findViewById(R.id.find_replacement);
        android.widget.TextView status = content.findViewById(R.id.find_status);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(content).create();
        Runnable refresh = () -> {
            String find = query.getText().toString();
            int count = 0;
            if (!find.isEmpty()) {
                String text = editCode.getText().toString();
                int from = 0;
                while ((from = text.indexOf(find, from)) >= 0) {
                    count++;
                    from += find.length();
                }
            }
            status.setText(find.isEmpty() ? "" : "找到 " + count + " 处");
        };
        query.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) { refresh.run(); }
        });
        content.findViewById(R.id.find_next).setOnClickListener(v ->
                replaceOnce(query.getText().toString(), null, false));
        content.findViewById(R.id.find_prev).setOnClickListener(v ->
                replaceOnce(query.getText().toString(), null, true));
        content.findViewById(R.id.find_replace_one).setOnClickListener(v ->
                replaceOnce(query.getText().toString(), replacement.getText().toString(), false));
        content.findViewById(R.id.dialog_btn_negative).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.dialog_btn_positive).setOnClickListener(v -> {
            try {
                String next = com.nous.codecanvas.editor.EditorCommands.replaceAll(
                        editCode.getText().toString(),
                        query.getText().toString(),
                        replacement.getText().toString());
                discreteEdit = true;
                editCode.setText(next);
                editCode.setSelection(next.length());
                refresh.run();
                Toast.makeText(this, "已全部替换", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        activeDialog = dialog;
        dialog.show();
        refresh.run();
    }

    /** Move to the next or previous match. When replacement is non-null, replace that match. */
    private void replaceOnce(String find, String replacement, boolean backward) {
        if (find == null || find.isEmpty()) {
            Toast.makeText(this, "请输入查找内容", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = editCode.getText().toString();
        int start = Math.max(0, editCode.getSelectionStart());
        int end = Math.max(start, editCode.getSelectionEnd());
        int at = backward ? text.lastIndexOf(find, Math.max(0, start - 1)) : text.indexOf(find, end);
        if (at < 0) at = backward ? text.lastIndexOf(find) : text.indexOf(find);
        if (at < 0) {
            Toast.makeText(this, "没有找到", Toast.LENGTH_SHORT).show();
            return;
        }
        if (replacement == null) {
            editCode.requestFocus();
            editCode.setSelection(at, at + find.length());
            return;
        }
        discreteEdit = true;
        editCode.getText().replace(at, at + find.length(), replacement);
        editCode.setSelection(at, at + replacement.length());
    }

    private void toggleSearch() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
            return;
        }
        if (!kind.hasSourceTab()) {
            Toast.makeText(this, "这个文件只能预览，没有可搜索的代码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (viewFlipper.getDisplayedChild() != 0) switchToTab(0);
        searchBar.setVisibility(View.VISIBLE);
        // The query box owns the keyboard while searching; nothing else may claim it.
        // Pre-fill from a selection: searching for what you just selected is the common case.
        String prefill = TextSearch.queryFromSelection(editCode.getText(),
                editCode.getSelectionStart(), editCode.getSelectionEnd());
        if (prefill != null && editSearchQuery.getText().length() == 0) {
            editSearchQuery.setText(prefill);
            editSearchQuery.setSelection(prefill.length());
        }
        editSearchQuery.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(editSearchQuery, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        refreshSearch(true);
    }

    private void closeSearch() {
        searchBar.setVisibility(View.GONE);
        // Clear the query as well as the result. Leaving the text behind meant reopening search
        // showed a stale keyword, and the highlight stayed painted on the code with no way to
        // dismiss it.
        editSearchQuery.setText("");
        searchResult = TextSearch.find("", "", false);
        searchIndex = -1;
        clearSearchHighlights();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        // Leaving the selection where the last match was is right: closing the search should not
        // move the cursor away from what the user was reading.
    }

    /** Remove the match highlight without touching the text. */
    private void clearSearchHighlights() {
        if (editCode == null) return;
        android.text.Editable text = editCode.getText();
        if (text instanceof android.text.Spannable) {
            android.text.Spannable sp = (android.text.Spannable) text;
            for (android.text.style.BackgroundColorSpan span
                    : sp.getSpans(0, sp.length(), android.text.style.BackgroundColorSpan.class)) {
                sp.removeSpan(span);
            }
        }
    }

    /**
     * Recompute matches and highlight them. {@code moveToFirst} jumps to the first match when the
     * query changed; a text edit keeps the current match if it still exists.
     */
    private void refreshSearch(boolean moveToFirst) {
        String query = editSearchQuery.getText().toString();
        int previousStart = -1;
        if (searchIndex >= 0 && searchIndex < searchResult.count()) {
            previousStart = searchResult.matches.get(searchIndex).start;
        }
        searchResult = TextSearch.find(editCode.getText().toString(), query, searchCaseSensitive);

        if (searchResult.isEmpty()) {
            searchIndex = -1;
            txtSearchCounter.setText("0 / 0");
            txtSearchCounter.setTextColor(getResources().getColor(R.color.canvas_ink_secondary));
            applySearchHighlights();
            return;
        }

        if (moveToFirst) {
            searchIndex = 0;
        } else if (previousStart >= 0) {
            searchIndex = Math.min(searchResult.count() - 1, searchResult.indexAtOrAfter(previousStart) - 1);
            if (searchIndex < 0) searchIndex = 0;
        } else {
            searchIndex = 0;
        }
        txtSearchCounter.setText(TextSearch.counter(searchIndex, searchResult.count()));
        txtSearchCounter.setTextColor(getResources().getColor(R.color.canvas_ink_primary));
        applySearchHighlights();
        revealCurrentMatch();
    }

    private void stepSearch(boolean backwards) {
        if (searchResult.isEmpty()) {
            if (!editSearchQuery.getText().toString().isEmpty()) {
                Toast.makeText(this, "没有找到「" + editSearchQuery.getText() + "」", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        searchIndex = backwards
                ? TextSearch.previousIndex(searchIndex, searchResult.count())
                : TextSearch.nextIndex(searchIndex, searchResult.count());
        txtSearchCounter.setText(TextSearch.counter(searchIndex, searchResult.count()));
        applySearchHighlights();
        revealCurrentMatch();
    }

    /** Highlight every match, with the current one emphasised. */
    /** Signature of the last painted match set, so an unchanged set is not repainted. */
    private String lastHighlightSignature;

    private void applySearchHighlights() {
        Editable editable = editCode.getText();
        StringBuilder signature = new StringBuilder();
        signature.append(searchIndex).append('|');
        for (TextSearch.Match match : searchResult.matches) {
            signature.append(match.start).append(',').append(match.end).append(';');
        }
        String signatureText = signature.toString();
        if (signatureText.equals(lastHighlightSignature)) return;
        lastHighlightSignature = signatureText;

        android.text.style.BackgroundColorSpan[] spans =
                editable.getSpans(0, editable.length(), android.text.style.BackgroundColorSpan.class);
        for (android.text.style.BackgroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
        if (searchResult.isEmpty()) return;
        int all = isDark() ? 0x4D7FA5FF : 0x332456C6;
        int current = isDark() ? 0x99F2C078 : 0x99FFD98A;
        for (int i = 0; i < searchResult.matches.size(); i++) {
            TextSearch.Match match = searchResult.matches.get(i);
            editable.setSpan(new android.text.style.BackgroundColorSpan(i == searchIndex ? current : all),
                    match.start, match.end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Scroll the editor so the current match is visible.
     *
     * <p>Deliberately does <b>not</b> call {@code editCode.requestFocus()}. That one call was a
     * data-corruption bug: focusing the code editor moved the IME with it, so the next keystroke
     * meant for the query box was typed into the document instead — "只能输入一个字母就退出输入状态，
     * 而且退输入状态会直接修改 svg 代码". The caret still moves to the match (so the position is
     * visible and the user can jump there deliberately), but focus stays where the user put it.</p>
     */
    private void revealCurrentMatch() {
        if (searchIndex < 0 || searchIndex >= searchResult.count()) return;
        final TextSearch.Match match = searchResult.matches.get(searchIndex);
        editCode.setSelection(match.start, match.end);
        // Let the layout settle before asking for the match's position on screen.
        editCode.post(() -> {
            android.text.Layout layout = editCode.getLayout();
            if (layout == null) return;
            int line = layout.getLineForOffset(match.start);
            int y = layout.getLineTop(line);
            scrollCode.smoothScrollTo(0, Math.max(0, y - scrollCode.getHeight() / 3));
        });
    }

    // ------------------------------------------------------------------ editor helpers

    private void setupEditorHelpers() {
        LinearLayout row = findViewById(R.id.layout_editor_helpers);
        row.removeAllViews();
        for (String label : new String[]{"查找", "更多", "Tab", "<", ">", "/", "=", "\"\"", "{}", "()", "[]"}) {
            Button b = new Button(this);
            b.setText(label);
            b.setTextSize(12);
            b.setTextColor(getResources().getColor(label.equals("查找")
                    ? R.color.canvas_primary : R.color.canvas_ink_primary));
            b.setBackgroundResource(R.drawable.bg_helper_key);
            b.setStateListAnimator(null);
            b.setAllCaps(false);
            b.setElevation(0f);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            // A key needs a finger-sized width and a little air on each side; the old 48dp keys sat
            // flush against each other and read as one solid bar.
            float density = getResources().getDisplayMetrics().density;
            int width = label.length() > 4 ? 84 : label.equals("更多") ? 62 : 46;
            b.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    (int) (width * density), -1);
            params.setMarginEnd((int) (4 * density));
            row.addView(b, params);
            b.setOnClickListener(v -> {
                if (label.equals("查找")) {
                    toggleSearch();
                } else if (label.equals("更多")) {
                    showMore();
                } else {
                    try {
                        com.nous.codecanvas.editor.EditorCommands.Result r =
                                com.nous.codecanvas.editor.EditorCommands.insert(
                                        editCode.getText().toString(),
                                        editCode.getSelectionStart(), editCode.getSelectionEnd(), label);
                        discreteEdit = true;
                        editCode.setText(r.text);
                        editCode.setSelection(r.cursor);
                    } catch (Exception e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void showMore() {
        List<CanvasBottomSheet.Action> actions = new ArrayList<>();
        actions.add(new CanvasBottomSheet.Action(0, R.drawable.ic_select_all, "全选"));
        actions.add(new CanvasBottomSheet.Action(1, R.drawable.ic_copy, "复制全部代码"));
        actions.add(new CanvasBottomSheet.Action(2, R.drawable.ic_find, "跳转到行"));
        actions.add(new CanvasBottomSheet.Action(3, R.drawable.ic_undo, "放弃修改，回到上次保存"));
        if (kind == DocumentKind.JSON) {
            actions.add(new CanvasBottomSheet.Action(4, R.drawable.ic_edit, "格式化 JSON"));
        }
        if (currentDocument.getSourceUri() != null) {
            actions.add(new CanvasBottomSheet.Action(5, R.drawable.ic_export, "用其他应用打开原文件"));
        }
        activeSheet = CanvasBottomSheet.show(this, "编辑工具", null,
                actions.toArray(new CanvasBottomSheet.Action[0]), id -> {
                    if (id == 0) {
                        editCode.requestFocus();
                        editCode.selectAll();
                    } else if (id == 1) {
                        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                                .setPrimaryClip(ClipData.newPlainText("代码", editCode.getText().toString()));
                        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                    } else if (id == 2) {
                        activeDialog = UiDialogHelper.createThemedInputDialog(this, "跳转到行", "输入行号",
                                "", android.text.InputType.TYPE_CLASS_NUMBER, "跳转", false, false, raw -> {
                                    try {
                                        editCode.setSelection(com.nous.codecanvas.editor.EditorCommands.lineOffset(
                                                editCode.getText().toString(), Integer.parseInt(raw)));
                                        editCode.requestFocus();
                                    } catch (Exception ex) {
                                        Toast.makeText(this, "请输入有效行号", Toast.LENGTH_SHORT).show();
                                    }
                                });
                        activeDialog.show();
                    } else if (id == 3) {
                        confirmDiscard();
                    } else if (id == 4) {
                        formatJsonInPlace();
                    } else if (id == 5) {
                        openOriginalExternally();
                    }
                });
    }

    /** Format JSON in the editor, refusing when the text is invalid so nothing is destroyed. */
    private void formatJsonInPlace() {
        com.nous.codecanvas.util.StructuredText.JsonResult result =
                com.nous.codecanvas.util.StructuredText.formatJson(editCode.getText().toString());
        if (!result.valid) {
            Toast.makeText(this, "JSON 格式错误，未改动：" + result.errorLabel(), Toast.LENGTH_LONG).show();
            return;
        }
        int start = editCode.getSelectionStart();
        discreteEdit = true;
        editCode.setText(result.formatted);
        editCode.setSelection(Math.min(start, editCode.length()));
        Toast.makeText(this, "已格式化（数字与字符串保持原样），可撤销", Toast.LENGTH_SHORT).show();
    }

    private void confirmDiscard() {
        if (!dirty) {
            Toast.makeText(this, "当前没有未保存的修改", Toast.LENGTH_SHORT).show();
            return;
        }
        activeDialog = CanvasConfirmDialog.show(this, "放弃修改？",
                "会回到上次保存的内容，这次编辑将丢失。", "放弃修改", "继续编辑", true,
                () -> {
                    isProgrammaticChange = true;
                    editCode.setText(savedContent);
                    isProgrammaticChange = false;
                    undoRedoManager.resetBaseline(savedContent, 0);
                    markDirty(false);
                    updateStats(savedContent);
                    applySyntaxHighlight();
                    refreshLineNumbers(1);
                    txtSaveIndicator.setText("已回到上次保存");
                    if (viewFlipper.getDisplayedChild() == 1) {
                        previewState.invalidate();
                        renderPreview(true);
                    }
                });
    }

    private void openOriginalExternally() {
        String uri = currentDocument.getSourceUri();
        if (uri == null) return;
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, "没有能打开这个文件的应用", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ document binding

    /**
     * Above this the code tab refuses to load the document into the EditText.
     *
     * <p>Measured, not guessed: a 535KB SVG put the editor into a 60-second ANR, because a single
     * {@code EditText} lays out, measures and span-scans its whole buffer. The preview is unaffected
     * (it is a WebView), so the honest behaviour is to show the preview and say the source is too
     * large to edit here — not to freeze.</p>
     */
    private static final int MAX_EDITABLE_CHARS = 200_000;

    private boolean sourceTooLarge;

    private void bindDocumentData() {
        txtEditorTitle.setText(currentDocument.getTitle());
        String body = currentDocument.getContent();
        if (body.length() > MAX_EDITABLE_CHARS) {
            sourceTooLarge = true;
            // Do not put the text into the EditText at all: that is the 60-second ANR. The preview
            // renders from the document, so the file is still viewable, saveable and shareable.
            // The banner itself is shown after applyKindToChrome(), which resets the chrome.
            return;
        }
        isProgrammaticChange = true;
        editCode.setText(currentDocument.getContent() != null ? currentDocument.getContent() : "");
        refreshLineNumbers(1);
        isProgrammaticChange = false;

        undoRedoManager.resetBaseline(currentDocument.getContent(), 0);
        updateUndoRedoButtons();
        updateStats(currentDocument.getContent());
        markDirty(false);
        // Highlighting waits for the first layout: the visible window is not known before it, and a
        // whole-document pass on open is what made a large file feel like it hung.
        editCode.post(this::applySyntaxHighlight);
    }

    /**
     * Text the preview, share and export must use. A file too large for the EditText still lives
     * on the document; reading the empty editor used to preview and share a blank file.
     */
    private String editorTextOrStored() {
        if (currentDocument == null) return "";
        if (sourceTooLarge || editCode == null) {
            String stored = currentDocument.getContent();
            return stored != null ? stored : "";
        }
        return editCode.getText().toString();
    }

    /**
     * Replace the code tab with an explanation. The preview, save, share and export all still work;
     * only the in-place text editor is unavailable, and that is stated plainly.
     */
    private void showSourceTooLarge(int chars) {
        View helpers = findViewById(R.id.editor_helpers);
        if (helpers != null) helpers.setVisibility(View.GONE);
        View actions = findViewById(R.id.toolbar_editor_actions);
        if (actions != null) actions.setVisibility(View.GONE);
        TextView advice = findViewById(R.id.txt_preview_advice);
        if (advice != null) {
            advice.setText("这个文件有 " + chars + " 个字符，超过编辑器能流畅处理的上限（"
                    + MAX_EDITABLE_CHARS + "）。预览、保存、分享和另存为都正常；"
                    + "需要改源码时请用「更多 → 用其他应用打开原文件」。");
            advice.setVisibility(View.VISIBLE);
        }
        if (tabCode != null) tabCode.setEnabled(false);
        if (viewFlipper != null) viewFlipper.setDisplayedChild(1);
    }

    /** Show or hide the unsaved dot; also decides whether leaving needs a prompt. */
    private void markDirty(boolean value) {
        dirty = value;
        if (txtUnsavedDot != null) txtUnsavedDot.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void updateStats(String text) {
        int charCount = text.length();
        // Counting newlines avoids String.split, which allocates an array of every line — on an
        // 80KB document that is tens of thousands of objects per keystroke.
        int lineCount = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lineCount++;
        }
        txtEditorStats.setText(lineCount + " 行 · " + charCount + " 字符");
    }

    private void updateUndoRedoButtons() {
        btnUndo.setEnabled(undoRedoManager.canUndo());
        btnUndo.setAlpha(undoRedoManager.canUndo() ? 1.0f : 0.60f);
        btnRedo.setEnabled(undoRedoManager.canRedo());
        btnRedo.setAlpha(undoRedoManager.canRedo() ? 1.0f : 0.60f);
    }

    private void performUndo() {
        commitPendingUndo();
        UndoRedoManager.EditSnapshot snapshot = undoRedoManager.undo();
        if (snapshot != null) {
            applyUndoSnapshot(snapshot);
            Toast.makeText(this, "已撤销", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有可撤销的修改", Toast.LENGTH_SHORT).show();
        }
    }

    /** Fold any pending typing into the history immediately, before an undo/redo acts. */
    /**
     * Fold the typing burst in progress into the history.
     *
     * <p>Uses the text captured when the burst <b>started</b>, never the live buffer: by the time a
     * follow-up action (a paste, a symbol insert) asks for the commit, the buffer already holds that
     * action's result, and pushing it would record the wrong state — which is how a paste ended up
     * being impossible to undo.</p>
     */
    private void commitPendingUndo() {
        handler.removeCallbacks(undoCommitRunnable);
        if (pendingUndoCursor >= 0 && pendingUndoText != null) {
            undoRedoManager.pushState(pendingUndoText, pendingUndoCursor);
            // Then record where the burst ended, so the history always ends on the visible text.
            if (editCode != null) {
                undoRedoManager.pushState(editCode.getText().toString(), editCode.getSelectionStart());
            }
        }
        pendingUndoCursor = -1;
        pendingUndoText = null;
        updateUndoRedoButtons();
    }

    /**
     * Put a history snapshot back on screen.
     *
     * <p>{@code isProgrammaticChange} suppresses the text watcher's own bookkeeping, and
     * {@code isUndoRedoAction} stops the snapshot from being pushed onto the history again — the
     * old code set the second flag but not the first, so every undo both re-pushed the state and
     * ran the full syntax-highlight pass.</p>
     */
    private void applyUndoSnapshot(UndoRedoManager.EditSnapshot snapshot) {
        isProgrammaticChange = true;
        isUndoRedoAction = true;
        editCode.setText(snapshot.text);
        int caret = Math.max(0, Math.min(snapshot.cursorPosition, snapshot.text.length()));
        editCode.setSelection(caret);
        isUndoRedoAction = false;
        isProgrammaticChange = false;

        // The document really did change, so the unsaved marker and the counters must follow.
        editRevision++;
        markDirty(!snapshot.text.equals(savedContent));
        updateStats(snapshot.text);
        refreshLineNumbers(com.nous.codecanvas.editor.LineNumberGutter.lineOf(snapshot.text, caret));
        updateUndoRedoButtons();
        applySyntaxHighlight();
        if (searchBar.getVisibility() == View.VISIBLE) refreshSearch(false);
        if (viewFlipper.getDisplayedChild() == 1) {
            handler.removeCallbacks(previewRefreshRunnable);
            handler.postDelayed(previewRefreshRunnable, 300);
        }
    }

    private void performRedo() {
        commitPendingUndo();
        UndoRedoManager.EditSnapshot snapshot = undoRedoManager.redo();
        if (snapshot != null) {
            applyUndoSnapshot(snapshot);
            Toast.makeText(this, "已重做", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有可重做的修改", Toast.LENGTH_SHORT).show();
        }
    }

    private void performPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence rawText = clip.getItemAt(0).coerceToText(this);
        if (rawText == null) return;
        if (rawText.length() > 2 * 1024 * 1024) {
            Toast.makeText(this, "剪贴板代码过大 (超过 2MB 限制)", Toast.LENGTH_LONG).show();
            return;
        }
        final String rawString = rawText.toString();
        List<com.nous.codecanvas.util.AiCodeExtractor.CodeBlock> blocks =
                com.nous.codecanvas.util.AiCodeExtractor.extract(rawString);
        if (blocks.size() > 1) {
            showMultiBlockPicker(blocks, rawString);
        } else {
            promptAppendOrReplace(blocks.isEmpty() ? rawString : blocks.get(0).code);
        }
    }

    private void showMultiBlockPicker(final List<com.nous.codecanvas.util.AiCodeExtractor.CodeBlock> blocks,
                                      final String originalRaw) {
        CanvasBottomSheet.Action[] actions = new CanvasBottomSheet.Action[blocks.size() + 1];
        for (int i = 0; i < blocks.size(); i++) {
            com.nous.codecanvas.util.AiCodeExtractor.CodeBlock b = blocks.get(i);
            String label = b.language.isEmpty() ? "代码块 " + (i + 1)
                    : b.language.toUpperCase(Locale.ROOT) + " 块 " + (i + 1);
            String snippet = b.code.length() > 34
                    ? b.code.substring(0, 34).replace("\n", " ") + "…" : b.code.replace("\n", " ");
            actions[i] = new CanvasBottomSheet.Action(i, R.drawable.ic_paste, label, snippet, false);
        }
        actions[blocks.size()] = new CanvasBottomSheet.Action(blocks.size(), R.drawable.ic_copy,
                "完整原始剪贴板文本", originalRaw.length() + " 字符", false);

        activeSheet = CanvasBottomSheet.show(this, "检测到 " + blocks.size() + " 段代码块",
                "选择一段插入编辑器", actions, id -> {
                    if (id < blocks.size()) {
                        promptAppendOrReplace(blocks.get(id).code);
                    } else {
                        promptAppendOrReplace(originalRaw);
                    }
                });
    }

    private void promptAppendOrReplace(final String text) {
        String currentContent = editCode.getText().toString();
        if (currentContent.trim().isEmpty()) {
            insertTextIntoEditor(text, true);
            return;
        }
        int start = editCode.getSelectionStart();
        int end = editCode.getSelectionEnd();
        if (start != end && start >= 0 && end >= 0) {
            insertTextIntoEditor(text, false);
            return;
        }
        CanvasBottomSheet.Action[] actions = new CanvasBottomSheet.Action[]{
                new CanvasBottomSheet.Action(0, R.drawable.ic_paste, "追加到末尾"),
                new CanvasBottomSheet.Action(1, R.drawable.ic_edit, "覆盖替换", "可撤销", true),
        };
        activeSheet = CanvasBottomSheet.show(this, "粘贴方式", "当前画布已有代码", actions, id -> {
            if (id == 0) {
                editCode.setSelection(editCode.length());
                insertTextIntoEditor("\n\n" + text, false);
                editCode.setSelection(editCode.length());
            } else {
                insertTextIntoEditor(text, true);
            }
        });
    }

    private void insertTextIntoEditor(String text, boolean replaceAll) {
        discreteEdit = true;
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

    /**
     * Repaint the gutter. Numbers must always describe the code beside them, so this is called
     * from the text watcher and from the cursor callback rather than on a timer.
     */
    /** The caret's line, taken from the layout when it exists (O(1) instead of a buffer walk). */
    private int currentCursorLine() {
        android.text.Layout layout = editCode.getLayout();
        int caret = Math.min(editCode.getSelectionStart(), editCode.length());
        if (layout != null && caret >= 0) {
            return layout.getLineForOffset(caret) + 1;
        }
        return com.nous.codecanvas.editor.LineNumberGutter.lineOf(editCode.getText(), Math.max(0, caret));
    }

    /** Last gutter state, so an unchanged gutter is not rebuilt on every keystroke. */
    private int gutterLines = -1;
    private int gutterCurrentLine = -1;

    private void refreshLineNumbers(int currentLine) {
        if (txtLineNumbers == null || editCode == null) return;
        // The Layout already knows the line count; walking the buffer again per keystroke is what
        // made typing feel heavy on a large file.
        android.text.Layout layout = editCode.getLayout();
        int lines = layout != null
                ? Math.min(layout.getLineCount(), com.nous.codecanvas.editor.LineNumberGutter.MAX_LINES)
                : com.nous.codecanvas.editor.LineNumberGutter.lineCount(editCode.getText());
        if (lines == gutterLines && currentLine == gutterCurrentLine) return;
        gutterLines = lines;
        gutterCurrentLine = currentLine;
        int active = getResources().getColor(R.color.canvas_primary);
        int normal = getResources().getColor(R.color.canvas_line_number);
        txtLineNumbers.setText(com.nous.codecanvas.editor.LineNumberGutter.highlighted(lines, currentLine, normal, active));
    }

    /**
     * Repaint syntax colours.
     *
     * <p>Two things made this the main source of typing lag on a large SVG:</p>
     * <ul>
     *   <li>It ran four whole-document regex passes. On a 300KB drawing that is tens of thousands
     *       of matches, each one a span object, on the UI thread after every keystroke.</li>
     *   <li>It then reset the selection, which forces the editor to re-lay-out from the caret.</li>
     * </ul>
     * <p>So: only the visible window is scanned, and only markup gets coloured. Plain code and
     * text documents skip it entirely — the old behaviour there was to paint XML tag colours over
     * Python or YAML, which was both wrong and expensive.</p>
     */
    private void applySyntaxHighlight() {
        int start;
        int end;
        int[] window = visibleTextWindow();
        // A short file is coloured whole, even before the scroll view has a height. Waiting on
        // that height used to skip the first paint, so keywords stayed the body colour.
        if (editCode.length() > 0 && editCode.length() <= 8000) {
            start = 0;
            end = editCode.length();
        } else if (window != null) {
            start = window[0];
            end = window[1];
        } else {
            if (highlightRetries++ < 4) editCode.post(highlightRunnable);
            return;
        }
        highlightRetries = 0;
        if (isMarkupKind()) {
            SyntaxHighlighter.highlightRange(editCode.getText(), isDark(), start, end);
        } else if (isCodeHighlightKind()) {
            String title = currentDocument == null ? "" : currentDocument.getTitle();
            SyntaxHighlighter.highlightCode(editCode.getText(), isDark(), start, end,
                    SyntaxHighlighter.languageForTitle(title));
        } else {
            clearSyntaxSpans();
        }
    }

    /** The script switch is an HTML concern. Markdown and Mermaid run the bundled renderer. */
    private boolean previewJavaScriptEnabled() {
        return isAllowJs || kind == DocumentKind.MARKDOWN || kind == DocumentKind.MERMAID
                || kind == DocumentKind.JSON || kind == DocumentKind.JSONL
                || kind == DocumentKind.CSV;
    }

    private void showEditorOverflow(View anchor) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
        boolean preview = viewFlipper != null && viewFlipper.getDisplayedChild() == 1;
        if (kind != null && kind.hasSourceTab()) {
            menu.getMenu().add(0, 1, 0, "查找");
            menu.getMenu().add(0, 7, 0, "查找与替换");
        }
        menu.getMenu().add(0, 2, 0, "另存为");
        menu.getMenu().add(0, 3, 0, "分享");
        if (preview) {
            menu.getMenu().add(0, 4, 0, "刷新");
            menu.getMenu().add(0, 6, 0, "全屏");
            if (kind == DocumentKind.HTML) menu.getMenu().add(0, 5, 0, "联网加载");
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) toggleSearch();
            else if (item.getItemId() == 7) showFindReplace();
            else if (item.getItemId() == 2) startSafExport();
            else if (item.getItemId() == 3) shareFile();
            else if (item.getItemId() == 4) findViewById(R.id.btn_preview_refresh).performClick();
            else if (item.getItemId() == 5) findViewById(R.id.btn_preview_network).performClick();
            else if (item.getItemId() == 6) findViewById(R.id.btn_preview_fullscreen).performClick();
            return true;
        });
        menu.show();
    }

    /** Code, data and config files get keyword colour. That is not an interpreter. */
    private boolean isCodeHighlightKind() {
        return kind == DocumentKind.CODE || kind == DocumentKind.JSON
                || kind == DocumentKind.JSONL || kind == DocumentKind.YAML
                || kind == DocumentKind.CSV || kind == DocumentKind.TEXT;
    }

    private boolean isMarkupKind() {
        return kind == DocumentKind.HTML || kind == DocumentKind.SVG
                || kind == DocumentKind.XML || kind == DocumentKind.MARKDOWN;
    }

    private void clearSyntaxSpans() {
        android.text.Editable editable = editCode.getText();
        android.text.style.ForegroundColorSpan[] spans = editable.getSpans(
                0, editable.length(), android.text.style.ForegroundColorSpan.class);
        if (spans.length == 0) return;
        for (android.text.style.ForegroundColorSpan span : spans) editable.removeSpan(span);
    }

    /**
     * The character range actually on screen, with a screenful of slack on each side so a small
     * scroll does not need a repaint. Returns null when the layout is not ready yet.
     */
    private int[] visibleTextWindow() {
        android.text.Layout layout = editCode.getLayout();
        if (layout == null) return null;
        int scrollY = scrollCode.getScrollY();
        int height = scrollCode.getHeight();
        if (height <= 0) return null;
        int firstLine = Math.max(0, layout.getLineForVertical(Math.max(0, scrollY - height)));
        int lastLine = Math.min(layout.getLineCount() - 1,
                layout.getLineForVertical(scrollY + height * 2));
        int start = layout.getLineStart(firstLine);
        int end = layout.getLineEnd(lastLine);
        return new int[]{start, end};
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    // ------------------------------------------------------------------ chrome per file type

    /**
     * A file's type decides which controls even exist. An image has no code tab, no search and no
     * symbol strip; a PDF has no editor either. Showing them and then refusing the action is the
     * kind of dead UI that reads as a bug.
     */
    private void applyKindToChrome() {
        boolean hasSource = kind.hasSourceTab() && !sourceTooLarge;
        layoutEditorTabs.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        btnSearch.setVisibility(View.GONE);
        if (btnExportSaf != null) btnExportSaf.setVisibility(View.GONE);
        if (btnShare != null) btnShare.setVisibility(View.GONE);
        btnSave.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        editorHelpersView.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        if (!hasSource) {
            viewFlipper.setDisplayedChild(1);
            tabCode.setVisibility(View.GONE);
            tabPreview.setVisibility(View.GONE);
        }
        // "Fit to content" is an SVG-only idea.
        btnViewportFitContent.setVisibility(kind == DocumentKind.SVG ? View.VISIBLE : View.GONE);
        boolean zoomable = kind == DocumentKind.SVG || kind == DocumentKind.MERMAID
                || isImageKind() || kind == DocumentKind.PDF;
        viewportToolbar.setVisibility(zoomable ? View.VISIBLE : View.GONE);
        if (previewViewport != null) {
            if (isImageKind() || kind == DocumentKind.PDF) {
                previewViewport.setViewTransformTarget(previewImage);
                previewViewport.setUseViewTransform(true);
            } else {
                previewViewport.setUseViewTransform(false);
            }
        }
        // Only content that must not scroll hands its touches to the viewport. A Markdown page or
        // a CSV table has to keep scrolling normally, so the viewport stays passive there.
        previewViewport.setInteractive(zoomable);
        // The script switch is meaningless for a raster image or a PDF.
        switchAllowJs.setVisibility(kind == DocumentKind.HTML ? View.VISIBLE : View.GONE);
        if (pdfPager != null) pdfPager.setVisibility(kind == DocumentKind.PDF ? View.VISIBLE : View.GONE);
        applyEditorChrome();
    }

    // ------------------------------------------------------------------ preview

    private void renderPreview(boolean force) {
        // The document's title is the source of truth for its type: a rename (or a caller that
        // changes the title directly) must change how the content is rendered, not just its label.
        DocumentKind current = currentDocument.kind();
        if (current != kind) {
            kind = current;
            applyKindToChrome();
        }
        String content = editorTextOrStored();
        String title = currentDocument.getTitle();

        // A .html file whose body is a single inline <svg> is artwork, not a web page. DocumentKind
        // can only see the extension, so the content decides here. Without this the drawing went
        // through HTML preview, which has no vector viewport: it was cropped by its own
        // preserveAspectRatio="slice" and could not be zoomed ("放进代码画布里就是无法缩放，显示不全").
        if (kind == DocumentKind.HTML
                && com.nous.codecanvas.util.RenderKindDetector.detect(title, content)
                        == com.nous.codecanvas.util.RenderKind.SVG) {
            kind = DocumentKind.SVG;
            applyKindToChrome();
        }
        if (!sourceTooLarge) {
            ((TextView) findViewById(R.id.txt_preview_advice)).setVisibility(View.GONE);
        }

        if (isImageKind()) {
            renderImage();
            return;
        }
        if (kind == DocumentKind.PDF) {
            renderPdf();
            return;
        }

        layoutXmlView.setVisibility(View.GONE);
        previewImage.setVisibility(View.GONE);
        if (webPreview != null) webPreview.setVisibility(View.VISIBLE);

        String fingerprint = PreviewKey.forDocument(currentDocument.getId(), content, isDark())
                + kind.name() + isAllowJs + isAllowNetwork + svgViewBoxOverride;
        if (!force && content.equals(lastRenderedContent)
                && java.util.Objects.equals(svgViewBoxOverride, lastRenderedViewBox)) {
            return;
        }
        lastRenderedContent = content;
        lastRenderedViewBox = svgViewBoxOverride;

        // Only markup can have "external resource" problems; scanning a 300KB YAML or code file with
        // five regexes on every preview refresh was pure cost.
        java.util.List<String> warnings = (!sourceTooLarge && (kind == DocumentKind.HTML || kind == DocumentKind.SVG
                || kind == DocumentKind.MARKDOWN))
                ? com.nous.codecanvas.util.RenderingAdvisor.inspect(content)
                : java.util.Collections.emptyList();
        if (!warnings.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String w : warnings) sb.append("• ").append(w).append("\n");
            TextView advice = findViewById(R.id.txt_preview_advice);
            advice.setText(sb.toString().trim());
            advice.setVisibility(View.VISIBLE);
        }

        txtRenderModeBadge.setText(badgeFor(kind));
        PreviewPage.Request request = new PreviewPage.Request();
        request.kind = kind.name();
        request.content = content;
        request.fileName = title;
        request.dark = isDark();
        request.allowScripts = isAllowJs;
        request.allowNetwork = isAllowNetwork;
        request.svgViewBoxOverride = svgViewBoxOverride;
        loadPreview(PreviewPage.build(request), fingerprint);

        // A fresh render starts fitted: a new drawing must not land outside an old viewport.
        if (isVectorKind()) previewViewport.fitToWindow();
    }

    /** Images are decoded from the original bytes, so nothing is re-encoded on the way in. */
    private void renderImage() {
        if (webPreview != null) webPreview.setVisibility(View.GONE);
        layoutXmlView.setVisibility(View.GONE);
        findViewById(R.id.preview_loading).setVisibility(View.GONE);
        previewState.invalidate();
        previewImage.setVisibility(View.VISIBLE);
        txtRenderModeBadge.setText(kind == DocumentKind.GIF ? "动图预览" : "图片预览");
        if (previewViewport != null) {
            previewViewport.setViewTransformTarget(previewImage);
            previewViewport.setUseViewTransform(true);
        }

        File blob = FileImporter.blobFile(this, currentDocument.getId());
        if (!blob.isFile()) {
            previewImage.setImageDrawable(null);
            Toast.makeText(this, "原始图片不在本地缓存里了，可以重新导入或用其他应用打开", Toast.LENGTH_LONG).show();
            return;
        }
        if (kind == DocumentKind.GIF && showAnimatedGif(blob)) {
            previewViewport.fitToWindow();
            return;
        }
        // Large images are decoded downsampled: a 4000px photo at full size would exhaust the heap
        // long before it could be displayed on a phone screen.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(blob.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(blob.getAbsolutePath(), options);
        if (bitmap == null) {
            Toast.makeText(this, "图片无法解码，文件可能已损坏。原始字节仍然保留。", Toast.LENGTH_LONG).show();
            return;
        }
        previewImage.setImageBitmap(bitmap);
        previewViewport.fitToWindow();
    }

    /** Keep a GIF playing. A still bitmap would freeze on the first frame. */
    private boolean showAnimatedGif(File blob) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source source = ImageDecoder.createSource(blob);
                Drawable drawable = ImageDecoder.decodeDrawable(source);
                previewImage.setImageDrawable(drawable);
                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                }
                return true;
            }
            Movie movie = Movie.decodeFile(blob.getAbsolutePath());
            if (movie == null) return false;
            previewImage.setImageDrawable(new LoopingGifDrawable(movie));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Power-of-two downsample that keeps a decoded image under roughly 12 megapixels. */
    static int sampleSizeFor(int width, int height) {
        if (width <= 0 || height <= 0) return 1;
        int sample = 1;
        long pixels = (long) width * height;
        while (pixels / ((long) sample * sample) > 12_000_000L) {
            sample *= 2;
        }
        return sample;
    }

    private void renderPdf() {
        if (webPreview != null) webPreview.setVisibility(View.GONE);
        layoutXmlView.setVisibility(View.GONE);
        findViewById(R.id.preview_loading).setVisibility(View.GONE);
        previewState.invalidate();
        previewImage.setVisibility(View.VISIBLE);
        if (pdfPager != null) pdfPager.setVisibility(View.VISIBLE);
        if (previewViewport != null) {
            previewViewport.setViewTransformTarget(previewImage);
            previewViewport.setUseViewTransform(true);
        }
        txtRenderModeBadge.setText("PDF 阅读");
        TextView advice = findViewById(R.id.txt_preview_advice);
        if (advice != null) advice.setVisibility(View.GONE);
        showPdfPage(pdfPageIndex);
    }

    private void showPdfPage(int index) {
        if (currentDocument == null) return;
        File blob = FileImporter.blobFile(this, currentDocument.getId());
        int target = Math.max(480, getResources().getDisplayMetrics().widthPixels);
        final int requested = index;
        new Thread(() -> {
            try {
                PdfPageRenderer.Page page = PdfPageRenderer.render(blob, requested, target);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || kind != DocumentKind.PDF) return;
                    pdfPageIndex = page.index;
                    pdfPageCount = page.count;
                    previewImage.setImageBitmap(page.bitmap);
                    if (txtPdfPage != null) {
                        txtPdfPage.setText((page.index + 1) + " / " + page.count);
                    }
                    if (previewViewport != null) previewViewport.fitToWindow();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TextView note = findViewById(R.id.txt_preview_advice);
                    if (note != null) {
                        note.setText("这份 PDF 打不开：" + describe(e)
                                + "。原始字节仍在，可以用其他应用打开或分享。");
                        note.setVisibility(View.VISIBLE);
                    }
                });
            }
        }, "canvas-pdf").start();
    }

    private static String badgeFor(DocumentKind kind) {
        switch (kind) {
            case HTML: return "HTML5 渲染";
            case SVG: return "SVG 矢量图";
            case MARKDOWN: return "Markdown 渲染";
            case MERMAID: return "Mermaid 图表";
            case JSON: return "JSON 结构";
            case JSONL: return "JSONL 逐行";
            case CSV: return "CSV 表格";
            case YAML: return "YAML 结构";
            case XML: return "XML 结构";
            case CODE: return "代码预览";
            default: return "纯文本视图";
        }
    }

    private boolean supportsThumbnail() {
        return kind == DocumentKind.HTML || kind == DocumentKind.SVG
                || kind == DocumentKind.MARKDOWN || kind == DocumentKind.MERMAID;
    }

    private void setNetworkAllowed(boolean allowed) {
        isAllowNetwork = allowed;
        if (webPreview != null) webPreview.getSettings().setBlockNetworkLoads(!allowed);
        ((Button) findViewById(R.id.btn_preview_network)).setText(allowed ? "切回离线" : "联网加载");
        previewState.invalidate();
        renderPreview(true);
    }

    // ------------------------------------------------------------------ fullscreen / tabs

    private void setFullscreen(boolean fullscreen) {
        isFullscreen = fullscreen;
        if (fullscreen) {
            if (headerEditor != null) headerEditor.setVisibility(View.GONE);
            if (layoutEditorTabs != null) layoutEditorTabs.setVisibility(View.GONE);
            if (toolbarPreviewControls != null) toolbarPreviewControls.setVisibility(View.GONE);
            if (editorHelpersView != null) editorHelpersView.setVisibility(View.GONE);
            View advice = findViewById(R.id.txt_preview_advice);
            if (advice != null) advice.setVisibility(View.GONE);
            if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.VISIBLE);
            if (containerPreviewCanvas != null) containerPreviewCanvas.setPadding(0, 0, 0, 0);
            // Auto rotate to landscape for wider aspect ratios
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            if (headerEditor != null) headerEditor.setVisibility(View.VISIBLE);
            if (layoutEditorTabs != null && kind.hasSourceTab()) layoutEditorTabs.setVisibility(View.VISIBLE);
            if (toolbarPreviewControls != null) toolbarPreviewControls.setVisibility(View.VISIBLE);
            if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.GONE);
            if (containerPreviewCanvas != null) {
                int p12 = (int) (12 * getResources().getDisplayMetrics().density);
                int p14 = (int) (14 * getResources().getDisplayMetrics().density);
                containerPreviewCanvas.setPadding(p12, p12, p12, p14);
            }
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    public void onBackPressed() {
        if (!consumeEditorBack()) super.onBackPressed();
    }

    /** Fullscreen and search close first. Leaving a dirty file still asks. */
    private boolean consumeEditorBack() {
        if (activeSheet != null && activeSheet.isShowing()) {
            activeSheet.dismiss();
            return true;
        }
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
            return true;
        }
        if (isFullscreen) {
            setFullscreen(false);
            return true;
        }
        if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
            return true;
        }
        return false;
    }

    private void onBackInvoked() {
        if (consumeEditorBack()) return;
        if (!dirty) {
            finish();
            return;
        }
        confirmLeave();
    }

    private void confirmLeave() {
        if (!dirty) {
            finish();
            return;
        }
        activeDialog = CanvasConfirmDialog.show(this, "还有未保存的修改",
                "离开前要先保存吗？「放弃」会丢掉这次编辑。", "保存并离开", "放弃修改", true,
                () -> {
                    saveCurrentDocument(true);
                    // The write is queued. Leaving before it runs drops the edit the user just
                    // asked to keep, so wait for that queue and only then close the screen.
                    new Thread(() -> {
                        com.nous.codecanvas.data.DocumentSaveCoordinator.drainAndWait(8000);
                        runOnUiThread(() -> {
                            if (!isFinishing()) finish();
                        });
                    }, "canvas-leave-save").start();
                },
                this::finish);
    }

    private void switchToTab(int tabIndex) {
        if (isFullscreen && tabIndex == 0) setFullscreen(false);
        if (!kind.hasSourceTab()) tabIndex = 1;
        if (sourceTooLarge && tabIndex == 0) {
            Toast.makeText(this, "这个文件太大，编辑器无法流畅处理；预览与导出正常", Toast.LENGTH_LONG).show();
            tabIndex = 1;
        }
        if (viewFlipper.getDisplayedChild() == tabIndex) {
            paintModeTab(tabCode, tabIndex == 0);
            paintModeTab(tabCodeLand, tabIndex == 0);
            paintModeTab(tabPreview, tabIndex == 1);
            paintModeTab(tabPreviewLand, tabIndex == 1);
            applyEditorChrome();
            if (tabIndex == 1) renderPreview(false);
            return;
        }

        if (tabIndex == 0) {
            paintModeTab(tabCode, true);
            paintModeTab(tabPreview, false);
            paintModeTab(tabCodeLand, true);
            paintModeTab(tabPreviewLand, false);

            findViewById(R.id.txt_preview_advice).setVisibility(View.GONE);
            toolbarEditorActions.setVisibility(View.VISIBLE);
            if (kind.hasSourceTab()) editorHelpersView.setVisibility(View.VISIBLE);
            toolbarPreviewControls.setVisibility(View.GONE);
            viewFlipper.setDisplayedChild(0);
        } else {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }

            paintModeTab(tabPreview, true);
            paintModeTab(tabCode, false);
            paintModeTab(tabPreviewLand, true);
            paintModeTab(tabCodeLand, false);

            toolbarEditorActions.setVisibility(View.GONE);
            editorHelpersView.setVisibility(View.GONE);
            toolbarPreviewControls.setVisibility(View.VISIBLE);
            renderPreview(false);
            viewFlipper.setDisplayedChild(1);
        }
        View page = viewFlipper.getCurrentView();
        page.animate().cancel();
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            float dir = tabIndex == 1 ? 1f : -1f;
            page.setAlpha(0.45f);
            page.setTranslationX(dir * 16f * getResources().getDisplayMetrics().density);
            page.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                    .start();
        } else {
            page.setAlpha(1f);
            page.setTranslationX(0f);
        }
        applyEditorChrome();
    }

    private void paintModeTab(TextView tab, boolean selected) {
        if (tab == null) return;
        tab.setSelected(selected);
        tab.setTextColor(getResources().getColor(selected
                ? R.color.canvas_on_primary : R.color.canvas_ink_secondary));
        tab.setBackgroundResource(R.drawable.bg_segment_item);
    }

    // ------------------------------------------------------------------ saving

    /**
     * Write the document.
     *
     * <p>Order of attempts: the original file when the document came from one and the provider
     * allows writing, otherwise the app's own store. The indicator says which happened — "已保存"
     * for a real save, and an explicit message when the original could not be written so the user
     * is not misled about their file.</p>
     *
     * @param explicit true when the user pressed save (shows a toast either way)
     */
    private void saveCurrentDocument(boolean explicit) {
        if (documentLoadFailed || currentDocument == null || editCode == null) return;
        // The source was never loaded into the editor, so there is nothing to write back and
        // saving an empty buffer would destroy the file. Images and PDFs are the same case:
        // the editor buffer is empty, and the bytes live beside the document.
        if (sourceTooLarge || currentDocument.isBlob() || !kind.editable()) return;
        // Opening a file and leaving used to rewrite it and bump the timestamp even when
        // nothing had changed. Only an explicit save, or an edit that is still dirty, writes.
        if (!explicit && !dirty) return;
        final String content = editCode.getText().toString();
        final String title = currentDocument.getTitle();
        final long capturedEdit = editRevision;
        final boolean creating = documentIsNew;

        currentDocument.setContent(content);
        currentDocument.setUpdatedAt(System.currentTimeMillis());
        if (txtSaveIndicator != null) txtSaveIndicator.setText("保存中…");

        // One process-wide writer queue, so two editor screens cannot interleave writes, plus the
        // store's revision check so a save captured before a newer edit can never win the race.
        com.nous.codecanvas.data.DocumentSaveCoordinator.save(
                repository, currentDocument.getId(), title, content, creating ? -1L : persistedRevision,
                new com.nous.codecanvas.data.DocumentSaveCoordinator.Result() {
                    @Override
                    public void onSaved(long storedRevision) {
                        // The app-store write succeeded; now try the real file.
                        final boolean[] sourceWritten = {false};
                        final String[] sourceProblem = {null};
                        if (currentDocument.getSourceUri() != null && !currentDocument.isBlob()) {
                            try {
                                byte[] payload = SourceWriter.payloadBytes(
                                        EditorActivity.this, currentDocument, content);
                                sourceWritten[0] = SourceWriter.saveToSource(
                                        EditorActivity.this, currentDocument, payload);
                                if (!sourceWritten[0]) {
                                    sourceProblem[0] = "原文件没有写入权限，修改只保存在应用内";
                                }
                            } catch (Exception e) {
                                sourceProblem[0] = "写回原文件失败：" + describe(e);
                            }
                        }
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            documentIsNew = false;
                            persistedRevision = storedRevision;
                            currentDocument.setRevision(storedRevision);
                            savedContent = content;
                            // Only claim "saved" when the text on screen is the text that was
                            // written. Claiming it for a superseded snapshot is how the old
                            // indicator ended up lying to the user.
                            if (capturedEdit == editRevision) {
                                markDirty(false);
                                txtSaveIndicator.setText(sourceProblem[0] != null
                                        ? "已存应用内" : "已保存");
                                if (sourceProblem[0] != null && explicit) {
                                    Toast.makeText(EditorActivity.this, sourceProblem[0], Toast.LENGTH_LONG).show();
                                } else if (explicit) {
                                    if (btnSave != null) {
                                        btnSave.performHapticFeedback(
                                                android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                                    }
                                    Toast.makeText(EditorActivity.this,
                                            sourceWritten[0] ? "已保存到原文件" : "已保存",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailed(Exception error) {
                        if (error instanceof com.nous.codecanvas.data.DocumentRepository.StaleWriteException) {
                            // Another screen moved this document forward. What the user is looking at
                            // is still the newest text, so adopt the stored revision and write again —
                            // but only if the document still exists, otherwise we would resurrect a
                            // file the user deleted.
                            runOnUiThread(() -> {
                                CanvasDocument latest = null;
                                try {
                                    latest = repository.findById(currentDocument.getId());
                                } catch (Exception ignored) {
                                }
                                if (latest == null) {
                                    persistFailed("文件已被删除，本次修改没有保存");
                                    return;
                                }
                                String mine = editorTextOrStored();
                                if (mine.equals(latest.getContent())) {
                                    adoptStoredMetadata(latest);
                                    markDirty(false);
                                    if (txtSaveIndicator != null) txtSaveIndicator.setText("已保存");
                                    return;
                                }
                                final CanvasDocument newer = latest.snapshot();
                                activeDialog = CanvasConfirmDialog.show(EditorActivity.this,
                                        "文件刚刚在别处更新了",
                                        "保留正在编辑的内容会覆盖那一版；改用已保存版本会丢掉这次编辑。",
                                        "保留我的修改", "改用已保存版本", false,
                                        () -> {
                                            adoptStoredMetadata(newer);
                                            saveCurrentDocument(true);
                                        },
                                        () -> reloadStoredDocument(newer));
                            });
                            return;
                        }
                        boolean missing = error instanceof com.nous.codecanvas.data.DocumentRepository.DocumentMissingException;
                        runOnUiThread(() -> persistFailed(missing
                                ? "文件已被删除，本次修改没有保存"
                                : "保存失败：" + describe(error)));
                    }
                });
    }

    /** Take the stored identity (revision, charset, kind) without replacing the text on screen. */
    private void adoptStoredMetadata(CanvasDocument latest) {
        if (latest == null || currentDocument == null) return;
        currentDocument.setRevision(latest.getRevision());
        currentDocument.setKindName(latest.getKindName());
        currentDocument.setEncoding(latest.getEncoding());
        currentDocument.setLineEnding(latest.getLineEnding());
        currentDocument.setSourceUri(latest.getSourceUri());
        currentDocument.setMimeType(latest.getMimeType());
        currentDocument.setBlob(latest.isBlob());
        currentDocument.setByteSize(latest.getByteSize());
        currentDocument.setTitle(latest.getTitle());
        persistedRevision = latest.getRevision();
        documentIsNew = false;
        kind = currentDocument.kind();
        if (txtEditorTitle != null) txtEditorTitle.setText(latest.getTitle());
    }

    /** Replace the editor with the copy already stored. */
    private void reloadStoredDocument(CanvasDocument latest) {
        currentDocument = latest.snapshot();
        persistedRevision = currentDocument.getRevision();
        documentIsNew = false;
        kind = currentDocument.kind();
        sourceTooLarge = false;
        bindDocumentData();
        applyKindToChrome();
        if (sourceTooLarge) showSourceTooLarge(currentDocument.getContent().length());
        markDirty(false);
        if (txtSaveIndicator != null) txtSaveIndicator.setText("已改用保存的版本");
        if (viewFlipper != null && viewFlipper.getDisplayedChild() == 1) {
            previewState.invalidate();
            renderPreview(true);
        }
    }

    private void persistFailed(String reason) {
        if (isFinishing() || isDestroyed()) return;
        if (txtSaveIndicator != null) txtSaveIndicator.setText(reason);
        Toast.makeText(EditorActivity.this, reason, Toast.LENGTH_LONG).show();
    }

    private static String describe(Throwable t) {
        if (t == null) return "未知错误";
        String message = t.getMessage();
        if (message == null || message.isEmpty()) return t.getClass().getSimpleName();
        return message.length() > 80 ? message.substring(0, 80) + "…" : message;
    }

    /**
     * Replace the editor with an honest failure state. Deliberately does not fall back to a blank
     * document, and does not save anything: the file on disk is the user's only copy.
     */
    private void showLoadFailure(String message) {
        documentLoadFailed = true;
        handler.removeCallbacks(autoSaveRunnable);
        handler.removeCallbacks(highlightRunnable);
        if (viewFlipper != null) viewFlipper.setVisibility(View.GONE);
        if (toolbarEditorActions != null) toolbarEditorActions.setVisibility(View.GONE);
        if (toolbarPreviewControls != null) toolbarPreviewControls.setVisibility(View.GONE);
        View helpers = findViewById(R.id.editor_helpers);
        if (helpers != null) helpers.setVisibility(View.GONE);
        View advice = findViewById(R.id.txt_preview_advice);
        if (advice != null) advice.setVisibility(View.GONE);
        if (layoutEditorTabs != null) layoutEditorTabs.setVisibility(View.GONE);
        if (txtSaveIndicator != null) txtSaveIndicator.setText("");
        if (txtEditorTitle != null) txtEditorTitle.setText("打不开这个文件");
        if (btnShare != null) btnShare.setEnabled(false);
        if (btnExportSaf != null) btnExportSaf.setEnabled(false);
        if (btnSave != null) btnSave.setEnabled(false);
        if (btnSearch != null) btnSearch.setEnabled(false);
        if (layoutTitleClickable != null) layoutTitleClickable.setOnClickListener(null);

        TextView msg = findViewById(R.id.txt_editor_error_message);
        if (msg != null) msg.setText(message);
        View panel = findViewById(R.id.layout_editor_error);
        if (panel != null) panel.setVisibility(View.VISIBLE);
        View retry = findViewById(R.id.btn_editor_error_retry);
        if (retry != null) retry.setOnClickListener(v -> recreate());
        View back = findViewById(R.id.btn_editor_error_back);
        if (back != null) back.setOnClickListener(v -> finish());
    }

    private void showRenameDialog() {
        if (documentLoadFailed || currentDocument == null) return;
        AlertDialog dialog = UiDialogHelper.createThemedInputDialog(
                this,
                "重命名 (可包含任意后缀)",
                "输入文件名 (如 my_page.html, icon.svg)",
                currentDocument.getTitle(),
                newTitle -> {
                    currentDocument.setTitle(newTitle);
                    if (!currentDocument.isBlob()) {
                        com.nous.codecanvas.data.FileKindDetector.applyTo(currentDocument);
                    }
                    kind = currentDocument.kind();
                    txtEditorTitle.setText(newTitle);
                    applyKindToChrome();
                    saveCurrentDocument(false);
                    Toast.makeText(EditorActivity.this, "已重命名为: " + newTitle, Toast.LENGTH_SHORT).show();
                }
        );
        activeDialog = dialog;
        dialog.show();
    }

    /** "Save as": a copy in a location the user picks. The original is untouched. */
    private void startSafExport() {
        if (documentLoadFailed || currentDocument == null) return;
        saveCurrentDocument(false);
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
            if (uri != null) writeToSafUri(uri);
        }
    }

    private void writeToSafUri(Uri uri) {
        if (currentDocument == null) return;
        final String content = editorTextOrStored();
        new Thread(() -> {
            try {
                byte[] payload = SourceWriter.payloadBytes(this, currentDocument, content);
                SourceWriter.writeToUri(this, uri, payload);
                runOnUiThread(() -> Toast.makeText(EditorActivity.this,
                        "已另存为 " + currentDocument.getTitle(), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(EditorActivity.this,
                        "另存失败: " + describe(e), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void shareFile() {
        if (documentLoadFailed || currentDocument == null) return;
        saveCurrentDocument(false);
        final String docTitle = currentDocument.getTitle();
        final String docContent = editorTextOrStored();
        new Thread(() -> {
            try {
                byte[] payload = SourceWriter.payloadBytes(this, currentDocument, docContent);
                String authority = getPackageName() + CanvasFileProvider.AUTHORITY_SUFFIX;
                com.nous.codecanvas.util.ShareExporter.Staged staged =
                        SourceWriter.stageForShare(this, authority, docTitle, payload);
                runOnUiThread(() -> startActivity(
                        com.nous.codecanvas.util.ShareExporter.chooserIntent(this, staged, "分享")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(EditorActivity.this,
                        "分享失败: " + describe(e), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** Links inside a previewed document open in the system browser, never inside this screen. */
    private void openExternally(String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(view);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的浏览器打开这个链接", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentDocument(false);
    }

    // ------------------------------------------------------------------ test hooks

    public CanvasDocument getCurrentDocumentForTest() {
        return currentDocument;
    }

    public AlertDialog getActiveDialogForTest() {
        return activeDialog;
    }

    public Dialog getActiveSheetForTest() {
        return activeSheet;
    }

    public boolean isDirtyForTest() {
        return dirty;
    }

    public SvgViewport getViewportForTest() {
        return previewViewport;
    }

    public TextSearch.Result getSearchResultForTest() {
        return searchResult;
    }

    public int getSearchIndexForTest() {
        return searchIndex;
    }

    public DocumentKind getKindForTest() {
        return kind;
    }

    @Override
    protected void onDestroy() {
        if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
        if (activeSheet != null && activeSheet.isShowing()) activeSheet.dismiss();
        handler.removeCallbacks(autoSaveRunnable);
        handler.removeCallbacks(highlightRunnable);
        handler.removeCallbacks(previewRefreshRunnable);
        handler.removeCallbacks(previewTimeoutRunnable);
        previewState.invalidate();
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
