package com.nous.codecanvas.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.nous.codecanvas.R;
import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.provider.CanvasFileProvider;
import com.nous.codecanvas.util.FileUtils;
import com.nous.codecanvas.util.RenderKind;
import com.nous.codecanvas.util.RenderKindDetector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final int REQ_IMPORT_SAF = 1001;

    private final java.util.concurrent.ExecutorService thumbnailExecutor = java.util.concurrent.Executors.newFixedThreadPool(2);
    private DocumentRepository repository;
    private AlertDialog activeDialog;
    private ListView listDocuments;
    private View viewEmpty;
    private EditText editSearch;
    private Button btnQuickPaste;
    private ImageButton btnNew;
    private ImageButton btnImport;

    private final List<CanvasDocument> allDocuments = new ArrayList<>();
    private final List<CanvasDocument> filteredDocuments = new ArrayList<>();
    private DocumentAdapter adapter;
    private final java.util.Map<String, DocumentPresentation> presentations = new java.util.HashMap<>();

    /** Derived metadata is prepared off-main once per reload, not on every scroll bind. */
    private static final class DocumentPresentation {
        final String previewKey;
        final String sizeText;
        final RenderKind kind;
        DocumentPresentation(CanvasDocument doc, boolean dark) {
            String content=doc.getContent();
            previewKey=com.nous.codecanvas.util.PreviewKey.forDocument(doc.getId(),content,dark);
            kind=RenderKindDetector.detect(doc.getTitle(),content);
            int bytes=content.getBytes(StandardCharsets.UTF_8).length;
            String size=bytes<1024 ? bytes+" B" : String.format(Locale.getDefault(),"%.1f KB",bytes/1024.0f);
            int lines=content.isEmpty() ? 0 : content.split("\r\n|\r|\n",-1).length;
            sizeText=size+" · "+lines+" 行";
        }
    }

    private java.util.Map<String,DocumentPresentation> preparePresentations(List<CanvasDocument> docs, boolean dark) {
        java.util.Map<String,DocumentPresentation> result=new java.util.HashMap<>();
        for(CanvasDocument doc:docs) result.put(doc.getId(),new DocumentPresentation(doc,dark));
        return result;
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
        // Seed default sample documents if cold start on new device
        try {
            repository.seedDefaultTemplatesIfEmpty();
        } catch (Exception e) {
            e.printStackTrace();
        }

        initViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDocuments();
    }

    private void initViews() {
        listDocuments = findViewById(R.id.list_documents);
        viewEmpty = findViewById(R.id.view_empty);
        editSearch = findViewById(R.id.edit_search);
        btnQuickPaste = findViewById(R.id.btn_quick_paste_preview);
        btnNew = findViewById(R.id.btn_new);
        btnImport = findViewById(R.id.btn_import);

        adapter = new DocumentAdapter();
        listDocuments.setAdapter(adapter);

        listDocuments.setOnItemClickListener((parent, view, position, id) -> {
            CanvasDocument doc = filteredDocuments.get(position);
            openEditor(doc.getId(), true);
        });
    }

    private void setupListeners() {
        btnQuickPaste.setOnClickListener(v -> performQuickPasteAndPreview());
        btnNew.setOnClickListener(v -> showNewDocumentDialog());
        btnImport.setOnClickListener(v -> startSafImport());

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDocuments(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadDocuments() {
        final boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new Thread(() -> {
            try {
                List<CanvasDocument> docs = repository.getAllDocuments();
                java.util.Map<String,DocumentPresentation> prepared=preparePresentations(docs,dark);
                runOnUiThread(() -> {
                    if(isDestroyed() || isFinishing()) return;
                    presentations.clear();
                    presentations.putAll(prepared);
                    allDocuments.clear();
                    allDocuments.addAll(docs);
                    filterDocuments(editSearch.getText().toString());
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "加载文档失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void filterDocuments(String query) {
        filteredDocuments.clear();
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) {
            filteredDocuments.addAll(allDocuments);
        } else {
            for (CanvasDocument doc : allDocuments) {
                if (doc.getTitle().toLowerCase(Locale.ROOT).contains(q) ||
                    doc.getContent().toLowerCase(Locale.ROOT).contains(q)) {
                    filteredDocuments.add(doc);
                }
            }
        }
        adapter.notifyDataSetChanged();
        viewEmpty.setVisibility(filteredDocuments.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openEditor(String docId, boolean startPreview) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_DOC_ID, docId);
        intent.putExtra(EditorActivity.EXTRA_START_PREVIEW, startPreview);
        startActivity(intent);
    }

    private void performQuickPasteAndPreview() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "剪贴板为空，请先复制代码", Toast.LENGTH_SHORT).show();
            return;
        }

        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence rawText = clip.getItemAt(0).coerceToText(this);
        if (rawText == null || rawText.length() == 0) {
            Toast.makeText(this, "剪贴板内容为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rawText.length() > 2 * 1024 * 1024) { // 2MB Cap
            Toast.makeText(this, "剪贴板代码过大 (超过 2MB 限制)", Toast.LENGTH_LONG).show();
            return;
        }

        final String rawString = rawText.toString();
        java.util.List<com.nous.codecanvas.util.AiCodeExtractor.CodeBlock> blocks =
                com.nous.codecanvas.util.AiCodeExtractor.extract(rawString);

        if (blocks.size() > 1) {
            // Multi-block picker dialog
            String[] items = new String[blocks.size() + 1];
            for (int i = 0; i < blocks.size(); i++) {
                com.nous.codecanvas.util.AiCodeExtractor.CodeBlock b = blocks.get(i);
                String label = (b.language.isEmpty() ? "代码块 " + (i + 1) : b.language.toUpperCase(Locale.ROOT) + " 块 " + (i + 1));
                String snippet = b.code.length() > 30 ? b.code.substring(0, 30).replace("\n", " ") + "..." : b.code.replace("\n", " ");
                items[i] = label + " (" + snippet + ")";
            }
            items[blocks.size()] = "完整原始剪贴板文本";

            activeDialog = new AlertDialog.Builder(this)
                    .setTitle("检测到多段代码块，请选择预览项")
                    .setItems(items, (dialog, which) -> {
                        if (which < blocks.size()) {
                            createAndLaunchQuickPaste(blocks.get(which).code, blocks.get(which).language);
                        } else {
                            createAndLaunchQuickPaste(rawString, "");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            String code = blocks.isEmpty() ? rawString : blocks.get(0).code;
            String lang = blocks.isEmpty() ? "" : blocks.get(0).language;
            createAndLaunchQuickPaste(code, lang);
        }
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
        activeDialog=dialog;
        dialog.show();
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
        }
    }

    private void importFromUri(Uri uri) {
        new Thread(() -> {
            try {
                String fileName = "imported.txt";
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            String name = cursor.getString(nameIndex);
                            if (name != null && !name.trim().isEmpty()) {
                                fileName = name;
                            }
                        }
                    }
                    cursor.close();
                }

                fileName = FileUtils.sanitizeFileName(fileName);

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                long totalBytes = 0;
                long MAX_IMPORT_SIZE = 2 * 1024 * 1024; // 2MB
                try (InputStream is = getContentResolver().openInputStream(uri)) {
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
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private class DocumentAdapter extends BaseAdapter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        @Override
        public int getCount() {
            return filteredDocuments.size();
        }

        @Override
        public CanvasDocument getItem(int position) {
            return filteredDocuments.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_document_card, parent, false);
            }

            CanvasDocument doc = getItem(position);
            TextView txtTag = convertView.findViewById(R.id.txt_doc_tag);
            TextView txtTitle = convertView.findViewById(R.id.txt_doc_title);
            TextView txtTime = convertView.findViewById(R.id.txt_doc_time);
            TextView txtSnippet = convertView.findViewById(R.id.txt_doc_snippet);
            TextView txtSize = convertView.findViewById(R.id.txt_doc_size);
            View contentContainer = convertView.findViewById(R.id.card_content_container);
            ImageButton btnShare = convertView.findViewById(R.id.btn_card_share);
            ImageButton btnDelete = convertView.findViewById(R.id.btn_card_delete);

            android.widget.ImageView cover = convertView.findViewById(R.id.img_doc_preview);
            DocumentPresentation presentation=presentations.get(doc.getId());
            if(presentation==null) throw new IllegalStateException("Missing prepared document metadata");
            String key=presentation.previewKey;
            cover.setTag(key);
            cover.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            RenderKind coverKind = presentation.kind;
            cover.setImageResource(coverKind==RenderKind.HTML ? R.drawable.cover_web : coverKind==RenderKind.SVG ? R.drawable.cover_vector : R.drawable.cover_document);
            cover.setContentDescription("类型封面；打开预览后显示作品缩略图");
            thumbnailExecutor.execute(() -> {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(PreviewThumbnailCache.file(getApplicationContext(),key).getAbsolutePath());
                runOnUiThread(() -> {
                    if(key.equals(cover.getTag()) && bitmap!=null){ cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP); cover.setImageBitmap(bitmap); cover.setContentDescription("作品预览缩略图"); }
                    else if(bitmap!=null) bitmap.recycle();
                });
            });
            txtTitle.setText(doc.getTitle());
            txtTime.setText(dateFormat.format(new Date(doc.getUpdatedAt())));

            // Type summary instead of raw code snippets
            TextView txtSummary = convertView.findViewById(R.id.txt_doc_summary);
            RenderKind kind = presentation.kind;
            txtTag.setText(kind.name());

            if (txtSummary != null) {
                if (kind == RenderKind.HTML) {
                    txtSummary.setText("网页 / HTML 画布 · 点击预览");
                } else if (kind == RenderKind.SVG) {
                    txtSummary.setText("SVG 矢量图 · 点击预览");
                } else if (kind == RenderKind.XML) {
                    txtSummary.setText("XML 结构文档 · 点击校验");
                } else {
                    txtSummary.setText("代码画布 · 点击查看");
                }
            }

            txtSize.setText(presentation.sizeText);

            convertView.setOnClickListener(v -> openEditor(doc.getId(), true));
            if (contentContainer != null) {
                contentContainer.setOnClickListener(v -> openEditor(doc.getId(), true));
            }

            btnShare.setOnClickListener(v -> shareDocument(doc));
            btnDelete.setOnClickListener(v -> confirmDeleteDocument(doc));

            return convertView;
        }
    }

    private void shareDocument(CanvasDocument doc) {
        if (doc == null) return;
        final String docTitle = doc.getTitle();
        final String docContent = doc.getContent();
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
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmDeleteDocument(CanvasDocument doc) {
        activeDialog = new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定删除画布「" + doc.getTitle() + "」吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    new Thread(() -> {
                        try {
                            repository.deleteDocument(doc.getId());
                            runOnUiThread(this::loadDocuments);
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public AlertDialog getActiveDialogForTest() { return activeDialog; }

    @Override protected void onDestroy() {
        if(activeDialog!=null && activeDialog.isShowing()) activeDialog.dismiss();
        super.onDestroy();
        thumbnailExecutor.shutdown();
    }

    public void loadDocumentsForTest() {
        try {
            allDocuments.clear();
            allDocuments.addAll(repository.getAllDocuments());
            boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
            presentations.clear(); presentations.putAll(preparePresentations(allDocuments,dark));
            filterDocuments(editSearch != null ? editSearch.getText().toString() : "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
