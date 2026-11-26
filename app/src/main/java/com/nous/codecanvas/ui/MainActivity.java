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
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.data.ImportBatch;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.AiCodeExtractor;
import com.nous.codecanvas.util.CanvasPrefs;
import com.nous.codecanvas.util.CanvasClipboard;
import com.nous.codecanvas.util.ClipboardCodeOffer;
import com.nous.codecanvas.util.FileUtils;
import com.nous.codecanvas.util.PreviewKey;
import com.nous.codecanvas.util.RenderKind;

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

    /** How long typing must pause before the list is refiltered. */
    private static final long SEARCH_DEBOUNCE_MS = 120L;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;
    /** Exporting straight from the list, so a card does not have to be opened in the editor first. */
    private static final int REQ_EXPORT_DOCUMENT = 1002;

    /** Non-zero while a list is flinging. Disk decodes wait until it stops. */
    private int listScrollState = android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE;

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
    private ImportSession imports;
    private boolean importUiReady;
    private boolean importInterrupted;
    private AlertDialog importError;
    private boolean importUiDestroying;
    private int libraryLoadGeneration;
    /** Text shared into the app, consumed the next time the user asks to paste. */
    private String pendingSharedText;
    private View viewEmpty;
    private EditText editSearch;
    private Button btnQuickPaste;
    private ImageButton btnNew;
    private ImageButton btnImport;

    // --- clipboard auto-detect banner -----------------------------------------------------------
    private View bannerClipboard;
    private static final long NO_CLIP = Long.MIN_VALUE;
    private long offeredClip = NO_CLIP;
    private long dismissedClip = NO_CLIP;
    private volatile int clipOfferEpoch;
    private int pasteEpoch;
    private boolean pasteReading;
    private volatile boolean clipboardResumed;
    private ClipboardManager watchedClipboard;
    private final Runnable pendingClipOffer = this::readClipboardOffer;
    private final ClipboardManager.OnPrimaryClipChangedListener clipChanged = this::maybeOfferClipboardBanner;

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
    private int drawerWidth;
    private boolean drawerCompact;
    private final java.util.Map<View, Integer> drawerCoveredAccessibility = new java.util.HashMap<>();
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
    /**
     * Everything the list needs about a document, computed once per load.
     *
     * <p>Two costs used to be paid here for every document on every load, and both grew with the
     * library: {@code getBytes(UTF_8)} allocated a full second copy of the text just to measure it,
     * and {@code split("\r\n|\r|\n")} ran a regex over the whole document just to count lines.
     * A library of a few hundred files therefore spent real time allocating garbage before the
     * first row could be drawn. Measuring UTF-8 length and counting newlines are both O(n) with no
     * allocation, and the classification is cached so the filter pass does not re-parse either.</p>
     */
    private static final class DocumentPresentation {
        final String previewKey;
        final String sizeText;
        /**
         * The document's real kind.
         *
         * <p>This used to be {@link RenderKind}, which only has four values (HTML/SVG/XML/RAW) because
         * it predates the format work. Every Markdown, JSON, CSV, YAML, image and PDF document
         * therefore showed the tag "RAW" on the home screen — the list looked like it had lost its
         * type support even though the editor opened the files correctly.</p>
         */
        final com.nous.codecanvas.data.DocumentKind kind;
        /** Lower-cased copy of the source, built on first search and reused afterwards. */
        private String searchIndex;

        DocumentPresentation(CanvasDocument doc, boolean dark, boolean scripts) {
            String content = doc.getContent();
            kind = doc.kind();
            previewKey = PreviewKey.forThumbnail(doc.getId(), kind.name(), content, dark, scripts);

            int bytes = doc.isBlob() ? (int) Math.min(Integer.MAX_VALUE, doc.getByteSize())
                    : utf8Length(content);
            String size = bytes < 1024 ? bytes + " B"
                    : String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0f);
            sizeText = doc.isBlob() ? size : size + " · " + lineCount(content) + " 行";
        }

        /** Length of the UTF-8 encoding without materialising it. */
        private static int utf8Length(String text) {
            int bytes = 0;
            for (int i = 0, n = text.length(); i < n; i++) {
                char c = text.charAt(i);
                if (c < 0x80) {
                    bytes += 1;
                } else if (c < 0x800) {
                    bytes += 2;
                } else if (Character.isHighSurrogate(c) && i + 1 < n
                        && Character.isLowSurrogate(text.charAt(i + 1))) {
                    bytes += 4;
                    i++;
                } else {
                    bytes += 3;
                }
            }
            return bytes;
        }

        /** Line count without a regex or a split array. */
        private static int lineCount(String text) {
            if (text.isEmpty()) return 0;
            int lines = 1;
            for (int i = 0, n = text.length(); i < n; i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    lines++;
                } else if (c == '\r') {
                    lines++;
                    if (i + 1 < n && text.charAt(i + 1) == '\n') i++;
                }
            }
            return lines;
        }

        /**
         * Lower-cased haystack for the search box, built once per document.
         *
         * <p>The old filter lower-cased every document's full source on every keystroke — an
         * allocation proportional to the whole library per character typed, which is why search
         * felt heavy with a lot of files.</p>
         */
        String searchIndex(String content) {
            if (searchIndex == null) searchIndex = content.toLowerCase(Locale.ROOT);
            return searchIndex;
        }
    }

    private java.util.Map<String, DocumentPresentation> preparePresentations(List<CanvasDocument> docs, boolean dark) {
        java.util.Map<String, DocumentPresentation> result = new java.util.HashMap<>();
        boolean scripts = CanvasPrefs.scriptsAllowed(this);
        for (CanvasDocument doc : docs) {
            result.put(doc.getId(), new DocumentPresentation(doc, dark, scripts));
        }
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
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        setContentView(R.layout.activity_main);

        View rootView = findViewById(R.id.root_main);
        if (rootView != null) {
            rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                SystemBarInsets.apply(v, insets);
                return insets;
            });
        }

        repository = new DocumentRepository(this);
        appearanceAtCreate = com.nous.codecanvas.util.AppearanceManager.getMode(this);
        Object retained = getLastNonConfigurationInstance();
        imports = retained instanceof ImportSession
                ? (ImportSession) retained : new ImportSession(getApplicationContext());
        if (savedInstanceState != null) {
            typeFilter = savedInstanceState.getString("type_filter");
            importInterrupted = retained == null && savedInstanceState.getBoolean("import_pending");
        }
        try {
            repository.seedDefaultTemplatesIfEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }

        initViews();
        setupListeners();
        // Recreation restores the delivered Intent, not a new external user action.
        if (savedInstanceState == null) handleIncomingIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("import_pending", imports != null && imports.hasPending());
        if (typeFilter != null) outState.putString("type_filter", typeFilter);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return imports;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (appearanceAtCreate != com.nous.codecanvas.util.AppearanceManager.getMode(this)) return;
        importUiReady = true;
        imports.listener = this::presentImportOutcome;
        if (importInterrupted) {
            importInterrupted = false;
            showImportError(new ImportOutcome(null, "上次导入流程被中断。请先检查作品库，没有副本再重新选择文件。", true), false);
        }
        presentImportOutcome();
    }

    @Override
    protected void onPause() {
        importUiReady = false;
        if (imports != null) imports.listener = null;
        clipboardResumed = false;
        pasteEpoch++;
        pasteReading = false;
        stopClipboardOffers();
        super.onPause();
    }

    /** Every freshly delivered open/share is independent, even if its location is unchanged. */
    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            retainReadGrant(intent, data);
            importFromUri(data);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            importBatch(ImportBatch.fromShare(intent));
        } else if (Intent.ACTION_SEND.equals(action)) {
            Object attachment = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (attachment instanceof Uri) {
                Uri stream = (Uri) attachment;
                retainReadGrant(intent, stream);
                importFromUri(stream);
                return;
            }
            CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            String shared = sharedText == null ? null : sharedText.toString();
            if (shared != null && !shared.trim().isEmpty()) {
                pendingSharedText = shared;
                // Same chooser as a paste: several fenced blocks must be picked, not silently
                // reduced to the first one.
                offerIncomingText(shared);
            }
        }
    }

    private void retainReadGrant(Intent intent, Uri uri) {
        int needed = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if ((intent.getFlags() & needed) != needed) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException ignored) {
            // A one-time grant is enough to create the local copy.
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
        clipboardResumed = true;
        if (hasWindowFocus()) maybeOfferClipboardBanner();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyHomeChrome();
        if (drawerRoot != null) updateDrawerLayout();
    }

    /** Scroll through the space behind the floating action, but clear it at the end. */
    private void applyHomeChrome() {
        if (btnQuickPaste == null) return;
        boolean showPaste = !selectionMode && !filteredDocuments.isEmpty() && !editSearch.hasFocus();
        boolean drawerVisible = drawerRoot != null && drawerRoot.getVisibility() == View.VISIBLE;
        btnQuickPaste.setVisibility(showPaste && !drawerVisible ? View.VISIBLE : View.GONE);
        // Keep the selection scope stable until the user exits multi-select.
        findViewById(R.id.library_header).setVisibility(selectionMode ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.btn_library_filter).setEnabled(!selectionMode);
        findViewById(R.id.btn_search_clear).setEnabled(!selectionMode);
        editSearch.setEnabled(!selectionMode);
        int bottom = dp(showPaste ? 76 : 12);
        setGalleryBottomPadding(listDocuments, bottom);
        setGalleryBottomPadding(gridDocuments, bottom);
    }

    private static void setGalleryBottomPadding(View gallery, int bottom) {
        if (gallery.getPaddingBottom() == bottom) return;
        gallery.setPaddingRelative(gallery.getPaddingStart(), gallery.getPaddingTop(),
                gallery.getPaddingEnd(), bottom);
    }

    @Override
    public void onBackPressed() {
        if (consumeBack()) return;
        super.onBackPressed();
    }

    /** Drawer and multi-select have to go first. The system back gesture uses the same path. */
    private boolean consumeBack() {
        if (activeSheet != null && activeSheet.isShowing()) {
            activeSheet.dismiss();
            return true;
        }
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
            return true;
        }
        if (selectionMode) {
            exitSelectionMode();
            return true;
        }
        if (drawerRoot != null && drawerRoot.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return true;
        }
        if (editSearch != null && (editSearch.hasFocus() || editSearch.length() > 0)) {
            editSearch.setText("");
            finishLibrarySearch();
            applyFilters();
            return true;
        }
        return false;
    }

    private void onBackInvoked() {
        if (!consumeBack()) finish();
    }

    private void initViews() {
        listDocuments = findViewById(R.id.list_documents);
        gridDocuments = findViewById(R.id.grid_documents);
        if (largeLibraryText()) {
            gridDocuments.setNumColumns(1);
            findViewById(R.id.txt_brand_sub).setVisibility(View.GONE);
        }
        viewEmpty = findViewById(R.id.view_empty);
        editSearch = findViewById(R.id.edit_search);
        btnQuickPaste = findViewById(R.id.btn_quick_paste_preview);
        View emptyPaste = findViewById(R.id.btn_empty_paste);
        if (emptyPaste != null) emptyPaste.setOnClickListener(v -> performQuickPasteAndPreview());
        View emptyImport = findViewById(R.id.btn_empty_import);
        if (emptyImport != null) emptyImport.setOnClickListener(v -> startSafImport());
        findViewById(R.id.btn_empty_reset).setOnClickListener(v -> resetLibrarySearch());
        findViewById(R.id.btn_library_filter).setOnClickListener(this::showLibraryFormatMenu);
        btnNew = findViewById(R.id.btn_new);
        btnImport = findViewById(R.id.btn_import);

        bannerClipboard = findViewById(R.id.banner_clipboard);
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
        android.widget.AbsListView.OnScrollListener scrollPause = new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                listScrollState = scrollState;
                if (backfiller != null) {
                    backfiller.setPaused(scrollState != SCROLL_STATE_IDLE);
                }
                if (scrollState == SCROLL_STATE_IDLE) loadVisibleThumbs();
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
            }
        };
        listDocuments.setOnScrollListener(scrollPause);
        gridDocuments.setOnScrollListener(scrollPause);
        setGrid(getPreferences(0).getBoolean("grid", false));
        applyHomeChrome();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackInvoked);
        }
        findViewById(R.id.btn_layout_list).setOnClickListener(v -> setGrid(false));
        findViewById(R.id.btn_layout_grid).setOnClickListener(v -> setGrid(true));

        backfiller = new ThumbnailBackfiller(this, this::refreshVisibleThumbnails);
        tuneList(listDocuments);
        tuneList(gridDocuments);

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

    /** A compact row in grid mode keeps long names and two actions readable at large font sizes. */
    private boolean largeLibraryText() {
        return getResources().getConfiguration().fontScale >= 1.5f;
    }

    private void setGrid(boolean grid) {
        getPreferences(0).edit().putBoolean("grid", grid).apply();
        listDocuments.setVisibility(grid ? View.GONE : View.VISIBLE);
        gridDocuments.setVisibility(grid ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_layout_list).setSelected(!grid);
        findViewById(R.id.btn_layout_grid).setSelected(grid);
        (grid ? gridDocuments : listDocuments).post(this::loadVisibleThumbs);
    }

    private void setupListeners() {
        btnQuickPaste.setOnClickListener(v -> performQuickPasteAndPreview());
        btnNew.setOnClickListener(v -> showNewDocumentDialog());
        btnImport.setOnClickListener(v -> startSafImport());
        findViewById(R.id.btn_add_menu).setOnClickListener(v -> showLibraryAddMenu());
        View settings = findViewById(R.id.btn_settings);
        if (settings != null) {
            settings.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
        }

        View clear = findViewById(R.id.btn_search_clear);
        if (clear != null) {
            clear.setOnClickListener(v -> {
                editSearch.setText("");
                applyFilters();
                editSearch.requestFocus();
            });
        }
        editSearch.setOnFocusChangeListener((v, focused) -> applyHomeChrome());
        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                finishLibrarySearch();
                applyFilters();
                return true;
            }
            return false;
        });
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (clear != null) clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                // Coalesce the rebuild. Filtering 120 documents and rebinding the whole ListView on
                // every keystroke made typing feel heavy (measured 495ms per key in a large
                // library); the query is applied once the burst settles.
                searchHandler.removeCallbacks(searchRunnable);
                final String query = s.toString();
                searchRunnable = () -> filterDocuments(query);
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void showLibraryAddMenu() {
        if (selectionMode || (activeSheet != null && activeSheet.isShowing())) return;
        finishLibrarySearch();
        CanvasBottomSheet.Action[] actions = {
                new CanvasBottomSheet.Action(1, R.drawable.ic_edit,
                        getString(R.string.library_add_new), getString(R.string.library_add_new_note), false),
                new CanvasBottomSheet.Action(2, R.drawable.ic_import,
                        getString(R.string.library_add_import), getString(R.string.library_add_import_note), false)
        };
        activeSheet = CanvasBottomSheet.showCreation(this, getString(R.string.library_add_title),
                getString(R.string.library_add_subtitle), actions, id -> {
                    if (id == 1) btnNew.performClick();
                    else if (id == 2) btnImport.performClick();
                });
    }

    private void finishLibrarySearch() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
        findViewById(R.id.root_main).requestFocus();
    }

    private void resetLibrarySearch() {
        finishLibrarySearch();
        editSearch.setText("");
        setTypeFilter(null);
    }

    /** Android's anchored format menu adds no permanent row on a narrow screen. */
    private void showLibraryFormatMenu(View anchor) {
        if (selectionMode) return;
        finishLibrarySearch();
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        for (int i = 0; i < LIBRARY_FILTERS.length; i++) {
            String[] option = LIBRARY_FILTERS[i];
            popup.getMenu().add(1, i + 1, i, option[0])
                    .setCheckable(true)
                    .setChecked(java.util.Objects.equals(typeFilter, option[1]));
        }
        popup.getMenu().setGroupCheckable(1, true, true);
        popup.setOnMenuItemClickListener(item -> {
            setTypeFilter(LIBRARY_FILTERS[item.getItemId() - 1][1]);
            return true;
        });
        popup.show();
    }

    private void setTypeFilter(String filter) {
        if (selectionMode) return;
        typeFilter = filter;
        updateDrawerFilterSelection();
        applyFilters();
    }

    private boolean isDarkTheme() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void loadDocuments() {
        final int generation = ++libraryLoadGeneration;
        final boolean dark = isDarkTheme();
        new Thread(() -> {
            try {
                List<CanvasDocument> docs = repository.getAllDocuments();
                java.util.Map<String, DocumentPresentation> prepared = preparePresentations(docs, dark);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing() || generation != libraryLoadGeneration) return;
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
                    backfiller.setPaused(listScrollState != android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
                    backfiller.enqueue(ThumbnailBackfiller.missingThumbnails(MainActivity.this, docs));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing() && generation == libraryLoadGeneration) {
                        Toast.makeText(MainActivity.this, "加载文档失败，请返回作品库重试。",
                                Toast.LENGTH_SHORT).show();
                    }
                });
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
            DocumentPresentation presentation = presentations.get(doc.getId());
            if (typeFilter != null) {
                // Reuse the kind computed at load time: re-detecting re-parsed the head of every
                // document on every filter change.
                com.nous.codecanvas.data.DocumentKind kind = presentation != null
                        ? presentation.kind
                        : doc.kind();
                if (!matchesTypeFilter(kind)) continue;
            }
            if (!q.isEmpty()) {
                if (!doc.getTitle().toLowerCase(Locale.ROOT).contains(q)) {
                    String haystack = presentation != null
                            ? presentation.searchIndex(doc.getContent())
                            : doc.getContent().toLowerCase(Locale.ROOT);
                    if (!haystack.contains(q)) continue;
                }
            }
            filteredDocuments.add(doc);
        }
        adapter.notifyDataSetChanged();
        ((BaseAdapter) gridDocuments.getAdapter()).notifyDataSetChanged();
        if (drawerAdapter != null) drawerAdapter.notifyDataSetChanged();
        listDocuments.post(this::loadVisibleThumbs);
        ((TextView) findViewById(R.id.txt_file_count)).setText(
                (typeFilter == null ? "作品库" : filterLabel(typeFilter)) + " · " + filteredDocuments.size());
        updateDrawerIndex();
        boolean noMatch = filteredDocuments.isEmpty();
        viewEmpty.setVisibility(noMatch ? View.VISIBLE : View.GONE);
        TextView emptyTitle = findViewById(R.id.txt_empty);
        TextView emptyHint = findViewById(R.id.txt_empty_hint);
        View emptyPaste = findViewById(R.id.btn_empty_paste);
        View emptyImport = findViewById(R.id.btn_empty_import);
        View emptyIcon = findViewById(R.id.img_empty);
        View emptyReset = findViewById(R.id.btn_empty_reset);
        View localNote = findViewById(R.id.txt_empty_local_note);
        View format = findViewById(R.id.btn_library_filter);
        boolean libraryEmpty = allDocuments.isEmpty();
        format.setSelected(typeFilter != null);
        format.setContentDescription(typeFilter == null
                ? getString(R.string.library_filter_all_description)
                : getString(R.string.library_filter_active_description, filterLabel(typeFilter)));
        localNote.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
        emptyReset.setVisibility(noMatch && !libraryEmpty && !selectionMode ? View.VISIBLE : View.GONE);
        if (emptyIcon != null) emptyIcon.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
        if (emptyTitle != null) {
            emptyTitle.setText(libraryEmpty ? "把第一段代码变成作品" : "没有符合的作品");
        }
        if (emptyHint != null) {
            emptyHint.setText(libraryEmpty
                    ? "在 AI 聊天里复制 HTML、SVG 或 XML 代码，粘进来就能看到效果，不用装浏览器。"
                    : getString(R.string.library_no_matches_hint));
        }
        if (emptyPaste != null) emptyPaste.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
        if (emptyImport != null) emptyImport.setVisibility(libraryEmpty ? View.VISIBLE : View.GONE);
        applyHomeChrome();
    }

    /** "其它" is everything that is not HTML, SVG or XML. There is no DocumentKind named RAW. */
    private static final String FILTER_OTHER = "OTHER";
    /** Shared labels and keys for the drawer and the fast format picker. */
    private static final String[][] LIBRARY_FILTERS = {
            {"全部", null},
            {"HTML", RenderKind.HTML.name()},
            {"SVG", RenderKind.SVG.name()},
            {"XML", RenderKind.XML.name()},
            {"其它", FILTER_OTHER},
    };

    private boolean matchesTypeFilter(com.nous.codecanvas.data.DocumentKind kind) {
        if (typeFilter == null) return true;
        if (FILTER_OTHER.equals(typeFilter)) {
            return kind != com.nous.codecanvas.data.DocumentKind.HTML
                    && kind != com.nous.codecanvas.data.DocumentKind.SVG
                    && kind != com.nous.codecanvas.data.DocumentKind.XML;
        }
        return kind.name().equals(typeFilter);
    }

    private static String filterLabel(String kind) {
        if (RenderKind.HTML.name().equals(kind)) return "HTML 画布";
        if (RenderKind.SVG.name().equals(kind)) return "SVG 矢量图";
        if (RenderKind.XML.name().equals(kind)) return "XML 文档";
        if (FILTER_OTHER.equals(kind)) return "其它";
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
        drawerList.setEmptyView(findViewById(R.id.drawer_empty));
        tuneList(drawerList);
        drawerList.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                listScrollState = scrollState;
                if (backfiller != null) {
                    backfiller.setPaused(scrollState != SCROLL_STATE_IDLE);
                }
                if (scrollState == SCROLL_STATE_IDLE) loadVisibleThumbs();
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
            }
        });
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
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        findViewById(R.id.btn_drawer).setOnClickListener(v -> openDrawer());
        findViewById(R.id.btn_drawer_close).setOnClickListener(v -> closeDrawer());

        drawerRoot.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (drawerRoot.getVisibility() == View.VISIBLE && (r - l != oldR - oldL || b - t != oldB - oldT)) {
                updateDrawerLayout();
            }
        });
        updateDrawerLayout();
        updateDrawerIndex();
    }

    private void updateDrawerIndex() {
        if (drawerSubtitle == null) return;
        drawerSubtitle.setText(typeFilter == null
                ? getString(R.string.catalog_any_count, filteredDocuments.size())
                : getString(R.string.catalog_type_count, filterLabel(typeFilter), filteredDocuments.size()));
        boolean libraryEmpty = allDocuments.isEmpty();
        ((TextView) findViewById(R.id.drawer_empty_title)).setText(libraryEmpty
                ? R.string.catalog_empty_title : R.string.catalog_no_matches);
        ((TextView) findViewById(R.id.drawer_empty_hint)).setText(libraryEmpty
                ? R.string.catalog_empty_hint : R.string.catalog_no_matches_hint);
    }

    private void updateDrawerLayout() {
        View parent = findViewById(R.id.root_main);
        int available = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (available <= 0) available = getResources().getDisplayMetrics().widthPixels;
        int height = parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom();
        if (height <= 0) height = getResources().getDisplayMetrics().heightPixels;
        int width = Math.max(1, Math.min(dp(368), available - dp(36)));
        boolean compact = height < dp(400);
        if (drawerWidth == width && drawerCompact == compact && drawerFilters.getChildCount() > 0) return;
        drawerWidth = width;
        drawerCompact = compact;
        drawerPanel.getLayoutParams().width = drawerWidth;
        drawerPanel.requestLayout();
        findViewById(R.id.drawer_format_heading).setVisibility(compact ? View.GONE : View.VISIBLE);
        findViewById(R.id.drawer_list_heading).setVisibility(compact ? View.GONE : View.VISIBLE);
        buildDrawerFilters();
    }

    /**
     * Use balanced rows so there is no lonely, clipped “其它”. On short screens a single
     * scrollable row gives the actual file index back its height.
     */
    private void buildDrawerFilters() {
        drawerFilters.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout row = newDrawerFilterRow();
        for (int i = 0; i < LIBRARY_FILTERS.length; i++) {
            if (!drawerCompact && i == 3) {
                row = newDrawerFilterRow();
                row.setPadding(0, dp(4), 0, 0);
            }
            String[] option = LIBRARY_FILTERS[i];
            View chip = inflater.inflate(R.layout.item_drawer_filter, row, false);
            ((TextView) chip.findViewById(R.id.filter_label)).setText(option[0]);
            chip.setContentDescription(option[0]);
            chip.setTag(option[1]);
            chip.setOnClickListener(v -> setTypeFilter(option[1]));
            LinearLayout.LayoutParams params = drawerCompact
                    ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            if (row.getChildCount() > 0) params.setMarginStart(dp(8));
            row.addView(chip, params);
        }
        updateDrawerFilterSelection();
    }

    private LinearLayout newDrawerFilterRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        drawerFilters.addView(row, new LinearLayout.LayoutParams(
                drawerCompact ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void updateDrawerFilterSelection() {
        if (drawerFilters == null) return;
        for (int r = 0; r < drawerFilters.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) drawerFilters.getChildAt(r);
            for (int i = 0; i < row.getChildCount(); i++) {
                View chip = row.getChildAt(i);
                boolean active = java.util.Objects.equals(typeFilter, chip.getTag());
                chip.setSelected(active);
                chip.findViewById(R.id.filter_label).setSelected(active);
            }
        }
    }

    private void setHomeAccessibilityCovered(boolean covered) {
        if (!(drawerRoot.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) drawerRoot.getParent();
        if (covered) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child == drawerRoot || drawerCoveredAccessibility.containsKey(child)) continue;
                drawerCoveredAccessibility.put(child, child.getImportantForAccessibility());
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        } else {
            for (java.util.Map.Entry<View, Integer> previous : drawerCoveredAccessibility.entrySet()) {
                previous.getKey().setImportantForAccessibility(previous.getValue());
            }
            drawerCoveredAccessibility.clear();
        }
    }

    private int drawerOffscreenX() {
        return drawerWidth * (drawerPanel.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 1 : -1);
    }

    private void openDrawer() {
        finishLibrarySearch();
        updateDrawerLayout();
        updateDrawerFilterSelection();
        updateDrawerIndex();
        drawerAdapter.notifyDataSetChanged();
        setHomeAccessibilityCovered(true);
        View scrim = findViewById(R.id.drawer_scrim);
        drawerRoot.setVisibility(View.VISIBLE);
        applyHomeChrome();
        if (scrim != null) {
            scrim.animate().cancel();
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
        drawerPanel.animate().cancel();
        drawerPanel.setTranslationX(drawerOffscreenX());
        drawerPanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
        findViewById(R.id.btn_drawer_close).requestFocus();
    }

    private void closeDrawer() {
        View scrim = findViewById(R.id.drawer_scrim);
        if (scrim != null) {
            scrim.animate().cancel();
            scrim.animate().alpha(0f).setDuration(180)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .start();
        }
        drawerPanel.animate().cancel();
        drawerPanel.animate().translationX(drawerOffscreenX())
                .setDuration(180)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    drawerRoot.setVisibility(View.GONE);
                    setHomeAccessibilityCovered(false);
                    applyHomeChrome();
                    findViewById(R.id.btn_drawer).requestFocus();
                })
                .start();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class DrawerAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredDocuments.size(); }
        @Override public CanvasDocument getItem(int position) { return filteredDocuments.get(position); }
        @Override public boolean hasStableIds() { return true; }
        @Override public long getItemId(int position) {
            String id = getItem(position).getId();
            return id == null ? position : (id.hashCode() & 0xffffffffL);
        }

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
                meta.setText(presentation.kind.label() + " · " + presentation.sizeText);
                String key = presentation.previewKey;
                thumb.setTag(key);
                thumb.setPadding(0, 0, 0, 0);
                bindThumbnail(thumb, key, presentation.kind, doc);
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
        finishLibrarySearch();
        selectionMode = true;
        selectedIds.clear();
        selectedIds.add(firstId);
        selectionBar.setVisibility(View.VISIBLE);
        applyHomeChrome();
        refreshSelectionUi();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        selectedIds.clear();
        selectionBar.setVisibility(View.GONE);
        applyHomeChrome();
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

    /** Each selected canvas keeps its filename and bytes, regardless of the other selections. */
    private void shareSelection() {
        List<CanvasDocument> docs = selectedDocuments();
        if (docs.isEmpty()) return;
        new Thread(() -> {
            try {
                String authority = getPackageName() + com.nous.codecanvas.provider.CanvasFileProvider.AUTHORITY_SUFFIX;
                Intent chooser = com.nous.codecanvas.util.SourceWriter.shareDocuments(
                        MainActivity.this, authority, docs);
                runOnUiThread(() -> startActivity(chooser));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void openEditor(String docId, boolean startPreview) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_DOC_ID, docId);
        intent.putExtra(EditorActivity.EXTRA_START_PREVIEW, startPreview);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    // --- clipboard auto-detect ---------------------------------------------------------------------

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (bannerClipboard == null) return;
        if (focused && clipboardResumed) maybeOfferClipboardBanner();
        else stopClipboardOffers();
    }

    private void stopClipboardOffers() {
        clipOfferEpoch++;
        searchHandler.removeCallbacks(pendingClipOffer);
        if (bannerClipboard != null) dismissClipboardBanner(false);
        if (watchedClipboard != null) {
            watchedClipboard.removePrimaryClipChangedListener(clipChanged);
            watchedClipboard = null;
        }
    }

    private void maybeOfferClipboardBanner() {
        // Every change invalidates the UI immediately, even when the replacement is empty.
        clipOfferEpoch++;
        searchHandler.removeCallbacks(pendingClipOffer);
        dismissClipboardBanner(false);
        if (!clipboardResumed || !hasWindowFocus() || !CanvasPrefs.clipboardAutoDetect(this)) {
            stopClipboardOffers();
            return;
        }
        if (watchedClipboard == null) {
            watchedClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (watchedClipboard != null) watchedClipboard.addPrimaryClipChangedListener(clipChanged);
        }
        searchHandler.postDelayed(pendingClipOffer, 80);
    }

    private void readClipboardOffer() {
        final int epoch = clipOfferEpoch;
        final Context app = getApplicationContext();
        clipboardReadExecutor.execute(() -> {
            if (!clipboardResumed || epoch != clipOfferEpoch || !CanvasPrefs.clipboardAutoDetect(app)) return;
            CanvasClipboard.Read clip = CanvasClipboard.read(app, true);
            final long token = clip.status == CanvasClipboard.Status.TEXT && ClipboardCodeOffer.looksLikeCode(clip.text)
                    ? clip.token : NO_CLIP;
            runOnUiThread(() -> {
                if (epoch != clipOfferEpoch || !canUseClipboard() || !CanvasPrefs.clipboardAutoDetect(this)) return;
                if (token == NO_CLIP || token == dismissedClip) return;
                offeredClip = token;
                // Detection never leaves raw clipboard text in a view or a field.
                bannerClipboard.setVisibility(View.VISIBLE);
            });
        });
    }

    private boolean canUseClipboard() {
        return clipboardResumed && hasWindowFocus() && !isFinishing() && !isDestroyed();
    }

    private void importBannerClip() {
        long expected = offeredClip;
        dismissClipboardBanner(true);
        if (expected != NO_CLIP) readPaste(expected);
    }

    private void dismissClipboardBanner(boolean remember) {
        if (remember && offeredClip != NO_CLIP) dismissedClip = offeredClip;
        offeredClip = NO_CLIP;
        bannerClipboard.setVisibility(View.GONE);
    }

    private void performQuickPasteAndPreview() {
        readPaste(NO_CLIP);
    }

    private void readPaste(long expected) {
        if (!canUseClipboard() || pasteReading) return;
        pasteReading = true;
        final int epoch = ++pasteEpoch;
        final Context app = getApplicationContext();
        clipboardReadExecutor.execute(() -> {
            CanvasClipboard.Read read = CanvasClipboard.read(app, expected != NO_CLIP);
            runOnUiThread(() -> {
                if (epoch != pasteEpoch) return;
                pasteReading = false;
                if (!canUseClipboard()) return;
                if (expected != NO_CLIP && !CanvasPrefs.clipboardAutoDetect(this)) return;
                if (expected != NO_CLIP && (read.status != CanvasClipboard.Status.TEXT || read.token != expected)) {
                    Toast.makeText(this, "剪贴板已变化，请点「粘贴」读取当前内容。", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (read.status == CanvasClipboard.Status.TEXT) {
                    dismissedClip = read.token;
                    dismissClipboardBanner(false);
                    offerIncomingText(read.text);
                } else Toast.makeText(this, read.message(), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void offerIncomingText(String rawString) {
        if (rawString == null || rawString.isEmpty()) {
            Toast.makeText(this, "剪贴板内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rawString.length() > 2 * 1024 * 1024) {
            Toast.makeText(this, "剪贴板代码过大 (超过 2MB 限制)", Toast.LENGTH_LONG).show();
            return;
        }
        pendingSharedText = null;
        if (rawString.trim().isEmpty()) {
            Toast.makeText(this, "没有可导入的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        List<AiCodeExtractor.CodeBlock> blocks = AiCodeExtractor.extractFenced(rawString);
        if (blocks.size() > 1 || (blocks.size() == 1 && blocks.get(0).hasSurroundingText(rawString))) {
            showMultiBlockSheet(blocks, rawString);
            return;
        }
        if (blocks.size() == 1) {
            createAndLaunchQuickPaste(blocks.get(0).code, blocks.get(0).language);
            return;
        }
        String markup = AiCodeExtractor.markupRegion(rawString);
        if (markup != null) {
            CanvasBottomSheet.Action[] actions = {
                    new CanvasBottomSheet.Action(0, R.drawable.ic_paste, "只打开识别到的代码",
                            "按标记范围提取，说明文字不导入", false),
                    new CanvasBottomSheet.Action(1, R.drawable.ic_copy, "保留完整原文",
                            "说明文字和代码都保留", false),
            };
            activeSheet = CanvasBottomSheet.show(this, "说明文字与代码", "选择要导入的内容",
                    actions, id -> createAndLaunchQuickPaste(id == 0 ? markup : rawString, id == 0 ? "" : "plaintext"));
            return;
        }
        createAndLaunchQuickPaste(rawString, "");
    }

    /** Multi-block pick: one sheet, one row per block, full text as the last row. */
    private void showMultiBlockSheet(List<AiCodeExtractor.CodeBlock> blocks, String rawString) {
        activeSheet = CodeBlockSheet.show(this, blocks, rawString, this::createAndLaunchQuickPaste);
    }

    private void createAndLaunchQuickPaste(String code, String lang) {
        String inferredExt = com.nous.codecanvas.util.PasteFileNaming.extension(code, lang);
        SimpleDateFormat sdf = new SimpleDateFormat("MMdd_HHmm", Locale.getDefault());
        String defaultTitle = "快速画布_" + sdf.format(new Date()) + inferredExt;

        CanvasDocument newDoc = new CanvasDocument(
                UUID.randomUUID().toString(),
                defaultTitle,
                code,
                System.currentTimeMillis()
        );

        newDoc.setLineEnding(com.nous.codecanvas.util.TextCodec.PRESERVE_LINE_ENDINGS);

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
        List<CanvasBottomSheet.Action> actions = new ArrayList<>();
        actions.add(new CanvasBottomSheet.Action(0, R.drawable.ic_open, "打开预览"));
        actions.add(new CanvasBottomSheet.Action(1, R.drawable.ic_edit, "重命名"));
        if (!doc.isBlob())
            actions.add(new CanvasBottomSheet.Action(2, R.drawable.ic_copy, "复制全部代码"));
        actions.add(new CanvasBottomSheet.Action(3, R.drawable.ic_export, "导出到文件"));
        actions.add(new CanvasBottomSheet.Action(4, R.drawable.ic_share, doc.isBlob() ? "分享文件" : "分享源码"));
        actions.add(new CanvasBottomSheet.Action(5, R.drawable.ic_delete, "删除", "无法撤销", true));
        DocumentPresentation presentation = presentations.get(doc.getId());
        String metadata = presentation == null ? null : ListMeta.rowDetail(presentation.sizeText,
                System.currentTimeMillis(), doc.getUpdatedAt());
        activeSheet = CanvasBottomSheet.show(this,
                doc.getTitle(),
                metadata,
                actions.toArray(new CanvasBottomSheet.Action[0]),
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
                        CanvasDocument renamed = doc.snapshot();
                        renamed.setTitle(newTitle);
                        if (!renamed.isBlob()) {
                            com.nous.codecanvas.data.FileKindDetector.applyTo(renamed);
                        }
                        repository.updateDocument(renamed.getId(), renamed.getTitle(), renamed.getContent(), doc.getRevision());
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
        String mime = doc.getMimeType();
        if (mime == null || mime.isEmpty() || "*/*".equals(mime) || "application/octet-stream".equals(mime)) {
            mime = com.nous.codecanvas.util.ShareExporter.mimeTypeFor(doc.getTitle());
        }
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, doc.getTitle());
        try {
            startActivityForResult(intent, REQ_EXPORT_DOCUMENT);
            pendingExportDocument = doc;
        } catch (Exception e) {
            Toast.makeText(this, "无法启动导出: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean startSafImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQ_IMPORT_SAF);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "无法启动文件选择器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_SAF && resultCode == RESULT_OK && data != null) {
            ImportBatch selected = ImportBatch.fromPicker(data);
            if (!selected.grouped && selected.files.size() == 1) {
                Uri uri = selected.files.get(0);
                // Keep the existing single-file linked-source workflow.
                int granted = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (granted != 0) {
                    try { getContentResolver().takePersistableUriPermission(uri, granted); }
                    catch (SecurityException | IllegalArgumentException ignored) {}
                }
                importFromUri(uri);
            } else {
                importBatch(selected);
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
                byte[] payload = com.nous.codecanvas.util.SourceWriter.payloadBytes(
                        this, doc, doc.getContent());
                com.nous.codecanvas.util.SourceWriter.writeToUri(this, target, payload);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "已导出 " + doc.getTitle(), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** A source URI is a location. Each successful open saves a separate local copy. */
    private void importFromUri(Uri uri) {
        ImportSession session = imports;
        if (!session.enqueue(() -> session.load(uri, false))) {
            showImportQueueFull();
        }
    }

    private void importBatch(ImportBatch batch) {
        // Unlike a single linked source, a batch only needs the temporary read permission.
        // Capture the retained session itself, not this Activity inside the background closure.
        ImportSession session = imports;
        if (!session.enqueue(() -> session.loadBatch(batch))) {
            showImportQueueFull();
        } else if (!batch.files.isEmpty()) {
            Toast.makeText(this, "正在导入 " + batch.files.size() + " 个文件…", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportQueueFull() {
        Toast.makeText(this, "导入队列已满。待当前导入结束后，请重新分享或选择文件。", Toast.LENGTH_LONG).show();
    }

    private void presentImportOutcome() {
        if (!importUiReady || isFinishing() || isDestroyed() || importError != null) return;
        ImportOutcome result = imports.completed.peek();
        if (result == null) return;
        if (result.summary) {
            if (result.saved > 0) loadDocuments();
            boolean hasFilters = result.saved > 0 && !selectionMode
                    && (typeFilter != null || !editSearch.getText().toString().trim().isEmpty());
            importError = CanvasConfirmDialog.show(this, "批量导入结果", result.message,
                    hasFilters ? "清除筛选" : "完成", "保留筛选", false,
                    hasFilters ? this::clearImportFilters : null);
            bindImportDismiss(result, true, hasFilters);
        } else if (result.document != null) {
            imports.completed.remove();
            importUiReady = false; // Only one editor opens per visit to the foreground.
            Toast.makeText(this, "已导入副本 · " + result.document.getTitle(), Toast.LENGTH_SHORT).show();
            openEditor(result.document.getId(), true);
        } else {
            showImportError(result, true);
        }
    }

    private void clearImportFilters() {
        editSearch.setText("");
        setTypeFilter(null);
        editSearch.clearFocus();
    }

    private void showImportError(ImportOutcome failed, boolean queued) {
        importError = CanvasConfirmDialog.show(this, "未能导入文件", failed.message,
                failed.offerPicker ? "重新选择" : "知道了", "关闭", false,
                failed.offerPicker ? this::retryImportInPicker : null, null);
        bindImportDismiss(failed, queued, failed.offerPicker);
    }

    private void bindImportDismiss(ImportOutcome result, boolean queued, boolean showCancel) {
        if (!showCancel) CanvasConfirmDialog.cancelButton(importError).setVisibility(View.GONE);
        importError.setOnDismissListener(dialog -> {
            importError = null;
            // Dismissing an old Activity's window is not a user's response.
            if (importUiDestroying) return;
            if (queued && imports.completed.peek() == result) imports.completed.remove();
            searchHandler.post(this::presentImportOutcome);
        });
    }

    private void retryImportInPicker() {
        importUiReady = false;
        if (!startSafImport()) importUiReady = true;
    }

    private static final class ImportOutcome {
        final CanvasDocument document;
        final String message;
        final boolean offerPicker;
        final boolean summary;
        final int saved;

        ImportOutcome(CanvasDocument document, String message, boolean offerPicker) {
            this(document, message, offerPicker, false, 0);
        }

        ImportOutcome(CanvasDocument document, String message, boolean offerPicker, boolean summary, int saved) {
            this.document = document;
            this.message = message;
            this.offerPicker = offerPicker;
            this.summary = summary;
            this.saved = saved;
        }
    }

    /** Serial file I/O, retained during recreation, with no reference to an inactive Activity. */
    private static final class ImportSession {
        interface Job { ImportOutcome load(); }

        private final Context context;
        private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        private final java.util.ArrayDeque<Job> queue = new java.util.ArrayDeque<>();
        final java.util.ArrayDeque<ImportOutcome> completed = new java.util.ArrayDeque<>();
        boolean running;
        Runnable listener;

        ImportSession(Context context) { this.context = context; }

        boolean hasPending() { return running || !queue.isEmpty() || !completed.isEmpty(); }

        boolean enqueue(Job job) {
            if (queue.size() + completed.size() + (running ? 1 : 0) >= 8) return false;
            queue.add(job);
            startNext();
            return true;
        }

        private void startNext() {
            if (running || queue.isEmpty()) return;
            Job job = queue.remove();
            running = true;
            new Thread(() -> {
                ImportOutcome outcome = job.load();
                main.post(() -> {
                    running = false;
                    completed.add(outcome);
                    if (listener != null) listener.run();
                    startNext();
                });
            }, "CanvasImport").start();
        }

        private ImportOutcome loadBatch(ImportBatch input) {
            int saved = 0, failed = 0;
            StringBuilder details = new StringBuilder();
            for (int i = 0; i < input.files.size(); i++) {
                ImportOutcome result = load(input.files.get(i), true);
                if (result.document != null) saved++;
                else {
                    failed++;
                    details.append("\n第 ").append(input.positions.get(i)).append(" 项：").append(result.message);
                }
            }
            StringBuilder summary = new StringBuilder();
            if (saved > 0) summary.append("已保存 ").append(saved).append(" 个副本到作品库。");
            else summary.append("没有文件导入。");
            if (failed > 0) summary.append("\n").append(failed).append(" 个文件未能导入。");
            if (input.invalid > 0) summary.append("\n").append(input.invalid).append(" 项不是可读取的文件链接。");
            if (input.duplicates > 0) summary.append("\n已跳过 ").append(input.duplicates).append(" 个重复项。");
            if (input.overflow > 0) summary.append("\n每次最多导入 ").append(ImportBatch.MAX_FILES)
                    .append(" 个文件，另有 ").append(input.overflow).append(" 项未处理，可分批再选。");
            if (saved == 0 && failed == 0 && input.invalid == 0 && input.overflow == 0) {
                summary.append("\n请在文件管理器或原应用中重新选择文件。");
            }
            return new ImportOutcome(null, summary.append(details).toString(), false, true, saved);
        }

        private ImportOutcome load(Uri uri, boolean localOnly) {
            FileImporter.Imported imported;
            try {
                imported = FileImporter.importFrom(context, uri);
            } catch (FileImporter.UnsupportedImportException e) {
                return new ImportOutcome(null, e.getMessage(), false);
            } catch (Exception e) {
                return new ImportOutcome(null, localOnly
                        ? "读取失败。请从系统文件选择器重选该文件。"
                        : "没有读到文件。它可能已被移动，或来源应用没有授予访问权限。可用系统文件选择器再选一次。", true);
            }
            CanvasDocument doc = imported.document;
            try {
                if (localOnly) doc.setSourceUri(null);
                if (doc.isBlob()) FileImporter.writeBlob(context, doc.getId(), imported.bytes);
                new DocumentRepository(context).saveDocument(doc);
                return new ImportOutcome(doc, null, false);
            } catch (Exception e) {
                if (doc.isBlob()) FileImporter.blobFile(context, doc.getId()).delete();
                return new ImportOutcome(null, "文件已读取，但未能保存副本。请检查设备剩余空间后再试。", false);
            }
        }
    }

    // --- list adapter --------------------------------------------------------------------------------

    private static final class Row {
        String boundId;
        TextView title;
        TextView tag;
        TextView time;
        TextView size;
        ImageButton more;
        android.widget.ImageView cover;
        android.widget.ImageView check;
    }

    private class DocumentAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredDocuments.size(); }
        @Override public CanvasDocument getItem(int position) { return filteredDocuments.get(position); }
        @Override public boolean hasStableIds() { return true; }
        @Override public long getItemId(int position) {
            String id = getItem(position).getId();
            return id == null ? position : (id.hashCode() & 0xffffffffL);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row;
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(
                        parent == gridDocuments && !largeLibraryText()
                                ? R.layout.item_document_card : R.layout.item_document_list,
                        parent, false);
                row = new Row();
                row.title = convertView.findViewById(R.id.txt_doc_title);
                row.tag = convertView.findViewById(R.id.txt_doc_tag);
                row.time = convertView.findViewById(R.id.txt_doc_time);
                row.size = convertView.findViewById(R.id.txt_doc_size);
                row.more = convertView.findViewById(R.id.btn_card_more);
                row.check = convertView.findViewById(R.id.img_check_badge);
                row.cover = convertView.findViewById(R.id.img_doc_preview);
                convertView.setTag(row);
                convertView.setOnClickListener(v -> {
                    Row held = (Row) v.getTag();
                    int pos = positionOf(held.boundId);
                    if (pos >= 0) onRowTapped(pos);
                });
                if (row.more != null) {
                    final View rowView = convertView;
                    row.more.setOnClickListener(v -> {
                        Row held = (Row) rowView.getTag();
                        CanvasDocument current = documentById(held.boundId);
                        if (current != null) showDocumentActions(current);
                    });
                }
            } else {
                row = (Row) convertView.getTag();
            }

            CanvasDocument doc = getItem(position);
            android.widget.ImageView cover = row.cover;
            TextView txtTag = row.tag;
            TextView txtTitle = row.title;
            TextView txtTime = row.time;
            TextView txtSize = row.size;
            ImageButton btnMore = row.more;
            android.widget.ImageView checkBadge = row.check;
            DocumentPresentation presentation = presentations.get(doc.getId());
            if (presentation == null) {
                presentation = new DocumentPresentation(doc, isDarkTheme(), CanvasPrefs.scriptsAllowed(MainActivity.this));
                presentations.put(doc.getId(), presentation);
            }
            String key = presentation.previewKey;
            com.nous.codecanvas.data.DocumentKind coverKind = presentation.kind;
            row.boundId = doc.getId();
            if (cover != null) {
                cover.setTag(key);
                bindThumbnail(cover, key, coverKind, doc);
            }

            boolean compactCard = parent == gridDocuments && !largeLibraryText();
            if (txtTitle != null) txtTitle.setText(doc.getTitle());
            if (txtTag != null) {
                String label = coverKind.label();
                if (compactCard && coverKind == com.nous.codecanvas.data.DocumentKind.MARKDOWN) {
                    txtTag.setText("MD");
                    txtTag.setContentDescription(label);
                } else {
                    txtTag.setText(label);
                    txtTag.setContentDescription(null);
                }
                KindStyle.applyChip(txtTag, coverKind);
            }
            if (txtSize != null) {
                String details = ListMeta.rowDetail(presentation.sizeText,
                        System.currentTimeMillis(), doc.getUpdatedAt());
                // A grid card prioritizes the name and size; the action sheet and list show dates.
                txtSize.setText(compactCard ? presentation.sizeText : details);
                txtSize.setContentDescription(compactCard ? details : null);
            }
            if (txtTime != null) txtTime.setText("");

            // Selection chrome: badge + row tint; type cover stays under the badge.
            boolean selected = selectionMode && selectedIds.contains(doc.getId());
            if (checkBadge != null) {
                checkBadge.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
                checkBadge.setSelected(selected);
            }
            convertView.setBackgroundResource(selected
                    ? R.drawable.bg_row_selected
                    : (parent == gridDocuments ? R.drawable.bg_card : R.drawable.bg_list_row));
            convertView.setLongClickable(true);
            if (btnMore != null) {
                btnMore.setContentDescription(getString(R.string.library_file_actions, doc.getTitle()));
                // The cover badge replaces the menu at the same position, never over the labels.
                btnMore.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
            }

            return convertView;
        }

        private CanvasDocument documentById(String id) {
            int pos = positionOf(id);
            return pos >= 0 ? filteredDocuments.get(pos) : null;
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
    /** The bitmap is already the whole picture, letterboxed. Fit it; do not crop again. */
    private void showThumbnailTop(android.widget.ImageView cover, android.graphics.Bitmap bitmap) {
        cover.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        cover.setImageBitmap(bitmap);
    }

    private void showPlaceholder(android.widget.ImageView cover, com.nous.codecanvas.data.DocumentKind kind) {
        cover.animate().cancel();
        cover.setAlpha(1f);
        cover.setTag(TAG_THUMB_BOUND, null);
        cover.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        cover.setBackgroundResource(R.drawable.bg_thumbnail);
        cover.setImageBitmap(KindPlaceholder.bitmap(this, kind));
        cover.setContentDescription(null);
    }

    /** After a fling, fill the tiles that were left as placeholders. */
    private void loadVisibleThumbs() {
        if (isFinishing() || isDestroyed() || backfiller == null
                || listScrollState != android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                || !CanvasPrefs.thumbnailsEnabled(this)) return;
        bindVisible(listDocuments, R.id.img_doc_preview);
        bindVisible(gridDocuments, R.id.img_doc_preview);
        bindVisible(drawerList, R.id.drawer_item_thumb);
    }

    private void bindVisible(android.widget.AbsListView list, int imageId) {
        if (list == null || list.getVisibility() != View.VISIBLE) return;
        int first = list.getFirstVisiblePosition();
        List<CanvasDocument> visible = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            int position = first + i;
            if (position < 0 || position >= filteredDocuments.size()) continue;
            android.widget.ImageView cover = list.getChildAt(i).findViewById(imageId);
            if (cover == null) continue;
            CanvasDocument doc = filteredDocuments.get(position);
            DocumentPresentation presentation = presentations.get(doc.getId());
            if (presentation == null) continue;
            bindThumbnail(cover, presentation.previewKey, presentation.kind, doc);
            visible.add(doc);
        }
        // Older files outside the initial warm-up still get a frame when they come on screen.
        backfiller.enqueue(visible);
    }

    /** Paint only the tiles that are on screen. Rebinding the whole list was a scroll hitch. */
    private void refreshVisibleThumbnails() {
        paintVisible(listDocuments, R.id.img_doc_preview);
        paintVisible(gridDocuments, R.id.img_doc_preview);
        paintVisible(drawerList, R.id.drawer_item_thumb);
    }

    private void paintVisible(android.widget.AbsListView list, int imageId) {
        if (list == null || !CanvasPrefs.thumbnailsEnabled(MainActivity.this)) return;
        for (int i = 0; i < list.getChildCount(); i++) {
            android.view.View row = list.getChildAt(i);
            android.widget.ImageView cover = row.findViewById(imageId);
            if (cover == null) continue;
            Object key = cover.getTag();
            if (!(key instanceof String)) continue;
            android.graphics.Bitmap bitmap = ThumbnailBackfiller.memoryGet((String) key);
            if (bitmap != null) paintThumbnail(cover, (String) key, bitmap, false);
        }
    }

    private static void tuneList(android.widget.AbsListView list) {
        if (list == null) return;
        list.setScrollingCacheEnabled(false);
        list.setAnimationCacheEnabled(false);
        list.setPersistentDrawingCache(0);
    }

    private static boolean isPixelKind(com.nous.codecanvas.data.DocumentKind kind) {
        return kind == com.nous.codecanvas.data.DocumentKind.IMAGE
                || kind == com.nous.codecanvas.data.DocumentKind.GIF
                || kind == com.nous.codecanvas.data.DocumentKind.PDF;
    }

    /** Decode no larger than the card. A full-screen bitmap in the list is what made flings hitch. */
    private android.graphics.Bitmap decodeThumbFile(java.io.File file) {
        if (file == null || !file.isFile()) return null;
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = 1;
        int maxW = PreviewThumbnailCache.CARD_W;
        int maxH = PreviewThumbnailCache.CARD_H;
        while (opts.inSampleSize < 16
                && (bounds.outWidth / opts.inSampleSize > maxW
                || bounds.outHeight / opts.inSampleSize > maxH)) {
            opts.inSampleSize *= 2;
        }
        return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    /** First page of a picture or PDF, straight from the stored bytes. */
    private android.graphics.Bitmap decodeStoredBlob(CanvasDocument doc) {
        java.io.File file = com.nous.codecanvas.data.FileImporter.blobFile(this, doc.getId());
        if (file == null || !file.isFile()) return null;
        if (doc.kind() == com.nous.codecanvas.data.DocumentKind.PDF) {
            try {
                return PdfPageRenderer.render(file, 0, 480).bitmap;
            } catch (Exception e) {
                return null;
            }
        }
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = EditorActivity.sampleSizeFor(bounds.outWidth, bounds.outHeight);
        return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private void bindThumbnail(android.widget.ImageView cover, String key,
                               com.nous.codecanvas.data.DocumentKind kind, CanvasDocument doc) {
        if (!CanvasPrefs.thumbnailsEnabled(MainActivity.this)) {
            showPlaceholder(cover, kind);
            return;
        }
        android.graphics.Bitmap cached = ThumbnailBackfiller.memoryGet(key);
        if (cached != null) {
            paintThumbnail(cover, key, cached, false);
            return;
        }
        android.graphics.drawable.Drawable shown = cover.getDrawable();
        boolean alreadyShowingThis = key.equals(cover.getTag(TAG_THUMB_BOUND))
                && shown instanceof android.graphics.drawable.BitmapDrawable
                && !((android.graphics.drawable.BitmapDrawable) shown).getBitmap().isRecycled();
        if (!alreadyShowingThis) showPlaceholder(cover, kind);
        // A fling must not decode bitmaps or post results back onto the scrolling frame.
        if (listScrollState != android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE) return;
        thumbnailExecutor.execute(() -> {
            android.graphics.Bitmap bitmap = isPixelKind(kind)
                    ? decodeStoredBlob(doc)
                    : decodeThumbFile(PreviewThumbnailCache.file(getApplicationContext(), key));
            // A photo or PDF page can be mostly paper and still be the real file.
            // Web captures that never painted are discarded so the coloured tile stays.
            if (bitmap != null && !isPixelKind(kind) && PreviewThumbnailCache.lacksContent(bitmap)) {
                bitmap.recycle();
                PreviewThumbnailCache.file(getApplicationContext(), key).delete();
                bitmap = null;
            }
            if (bitmap != null && !isPixelKind(kind)) ThumbnailBackfiller.memoryPutContent(key, bitmap);
            final android.graphics.Bitmap loaded = bitmap;
            final boolean fadeIn = !alreadyShowingThis;
            runOnUiThread(() -> applyThumbnail(cover, key, loaded, fadeIn));
        });
    }

    /** A queued result must not paint a reused row or revive a disabled thumbnail. */
    private void applyThumbnail(android.widget.ImageView cover, String key,
                                android.graphics.Bitmap bitmap, boolean fadeIn) {
        if (!key.equals(cover.getTag()) || bitmap == null || bitmap.isRecycled()
                || !CanvasPrefs.thumbnailsEnabled(this)) return;
        paintThumbnail(cover, key, bitmap, fadeIn);
    }

    private void paintThumbnail(android.widget.ImageView cover, String key,
                                android.graphics.Bitmap bitmap, boolean fadeIn) {
        cover.animate().cancel();
        showThumbnailTop(cover, bitmap);
        cover.setContentDescription("作品预览缩略图");
        cover.setTag(TAG_THUMB_BOUND, key);
        cover.setAlpha(fadeIn ? 0f : 1f);
        if (fadeIn) {
            cover.animate().alpha(1f).setDuration(THUMB_FADE_MS)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    /** Share the document's real bytes: an image or PDF must not be re-encoded as text. */
    private void shareDocument(CanvasDocument doc) {
        if (doc == null) return;
        final String docTitle = doc.getTitle();
        new Thread(() -> {
            try {
                byte[] payload = com.nous.codecanvas.util.SourceWriter.payloadBytes(
                        this, doc, doc.getContent());
                String authority = getPackageName() + com.nous.codecanvas.provider.CanvasFileProvider.AUTHORITY_SUFFIX;
                com.nous.codecanvas.util.ShareExporter.Staged staged =
                        com.nous.codecanvas.util.ShareExporter.stageBytes(this, authority, docTitle, payload);
                runOnUiThread(() -> startActivity(
                        com.nous.codecanvas.util.ShareExporter.chooserIntent(this, staged, "分享")));
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
    public void offerClipboardForTest() {
        maybeOfferClipboardBanner();
        if (clipboardResumed && hasWindowFocus() && CanvasPrefs.clipboardAutoDetect(this)) {
            searchHandler.removeCallbacks(pendingClipOffer);
            readClipboardOffer();
        }
    }

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
        listScrollState = android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
        if (backfiller != null) backfiller.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        clipboardResumed = false;
        stopClipboardOffers();
        importUiReady = false;
        importUiDestroying = true;
        if (importError != null) importError.dismiss();
        if (backfiller != null) backfiller.stop();
        if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
        if (activeSheet != null && activeSheet.isShowing()) activeSheet.dismiss();
        super.onDestroy();
        searchHandler.removeCallbacksAndMessages(null);
        thumbnailExecutor.shutdown();
        clipboardReadExecutor.shutdownNow();
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
