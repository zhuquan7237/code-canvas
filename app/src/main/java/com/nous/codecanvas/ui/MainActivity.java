package com.nous.codecanvas.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.AiCodeExtractor;
import com.nous.codecanvas.util.CanvasPrefs;
import com.nous.codecanvas.util.ClipboardCodeOffer;
import com.nous.codecanvas.util.FileUtils;
import com.nous.codecanvas.util.PreviewKey;
import com.nous.codecanvas.util.RenderKind;
import com.nous.codecanvas.util.RenderKindDetector;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final int REQ_IMPORT_SAF = 1001;
    /** Exporting straight from the list, so a card does not have to be opened in the editor first. */
    private static final int REQ_EXPORT_DOCUMENT = 1002;

    /** Crossfade for a thumbnail that had to be generated during this visit. */
    private static final int THUMB_FADE_MS = 180;
    /** Key under which a row records the thumbnail it is currently showing. */
    private static final int TAG_THUMB_BOUND = 0x7f0f0001;

    private final java.util.concurrent.ExecutorService thumbnailExecutor = java.util.concurrent.Executors.newFixedThreadPool(2);
    /** Single worker for the clipboard read, so a slow clip can never stall the UI thread. */
    private final java.util.concurrent.ExecutorService clipboardReadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private DocumentRepository repository;
    private AlertDialog activeDialog;
    private Dialog activeSheet;
    private ListView listDocuments;
    private android.widget.GridView gridDocuments;
    private com.nous.codecanvas.update.AppUpdater updater;
    /** URI of an external open already handled, so a recreation does not import twice. */
    private String handledIncomingUri;
    /** Text shared into the app, consumed the next time the user asks to paste. */
    private String pendingSharedText;
    private View viewEmpty;
    private EditText editSearch;
    private Button btnQuickPaste;
    private ImageButton btnNew;
    private ImageButton btnImport;

    // --- clipboard auto-detect banner -----------------------------------------------------------
    private View bannerClipboard;
    private TextView bannerSnippet;
    /** Content currently offered by the banner; null while the banner is hidden. */
    private String bannerClipContent;
    /** Hash of the last clip the user dismissed or imported, so it is not offered twice. */
    private int lastHandledClipHash;

    // --- multi-select ----------------------------------------------------------------------------
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private boolean selectionMode;
    private View selectionBar;
    private TextView txtSelectionCount;

    // --- catalog drawer ---------------------------------------------------------------------------
    private View drawerRoot;
    private View drawerPanel;
    private ListView drawerList;
    private TextView drawerSubtitle;
    private LinearLayout drawerFilters;
    private DrawerAdapter drawerAdapter;
    /** null = 全部, otherwise a RenderKind name. */
    private String typeFilter;

    private final List<CanvasDocument> allDocuments = new ArrayList<>();
    private final List<CanvasDocument> filteredDocuments = new ArrayList<>();
    private DocumentAdapter adapter;
    private DocumentAdapter gridAdapter;
    private ThumbnailBackfiller backfiller;
    private final java.util.Map<String, DocumentPresentation> presentations = new java.util.HashMap<>();

    /** The appearance mode this Activity was built with; compared in onResume to detect changes. */
    private int appearanceAtCreate = com.nous.codecanvas.util.AppearanceManager.MODE_SYSTEM;

    /** Document awaiting a SAF export destination, set when the list's long-press menu exports. */
    private CanvasDocument pendingExportDocument;

    /** Derived metadata is prepared off-main once per reload, not on every scroll bind. */
    private static final class DocumentPresentation {
        final String previewKey;
        final String sizeText;
        final RenderKind kind;
        DocumentPresentation(CanvasDocument doc, boolean dark) {
            String content = doc.getContent();
            previewKey = PreviewKey.forDocument(doc.getId(), content, dark);
            kind = RenderKindDetector.detect(doc.getTitle(), content);
            int bytes = content.getBytes(StandardCharsets.UTF_8).length;
            String size = bytes < 1024 ? bytes + " B" : String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0f);
            int lines = content.isEmpty() ? 0 : content.split("\r\n|\r|\n", -1).length;
            sizeText = size + " · " + lines + " 行";
        }
    }

    private java.util.Map<String, DocumentPresentation> preparePresentations(List<CanvasDocument> docs, boolean dark) {
        java.util.Map<String, DocumentPresentation> result = new java.util.HashMap<>();
        for (CanvasDocument doc : docs) result.put(doc.getId(), new DocumentPresentation(doc, dark));
        return result;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // Appearance must be decided here, not in onCreate: the framework reads this Activity's
        // resources before onCreate runs, and applyOverrideConfiguration() refuses to work after
        // that ("getResources() or getAssets() has already been called").
        super.attachBaseContext(com.nous.codecanvas.util.AppearanceManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View rootView = findViewById(R.id.root_main);
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
        appearanceAtCreate = com.nous.codecanvas.util.AppearanceManager.getMode(this);
        try {
            repository.seedDefaultTemplatesIfEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }

        initViews();
        setupListeners();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    /**
     * Consume an external open/share. The manifest has always advertised ACTION_VIEW, but nothing
     * read the intent, so picking "代码画布" in a file manager just landed on the home screen.
     */
    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            try {
                getContentResolver().takePersistableUriPermission(data, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            String key = data.toString();
            if (key.equals(handledIncomingUri)) return;
            handledIncomingUri = key;
            importFromUri(data);
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (stream != null) {
                try {
                    getContentResolver().takePersistableUriPermission(stream, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                String key = stream.toString();
                if (!key.equals(handledIncomingUri)) {
                    handledIncomingUri = key;
                    importFromUri(stream);
                }
                return;
            }
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && !shared.trim().isEmpty()) {
                pendingSharedText = shared;
                List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(shared);
                if (blocks.isEmpty()) {
                    createAndLaunchQuickPaste(shared, "");
                } else {
                    createAndLaunchQuickPaste(blocks.get(0).code, blocks.get(0).language);
                }
            }
        }
    }

    /** Text handed over by another app, offered on the next paste action. */
    public String consumePendingSharedTextForTest() {
        String value = pendingSharedText;
        pendingSharedText = null;
        return value;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appearanceAtCreate != com.nous.codecanvas.util.AppearanceManager.getMode(this)) {
            recreate();
            return;
        }
        loadDocuments();
        if (updater != null) updater.onResume();
        // The visit almost always starts with code already on the clipboard; ask once, politely.
        maybeOfferClipboardBanner();
    }

    @Override
    public void onBackPressed() {
        if (selectionMode) {
            exitSelectionMode();
            return;
        }
        if (drawerRoot != null && drawerRoot.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return;
        }
        super.onBackPressed();
    }

    private void initViews() {
        listDocuments = findViewById(R.id.list_documents);
        gridDocuments = findViewById(R.id.grid_documents);
        viewEmpty = findViewById(R.id.view_empty);
        editSearch = findViewById(R.id.edit_search);
        btnQuickPaste = findViewById(R.id.btn_quick_paste_preview);
        View emptyPaste = findViewById(R.id.btn_empty_paste);
        if (emptyPaste != null) emptyPaste.setOnClickListener(v -> performQuickPasteAndPreview());
        View emptyImport = findViewById(R.id.btn_empty_import);
        if (emptyImport != null) emptyImport.setOnClickListener(v -> startSafImport());
        btnNew = findViewById(R.id.btn_new);
        btnImport = findViewById(R.id.btn_import);

        bannerClipboard = findViewById(R.id.banner_clipboard);
        bannerSnippet = findViewById(R.id.banner_snippet);
        findViewById(R.id.btn_banner_import).setOnClickListener(v -> importBannerClip());
        findViewById(R.id.btn_banner_dismiss).setOnClickListener(v -> dismissClipboardBanner(true));

        selectionBar = findViewById(R.id.selection_bar);
        txtSelectionCount = findViewById(R.id.txt_selection_count);
        findViewById(R.id.btn_selection_close).setOnClickListener(v -> exitSelectionMode());
        findViewById(R.id.btn_selection_all).setOnClickListener(v -> toggleSelectAll());
        findViewById(R.id.btn_selection_share).setOnClickListener(v -> shareSelection());
        findViewById(R.id.btn_selection_delete).setOnClickListener(v -> confirmDeleteSelection());

        setupDrawer();

        adapter = new DocumentAdapter();
        listDocuments.setAdapter(adapter);
        gridAdapter = new DocumentAdapter();
        gridDocuments.setAdapter(gridAdapter);
        setGrid(getPreferences(0).getBoolean("grid", false));
        findViewById(R.id.btn_layout_list).setOnClickListener(v -> setGrid(false));
        findViewById(R.id.btn_layout_grid).setOnClickListener(v -> setGrid(true));
        updater = new com.nous.codecanvas.update.AppUpdater(this, (Button) findViewById(R.id.btn_check_update));

        backfiller = new ThumbnailBackfiller(this, () -> {
            if (adapter != null) adapter.notifyDataSetChanged();
            if (gridAdapter != null) gridAdapter.notifyDataSetChanged();
        });

        listDocuments.setOnItemClickListener((parent, view, position, id) -> onRowTapped(position));
        gridDocuments.setOnItemClickListener((parent, view, position, id) -> onRowTapped(position));
        listDocuments.setOnItemLongClickListener((parent, view, position, id) -> {
            onRowLongPressed(position);
            return true;
        });
        gridDocuments.setOnItemLongClickListener((parent, view, position, id) -> {
            onRowLongPressed(position);
            return true;
        });
    }

    private void setGrid(boolean grid) {
        getPreferences(0).edit().putBoolean("grid", grid).apply();
        listDocuments.setVisibility(grid ? View.GONE : View.VISIBLE);
        gridDocuments.setVisibility(grid ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_layout_list).setSelected(!grid);
        findViewById(R.id.btn_layout_grid).setSelected(grid);
    }

    private void setupListeners() {
        btnQuickPaste.setOnClickListener(v -> performQuickPasteAndPreview());
        btnNew.setOnClickListener(v -> showNewDocumentDialog());
        btnImport.setOnClickListener(v -> startSafImport());
        View settings = findViewById(R.id.btn_settings);
        if (settings != null) {
            settings.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        }

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDocuments(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadDocuments() {
        final boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new Thread(() -> {
            try {
                List<CanvasDocument> docs = repository.getAllDocuments();
                java.util.Map<String, DocumentPresentation> prepared = preparePresentations(docs, dark);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    presentations.clear();
                    presentations.putAll(prepared);
                    allDocuments.clear();
                    allDocuments.addAll(docs);
                    // Drop selections whose documents no longer exist.
                    Set<String> alive = new HashSet<>();
                    for (CanvasDocument d : docs) alive.add(d.getId());
                    selectedIds.retainAll(alive);
                    if (selectionMode && selectedIds.isEmpty()) exitSelectionMode();
                    filterDocuments(editSearch.getText().toString());
                    backfiller.enqueue(ThumbnailBackfiller.missingThumbnails(MainActivity.this, docs));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "加载文档失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void filterDocuments(String query) {
        applyFilters();
    }

    /**
     * One filter pipeline for both surfaces: the search box, the drawer's type pills, and the list
     * all read the same {@code filteredDocuments}, so the count in the header, the index in the
     * drawer and the gallery can never disagree.
     */
    private void applyFilters() {
        String query = editSearch != null ? editSearch.getText().toString() : "";
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        filteredDocuments.clear();
        for (CanvasDocument doc : allDocuments) {
            if (typeFilter != null) {
                RenderKind kind = RenderKindDetector.detect(doc.getTitle(), doc.getContent());
                if (!kind.name().equals(typeFilter)) continue;
            }
            if (!q.isEmpty()
                    && !doc.getTitle().toLowerCase(Locale.ROOT).contains(q)
                    && !doc.getContent().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            filteredDocuments.add(doc);
        }
        adapter.notifyDataSetChanged();
        ((BaseAdapter) gridDocuments.getAdapter()).notifyDataSetChanged();
        if (drawerAdapter != null) drawerAdapter.notifyDataSetChanged();
        ((TextView) findViewById(R.id.txt_file_count)).setText(
                (typeFilter == null ? "我的文件" : filterLabel(typeFilter)) + " · " + filteredDocuments.size());
        if (drawerSubtitle != null) {
            drawerSubtitle.setText(typeFilter == null
                    ? "全部作品 · " + filteredDocuments.size()
                    : filterLabel(typeFilter) + " · " + filteredDocuments.size());
        }
        viewEmpty.setVisibility(filteredDocuments.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String filterLabel(String kind) {
        if (RenderKind.HTML.name().equals(kind)) return "HTML 画布";
        if (RenderKind.SVG.name().equals(kind)) return "SVG 矢量图";
        if (RenderKind.XML.name().equals(kind)) return "XML 文档";
        if (RenderKind.RAW.name().equals(kind)) return "其它文本";
        return "我的文件";
    }

    // --- catalog drawer ---------------------------------------------------------------------------

    private void setupDrawer() {
        drawerRoot = findViewById(R.id.drawer_root);
        drawerPanel = findViewById(R.id.drawer_panel);
        drawerList = findViewById(R.id.drawer_list);
        drawerSubtitle = findViewById(R.id.drawer_subtitle);
        drawerFilters = findViewById(R.id.drawer_filters);

        drawerAdapter = new DrawerAdapter();
        drawerList.setAdapter(drawerAdapter);
        drawerList.setOnItemClickListener((p, v, pos, id) -> {
            CanvasDocument doc = drawerAdapter.getItem(pos);
            closeDrawer();
            openEditor(doc.getId(), true);
        });
        drawerList.setOnItemLongClickListener((p, v, pos, id) -> {
            CanvasDocument doc = drawerAdapter.getItem(pos);
            closeDrawer();
            // Long-press in the index jumps straight to that file's action sheet.
            drawerList.postDelayed(() -> showDocumentActions(doc), 180);
            return true;
        });

        findViewById(R.id.drawer_scrim).setOnClickListener(v -> closeDrawer());
        findViewById(R.id.drawer_settings).setOnClickListener(v -> {
            closeDrawer();
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
        findViewById(R.id.btn_drawer).setOnClickListener(v -> openDrawer());
        findViewById(R.id.btn_drawer_close).setOnClickListener(v -> closeDrawer());

        buildFilterChips();
    }

    /** 全部 / HTML / SVG / XML / 其它 — one pill row, single-select. */
    private void buildFilterChips() {
        drawerFilters.removeAllViews();
        String[][] options = {
                {"全部", null},
                {"HTML", RenderKind.HTML.name()},
                {"SVG", RenderKind.SVG.name()},
                {"XML", RenderKind.XML.name()},
                {"其它", RenderKind.RAW.name()},
        };
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String[] option : options) {
            View chip = inflater.inflate(R.layout.item_drawer_filter, drawerFilters, false);
            boolean active = option[1] == null ? typeFilter == null : option[1].equals(typeFilter);
            chip.setSelected(active);
            TextView label = chip.findViewById(R.id.filter_label);
            label.setText(option[0]);
            label.setSelected(active);
            chip.setOnClickListener(v -> {
                typeFilter = option[1];
                buildFilterChips();
                applyFilters();
            });
            drawerFilters.addView(chip);
        }
    }

    private void openDrawer() {
        drawerAdapter.notifyDataSetChanged();
        drawerRoot.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(-drawerPanel.getWidth() > 0
                ? -drawerPanel.getWidth() : -dp(294));
        drawerPanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void closeDrawer() {
        drawerPanel.animate().translationX(-drawerPanel.getWidth())
                .setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> drawerRoot.setVisibility(View.GONE))
                .start();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class DrawerAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredDocuments.size(); }
        @Override public CanvasDocument getItem(int position) { return filteredDocuments.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_drawer_document, parent, false);
            }
            CanvasDocument doc = getItem(position);
            DocumentPresentation presentation = presentations.get(doc.getId());
            TextView title = convertView.findViewById(R.id.drawer_item_title);
            TextView meta = convertView.findViewById(R.id.drawer_item_meta);
            android.widget.ImageView thumb = convertView.findViewById(R.id.drawer_item_thumb);

            title.setText(doc.getTitle());
            if (presentation != null) {
                meta.setText(presentation.kind.name() + " · " + presentation.sizeText);
                String key = presentation.previewKey;
                thumb.setTag(key);
                thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER);
                thumb.setImageResource(presentation.kind == RenderKind.HTML ? R.drawable.cover_web
                        : presentation.kind == RenderKind.SVG ? R.drawable.cover_vector
                        : R.drawable.cover_document);
                android.graphics.Bitmap cached = ThumbnailBackfiller.memoryGet(key);
                if (cached != null && !cached.isRecycled()) {
                    thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    thumb.setImageBitmap(cached);
                }
            } else {
                meta.setText("");
            }
            return convertView;
        }
    }

    // --- row interaction: tap opens (or selects), long-press starts selection ---------------------

    private void onRowTapped(int position) {
        if (position < 0 || position >= filteredDocuments.size()) return;
        CanvasDocument doc = filteredDocuments.get(position);
        if (selectionMode) {
            toggleSelection(doc.getId());
        } else {
            openEditor(doc.getId(), true);
        }
    }

    private void onRowLongPressed(int position) {
        if (position < 0 || position >= filteredDocuments.size()) return;
        CanvasDocument doc = filteredDocuments.get(position);
        // The gesture crossed the platform's long-press threshold; say so the way the platform
        // does, so entering selection reads as a deliberate mode rather than a stuck tap.
        View row = listDocuments.getChildAt(position - listDocuments.getFirstVisiblePosition());
        if (row == null) row = gridDocuments.getChildAt(position - gridDocuments.getFirstVisiblePosition());
        if (row != null) {
            row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }
        if (!selectionMode) {
            enterSelectionMode(doc.getId());
        } else {
            toggleSelection(doc.getId());
        }
    }

    private void enterSelectionMode(String firstId) {
        selectionMode = true;
        selectedIds.clear();
        selectedIds.add(firstId);
        selectionBar.setVisibility(View.VISIBLE);
        refreshSelectionUi();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        selectedIds.clear();
        selectionBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
        gridAdapter.notifyDataSetChanged();
    }

    private void toggleSelection(String id) {
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);
        if (selectedIds.isEmpty()) {
            exitSelectionMode();
            return;
        }
        refreshSelectionUi();
    }

    private void toggleSelectAll() {
        if (selectedIds.size() == filteredDocuments.size()) {
            exitSelectionMode();
            return;
        }
        for (CanvasDocument d : filteredDocuments) selectedIds.add(d.getId());
        refreshSelectionUi();
    }

    private void refreshSelectionUi() {
        String label = "已选 " + selectedIds.size() + " 项";
        txtSelectionCount.setText(label);
        // Selection is a mode, not a visual detail: announce it for screen readers.
        txtSelectionCount.announceForAccessibility(label);
        adapter.notifyDataSetChanged();
        gridAdapter.notifyDataSetChanged();
    }

    // --- batch actions ----------------------------------------------------------------------------

    private List<CanvasDocument> selectedDocuments() {
        List<CanvasDocument> out = new ArrayList<>();
        for (CanvasDocument d : filteredDocuments) {
            if (selectedIds.contains(d.getId())) out.add(d);
        }
        return out;
    }

    private void confirmDeleteSelection() {
        int count = selectedIds.size();
        if (count == 0) return;
        activeDialog = CanvasConfirmDialog.show(this,
                "删除 " + count + " 个画布",
                "这些文件会从本机移除，无法撤销。",
                "删除", true,
                () -> new Thread(() -> {
                    try {
                        for (String id : new ArrayList<>(selectedIds)) {
                            repository.deleteDocument(id);
                        }
                        runOnUiThread(() -> {
                            exitSelectionMode();
                            loadDocuments();
                            Toast.makeText(MainActivity.this, "已删除 " + count + " 个画布", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).start());
    }

    /** Share the selected sources concatenated as one text block. */
    private void shareSelection() {
        List<CanvasDocument> docs = selectedDocuments();
        if (docs.isEmpty()) return;
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                for (CanvasDocument d : docs) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(d.getContent());
                }
                String authority = getPackageName() + com.nous.codecanvas.provider.CanvasFileProvider.AUTHORITY_SUFFIX;
                String name = docs.size() == 1 ? docs.get(0).getTitle() : "code-canvas-" + docs.size() + "-files.txt";
                com.nous.codecanvas.util.ShareExporter.Staged staged =
                        com.nous.codecanvas.util.ShareExporter.stage(this, authority, name, sb.toString());
                runOnUiThread(() -> startActivity(
                        com.nous.codecanvas.util.ShareExporter.chooserIntent(staged, "分享源码")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void openEditor(String docId, boolean startPreview) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_DOC_ID, docId);
        intent.putExtra(EditorActivity.EXTRA_START_PREVIEW, startPreview);
        startActivity(intent);
    }

    // --- clipboard auto-detect ---------------------------------------------------------------------

    /**
     * Offer the clipboard's content once per distinct clip — off the main thread.
     *
     * <p>{@code ClipboardManager.getPrimaryClip()} and {@code ClipData.coerceToText()} are binder
     * calls: with a {@code content://} clip whose provider is slow or gone, they block the calling
     * thread. Reading them in {@code onResume} on the UI thread is an ANR waiting to happen, so the
     * read happens on a worker and only the visibility change comes back to the main thread. The
     * check is deliberately strict — the banner appears only when the clip really is renderable
     * markup — so the app never nags about a copied phone number.</p>
     */
    private void maybeOfferClipboardBanner() {
        if (!CanvasPrefs.clipboardAutoDetect(this)) return;
        final java.util.concurrent.ExecutorService worker = clipboardReadExecutor;
        worker.execute(() -> {
            String text = null;
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence raw = clip.getItemAt(0).coerceToText(this);
                        if (raw != null) text = raw.toString();
                    }
                }
            } catch (Exception ignored) {
                return; // an unreadable clipboard is simply not offered
            }
            if (text == null) return;
            final String clipText = text;
            if (clipText.hashCode() == lastHandledClipHash) return;
            if (!ClipboardCodeOffer.looksLikeCode(clipText)) return;
            final String oneLine = clipText.replace('\n', ' ').trim();
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (clipText.hashCode() == lastHandledClipHash) return;
                bannerClipContent = clipText;
                bannerSnippet.setText(oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine);
                bannerClipboard.setVisibility(View.VISIBLE);
            });
        });
    }

    private void importBannerClip() {
        String content = bannerClipContent;
        dismissClipboardBanner(true);
        if (content == null) return;
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(content);
        if (blocks.size() == 1) {
            createAndLaunchQuickPaste(blocks.get(0).code, blocks.get(0).language);
        } else if (blocks.size() > 1) {
            showMultiBlockSheet(blocks, content);
        } else {
            createAndLaunchQuickPaste(content, "");
        }
    }

    private void dismissClipboardBanner(boolean remember) {
        if (remember && bannerClipContent != null) {
            lastHandledClipHash = bannerClipContent.hashCode();
        }
        bannerClipContent = null;
        bannerClipboard.setVisibility(View.GONE);
    }

    // --- quick paste --------------------------------------------------------------------------------

    private void performQuickPasteAndPreview() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "剪贴板为空，请先复制代码", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence rawText = clip.getItemAt(0).coerceToText(this);
        if (rawText == null || rawText.length() == 0) {
            Toast.makeText(this, "剪贴板内容为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rawText.length() > 2 * 1024 * 1024) {
            Toast.makeText(this, "剪贴板代码过大 (超过 2MB 限制)", Toast.LENGTH_LONG).show();
            return;
        }

        final String rawString = rawText.toString();
        lastHandledClipHash = rawString.hashCode();
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extract(rawString);

        if (blocks.size() > 1) {
            showMultiBlockSheet(blocks, rawString);
        } else {
            String region = blocks.isEmpty() ? AiCodeExtractor.markupRegion(rawString) : null;
            if (region != null) {
                final String markup = region;
                CanvasBottomSheet.Action[] actions = new CanvasBottomSheet.Action[]{
                        new CanvasBottomSheet.Action(0, R.drawable.ic_paste, "只保存识别到的代码（推荐）",
                                "按第一个尖括号到最后一行截取", false),
                        new CanvasBottomSheet.Action(1, R.drawable.ic_copy, "保存全文",
                                rawString.length() + " 字符", false),
                };
                activeSheet = CanvasBottomSheet.show(this,
                        "这段内容里既有说明文字也有代码",
                        "没有找到代码围栏，截取规则选错了可以直接重来",
                        actions,
                        id -> createAndLaunchQuickPaste(id == 0 ? markup : rawString, ""));
                return;
            }
            String code = blocks.isEmpty() ? rawString : blocks.get(0).code;
            String lang = blocks.isEmpty() ? "" : blocks.get(0).language;
            createAndLaunchQuickPaste(code, lang);
        }
    }

    /** Multi-block pick: one sheet, one row per block, full text as the last row. */
    private void showMultiBlockSheet(List<AiCodeExtractor.CodeBlock> blocks, String rawString) {
        CanvasBottomSheet.Action[] actions = new CanvasBottomSheet.Action[blocks.size() + 1];
        for (int i = 0; i < blocks.size(); i++) {
            AiCodeExtractor.CodeBlock b = blocks.get(i);
            String label = b.language.isEmpty()
                    ? "代码块 " + (i + 1)
                    : b.language.toUpperCase(Locale.ROOT) + " 块 " + (i + 1);
            String snippet = b.code.length() > 34 ? b.code.substring(0, 34).replace("\n", " ") + "…"
                    : b.code.replace("\n", " ");
            actions[i] = new CanvasBottomSheet.Action(i, R.drawable.ic_paste, label, snippet, false);
        }
        actions[blocks.size()] = new CanvasBottomSheet.Action(blocks.size(), R.drawable.ic_copy,
                "完整原始剪贴板文本", rawString.length() + " 字符", false);
        activeSheet = CanvasBottomSheet.show(this,
                "检测到 " + blocks.size() + " 段代码块",
                "选择一段创建画布",
                actions,
                id -> {
                    if (id < blocks.size()) {
                        createAndLaunchQuickPaste(blocks.get(id).code, blocks.get(id).language);
                    } else {
                        createAndLaunchQuickPaste(rawString, "");
                    }
                });
    }

    private void createAndLaunchQuickPaste(String code, String lang) {
        String inferredExt = inferExtension(code, lang);
        SimpleDateFormat sdf = new SimpleDateFormat("MMdd_HHmm", Locale.getDefault());
        String defaultTitle = "快速画布_" + sdf.format(new Date()) + inferredExt;

        CanvasDocument newDoc = new CanvasDocument(
                UUID.randomUUID().toString(),
                defaultTitle,
                code,
                System.currentTimeMillis()
        );

        new Thread(() -> {
            try {
                repository.saveDocument(newDoc);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "已从剪贴板创建并预览", Toast.LENGTH_SHORT).show();
                    openEditor(newDoc.getId(), true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "创建画布失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String inferExtension(String code, String lang) {
        String l = lang.trim().toLowerCase(Locale.ROOT);
        if (l.contains("html") || l.contains("htm")) return ".html";
        if (l.contains("svg")) return ".svg";
        if (l.contains("xml")) return ".xml";
        if (l.contains("json")) return ".json";
        if (l.contains("js") || l.contains("javascript")) return ".js";
        if (l.contains("css")) return ".css";

        RenderKind kind = RenderKindDetector.detect("", code);
        if (kind == RenderKind.SVG) return ".svg";
        if (kind == RenderKind.HTML) return ".html";
        if (kind == RenderKind.XML) return ".xml";
        return ".txt";
    }

    private void showNewDocumentDialog() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMdd_HHmm", Locale.getDefault());
        String defaultTitle = "新建画布_" + sdf.format(new Date()) + ".html";

        AlertDialog dialog = UiDialogHelper.createThemedInputDialog(
                this,
                "新建画布文件",
                "输入文件名 (例如 demo.html, chart.svg, config.xml)",
                defaultTitle,
                title -> {
                    CanvasDocument newDoc = new CanvasDocument(UUID.randomUUID().toString(), title, "", System.currentTimeMillis());
                    new Thread(() -> {
                        try {
                            repository.saveDocument(newDoc);
                            runOnUiThread(() -> openEditor(newDoc.getId(), false));
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                }
        );
        activeDialog = dialog;
        dialog.show();
    }

    // --- per-document actions (single-item sheet, reachable from the ⋮ glyph) -----------------------

    /** The ⋮ glyph's menu — the same bottom sheet as everywhere else. */
    private void showDocumentActions(final CanvasDocument doc) {
        if (doc == null) return;
        CanvasBottomSheet.Action[] actions = new CanvasBottomSheet.Action[]{
                new CanvasBottomSheet.Action(0, R.drawable.ic_open, "打开预览", null, false),
                new CanvasBottomSheet.Action(1, R.drawable.ic_edit, "重命名", null, false),
                new CanvasBottomSheet.Action(2, R.drawable.ic_copy, "复制全部代码", null, false),
                new CanvasBottomSheet.Action(3, R.drawable.ic_export, "导出到文件", null, false),
                new CanvasBottomSheet.Action(4, R.drawable.ic_share, "分享源码", null, false),
                new CanvasBottomSheet.Action(5, R.drawable.ic_delete, "删除", "无法撤销", true),
        };
        activeSheet = CanvasBottomSheet.show(this,
                doc.getTitle(),
                null,
                actions,
                id -> {
                    switch (id) {
                        case 0: openEditor(doc.getId(), true); break;
                        case 1: renameDocument(doc); break;
                        case 2: copyDocumentCode(doc); break;
                        case 3: exportDocument(doc); break;
                        case 4: shareDocument(doc); break;
                        case 5: confirmDeleteDocument(doc); break;
                    }
                });
    }

    private void renameDocument(final CanvasDocument doc) {
        activeDialog = UiDialogHelper.createThemedInputDialog(
                this,
                "重命名画布",
                "文件名可以带任意后缀（.html / .svg / .xml / .txt …）",
                doc.getTitle(),
                newTitle -> new Thread(() -> {
                    try {
                        repository.updateDocument(doc.getId(), newTitle, doc.getContent(), doc.getRevision());
                        runOnUiThread(() -> {
                            loadDocuments();
                            Toast.makeText(MainActivity.this, "已重命名为 " + newTitle, Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "重命名失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).start()
        );
        activeDialog.show();
    }

    private void copyDocumentCode(final CanvasDocument doc) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(doc.getTitle(), doc.getContent()));
        Toast.makeText(this, "已复制 " + doc.getTitle() + " 的代码", Toast.LENGTH_SHORT).show();
    }

    /** Reuses the editor's export path so a list export and an in-editor export behave alike. */
    private void exportDocument(final CanvasDocument doc) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, doc.getTitle());
        try {
            startActivityForResult(intent, REQ_EXPORT_DOCUMENT);
            pendingExportDocument = doc;
        } catch (Exception e) {
            Toast.makeText(this, "无法启动导出: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startSafImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT_SAF);
        } catch (Exception e) {
            Toast.makeText(this, "无法启动文件选择器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_SAF && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importFromUri(uri);
            }
        } else if (requestCode == REQ_EXPORT_DOCUMENT) {
            CanvasDocument doc = pendingExportDocument;
            pendingExportDocument = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && doc != null) {
                writeDocumentToUri(doc, data.getData());
            }
        }
    }

    /** Streams the document straight to the destination the user picked in the system file picker. */
    private void writeDocumentToUri(final CanvasDocument doc, final Uri target) {
        new Thread(() -> {
            try {
                java.io.OutputStream out = getContentResolver().openOutputStream(target);
                if (out == null) {
                    throw new java.io.IOException("无法写入所选位置");
                }
                try {
                    out.write(doc.getContent().getBytes(StandardCharsets.UTF_8));
                } finally {
                    out.close();
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "已导出 " + doc.getTitle(), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void importFromUri(Uri uri) {
        new Thread(() -> {
            try {
                String fileName = "imported.txt";
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    String last = uri.getLastPathSegment();
                    if (last != null && !last.trim().isEmpty()) {
                        fileName = last;
                    }
                } else {
                    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (nameIndex >= 0) {
                                String name = cursor.getString(nameIndex);
                                if (name != null && !name.trim().isEmpty()) {
                                    fileName = name;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        String last = uri.getLastPathSegment();
                        if (last != null && !last.trim().isEmpty()) fileName = last;
                    }
                }

                fileName = FileUtils.sanitizeFileName(fileName);

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                long totalBytes = 0;
                long MAX_IMPORT_SIZE = 2 * 1024 * 1024; // 2MB
                try (InputStream is = "file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null
                        ? new java.io.FileInputStream(uri.getPath())
                        : getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        byte[] data = new byte[8192];
                        int nRead;
                        while ((nRead = is.read(data, 0, data.length)) != -1) {
                            totalBytes += nRead;
                            if (totalBytes > MAX_IMPORT_SIZE) {
                                throw new IllegalStateException("导入文件超过大小限制 (最大 2MB)");
                            }
                            buffer.write(data, 0, nRead);
                        }
                    }
                }
                String content = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                if (content.trim().isEmpty()) {
                    throw new IllegalStateException("文件是空的，没有可导入的内容");
                }

                CanvasDocument importedDoc = new CanvasDocument(
                        UUID.randomUUID().toString(),
                        fileName,
                        content,
                        System.currentTimeMillis()
                );
                repository.saveDocument(importedDoc);

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "成功导入: " + importedDoc.getTitle(), Toast.LENGTH_SHORT).show();
                    openEditor(importedDoc.getId(), true);
                });

            } catch (Exception e) {
                android.util.Log.e("CodeCanvasImport", "import failed: " + uri, e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // --- list adapter --------------------------------------------------------------------------------

    private class DocumentAdapter extends BaseAdapter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        @Override public int getCount() { return filteredDocuments.size(); }
        @Override public CanvasDocument getItem(int position) { return filteredDocuments.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(
                        parent == gridDocuments ? R.layout.item_document_card : R.layout.item_document_list,
                        parent, false);
            }

            CanvasDocument doc = getItem(position);
            TextView txtTag = convertView.findViewById(R.id.txt_doc_tag);
            TextView txtTitle = convertView.findViewById(R.id.txt_doc_title);
            TextView txtTime = convertView.findViewById(R.id.txt_doc_time);
            TextView txtSize = convertView.findViewById(R.id.txt_doc_size);
            View contentContainer = convertView.findViewById(R.id.card_content_container);
            ImageButton btnMore = convertView.findViewById(R.id.btn_card_more);
            android.widget.ImageView checkBadge = convertView.findViewById(R.id.img_check_badge);

            android.widget.ImageView cover = convertView.findViewById(R.id.img_doc_preview);
            DocumentPresentation presentation = presentations.get(doc.getId());
            if (presentation == null) throw new IllegalStateException("Missing prepared document metadata");
            String key = presentation.previewKey;
            RenderKind coverKind = presentation.kind;
            Drawable typeCover = getResources().getDrawable(
                    coverKind == RenderKind.HTML ? R.drawable.cover_web
                            : coverKind == RenderKind.SVG ? R.drawable.cover_vector
                            : R.drawable.cover_document);

            cover.setTag(key);
            cover.setContentDescription("作品缩略图；还没生成好时显示文件类型封面");
            bindThumbnail(cover, key, typeCover);

            if (txtTitle != null) txtTitle.setText(doc.getTitle());
            if (txtTime != null) txtTime.setText(dateFormat.format(new Date(doc.getUpdatedAt())));
            if (txtTag != null) txtTag.setText(coverKind.name());
            if (txtSize != null) txtSize.setText(presentation.sizeText);

            // Selection chrome: badge + row tint; type cover stays under the badge.
            boolean selected = selectionMode && selectedIds.contains(doc.getId());
            if (checkBadge != null) {
                checkBadge.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
                checkBadge.setSelected(selected);
            }
            convertView.setBackgroundResource(selected
                    ? R.drawable.bg_row_selected
                    : R.drawable.bg_card);

            if (contentContainer != null) {
                contentContainer.setClickable(false);
                contentContainer.setFocusable(false);
            }
            convertView.setLongClickable(true);
            convertView.setOnClickListener(v -> {
                int pos = positionOf(doc.getId());
                if (pos >= 0) onRowTapped(pos);
            });

            if (btnMore != null) {
                btnMore.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
                btnMore.setOnClickListener(v -> showDocumentActions(doc));
            }

            return convertView;
        }

        private int positionOf(String id) {
            for (int i = 0; i < filteredDocuments.size(); i++) {
                if (filteredDocuments.get(i).getId().equals(id)) return i;
            }
            return -1;
        }
    }

    /**
     * The flicker fix. Every bind used to slam the type cover in first and swap the bitmap in
     * later — two paints, one visible flash, every time the row entered the screen. Now:
     * a memory cache hit lands the bitmap immediately with no intermediate state; only a true
     * miss (thumbnail still being rendered) shows the type cover, and the finished capture
     * crossfades in when the backfiller stores it.
     */
    private void bindThumbnail(android.widget.ImageView cover, String key, Drawable typeCover) {
        if (!CanvasPrefs.thumbnailsEnabled(MainActivity.this)) {
            cover.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            cover.setImageDrawable(typeCover);
            return;
        }
        android.graphics.Bitmap cached = ThumbnailBackfiller.memoryGet(key);
        if (cached != null && !cached.isRecycled()) {
            // A memory hit lands in the same frame, with no animation and no intermediate paint —
            // animating a cache hit is the other half of what makes a list flicker.
            cover.setAlpha(1f);
            cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            cover.setImageBitmap(cached);
            cover.setContentDescription("作品预览缩略图");
            cover.setTag(TAG_THUMB_BOUND, key);
            return;
        }
        boolean alreadyShowingThis = key.equals(cover.getTag(TAG_THUMB_BOUND));
        cover.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        cover.setImageDrawable(typeCover);
        if (!alreadyShowingThis) cover.setAlpha(1f);
        thumbnailExecutor.execute(() -> {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(
                    PreviewThumbnailCache.file(getApplicationContext(), key).getAbsolutePath());
            if (bitmap != null && PreviewThumbnailCache.looksBlank(bitmap)) {
                bitmap.recycle();
                PreviewThumbnailCache.file(getApplicationContext(), key).delete();
                bitmap = null;
            }
            if (bitmap != null) ThumbnailBackfiller.memoryPut(key, bitmap);
            final android.graphics.Bitmap loaded = bitmap;
            final boolean fadeIn = !alreadyShowingThis;
            runOnUiThread(() -> {
                if (key.equals(cover.getTag()) && loaded != null && !loaded.isRecycled()) {
                    cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    cover.setImageBitmap(loaded);
                    cover.setContentDescription("作品预览缩略图");
                    cover.setTag(TAG_THUMB_BOUND, key);
                    if (fadeIn) {
                        // Only a capture generated during this visit fades in; a row that already
                        // showed its thumbnail simply stays put.
                        cover.setAlpha(0f);
                        cover.animate().alpha(1f).setDuration(THUMB_FADE_MS)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start();
                    } else {
                        cover.setAlpha(1f);
                    }
                }
            });
        });
    }

    private void shareDocument(CanvasDocument doc) {
        if (doc == null) return;
        final String docTitle = doc.getTitle();
        final String docContent = doc.getContent();
        new Thread(() -> {
            try {
                String authority = getPackageName() + com.nous.codecanvas.provider.CanvasFileProvider.AUTHORITY_SUFFIX;
                com.nous.codecanvas.util.ShareExporter.Staged staged =
                        com.nous.codecanvas.util.ShareExporter.stage(this, authority, docTitle, docContent);
                runOnUiThread(() -> startActivity(
                        com.nous.codecanvas.util.ShareExporter.chooserIntent(staged, "分享源码")));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmDeleteDocument(CanvasDocument doc) {
        activeDialog = CanvasConfirmDialog.show(this,
                "删除画布",
                "「" + doc.getTitle() + "」会从本机移除，无法撤销。",
                "删除", true,
                () -> new Thread(() -> {
                    try {
                        repository.deleteDocument(doc.getId());
                        runOnUiThread(this::loadDocuments);
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).start());
    }

    public AlertDialog getActiveDialogForTest() { return activeDialog; }
    public Dialog getActiveSheetForTest() { return activeSheet; }
    public boolean isSelectionModeForTest() { return selectionMode; }
    public int getSelectionCountForTest() { return selectedIds.size(); }
    public boolean isClipboardBannerShowingForTest() {
        return bannerClipboard != null && bannerClipboard.getVisibility() == View.VISIBLE;
    }
    /** Test hook: run the clipboard offer without waiting for a real resume. */
    public void offerClipboardForTest() { maybeOfferClipboardBanner(); }

    /** Test hook: block until the asynchronous clipboard read has had its chance to land. */
    public void awaitClipboardReadForTest() {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        clipboardReadExecutor.execute(latch::countDown);
        try {
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override protected void onStop() {
        if (backfiller != null) backfiller.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (backfiller != null) backfiller.stop();
        if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
        if (activeSheet != null && activeSheet.isShowing()) activeSheet.dismiss();
        super.onDestroy();
        thumbnailExecutor.shutdown();
        clipboardReadExecutor.shutdown();
        if (updater != null) updater.close();
    }

    public void loadDocumentsForTest() {
        try {
            allDocuments.clear();
            allDocuments.addAll(repository.getAllDocuments());
            boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            presentations.clear();
            presentations.putAll(preparePresentations(allDocuments, dark));
            filterDocuments(editSearch != null ? editSearch.getText().toString() : "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
