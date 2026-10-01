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

    private DocumentRepository repository;
    private ListView listDocuments;
    private View viewEmpty;
    private EditText editSearch;
    private ImageButton btnNew;
    private ImageButton btnImport;

    private final List<CanvasDocument> allDocuments = new ArrayList<>();
    private final List<CanvasDocument> filteredDocuments = new ArrayList<>();
    private DocumentAdapter adapter;

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
        btnNew = findViewById(R.id.btn_new);
        btnImport = findViewById(R.id.btn_import);

        adapter = new DocumentAdapter();
        listDocuments.setAdapter(adapter);

        listDocuments.setOnItemClickListener((parent, view, position, id) -> {
            CanvasDocument doc = filteredDocuments.get(position);
            openEditor(doc.getId());
        });
    }

    private void setupListeners() {
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
        new Thread(() -> {
            try {
                List<CanvasDocument> docs = repository.getAllDocuments();
                runOnUiThread(() -> {
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

    private void openEditor(String docId) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_DOC_ID, docId);
        startActivity(intent);
    }

    private void showNewDocumentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建画布文件");
        final EditText input = new EditText(this);
        input.setHint("输入文件名 (例如 demo.html, chart.svg, config.xml)");
        input.setText("untitled.html");
        input.setSelection("untitled.html".length());
        builder.setView(input);

        builder.setPositiveButton("创建", (dialog, which) -> {
            String title = FileUtils.sanitizeFileName(input.getText().toString());
            CanvasDocument newDoc = new CanvasDocument(UUID.randomUUID().toString(), title, "", System.currentTimeMillis());
            new Thread(() -> {
                try {
                    repository.saveDocument(newDoc);
                    runOnUiThread(() -> openEditor(newDoc.getId()));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
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
                    openEditor(importedDoc.getId());
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

            txtTitle.setText(doc.getTitle());
            txtTime.setText(dateFormat.format(new Date(doc.getUpdatedAt())));

            String content = doc.getContent();
            txtSnippet.setText(content.isEmpty() ? "(空白内容)" : content);

            // Render type badge
            RenderKind kind = RenderKindDetector.detect(doc.getTitle(), content);
            txtTag.setText(kind.name());

            // Size & Line stats
            int bytes = content.getBytes(StandardCharsets.UTF_8).length;
            String sizeText = bytes < 1024 ? bytes + " B" : String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0f);
            int lineCount = content.isEmpty() ? 0 : content.split("\r\n|\r|\n", -1).length;
            txtSize.setText(sizeText + " · " + lineCount + " 行");

            convertView.setOnClickListener(v -> openEditor(doc.getId()));
            if (contentContainer != null) {
                contentContainer.setOnClickListener(v -> openEditor(doc.getId()));
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
        new AlertDialog.Builder(this)
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
}
