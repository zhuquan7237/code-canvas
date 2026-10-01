package com.nous.codecanvas.data;

import android.content.Context;
import com.nous.codecanvas.model.CanvasDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DocumentRepository {
    private static final String DOCUMENTS_FILE = "code_canvas_docs.json";
    // Bump the marker name when the built-in samples are redesigned: a fresh
    // install seeds the new ones, while existing users keep whatever they have
    // (the seeding path never overwrites a non-empty document list).
    private static final String SEED_MARKER_FILE = "code_canvas_seeded_v2.marker";
    private static final int MAX_DOCUMENT_FILE_SIZE = 50 * 1024 * 1024; // 50MB safety cap
    private static final Object GLOBAL_FILE_LOCK = new Object();

    private final File storageDir;
    private final File storageFile;
    private final File seedMarkerFile;

    public DocumentRepository(Context context) {
        this(context.getFilesDir());
    }

    public DocumentRepository(File dir) {
        this.storageDir = dir;
        this.storageFile = new File(dir, DOCUMENTS_FILE);
        this.seedMarkerFile = new File(dir, SEED_MARKER_FILE);
    }

    public List<CanvasDocument> getAllDocuments() throws java.io.IOException {
        synchronized (GLOBAL_FILE_LOCK) {
            List<CanvasDocument> list = new ArrayList<>();
            if (!storageFile.exists()) {
                return list;
            }
            long fileLength = storageFile.length();
            if (fileLength > MAX_DOCUMENT_FILE_SIZE) {
                throw new java.io.IOException("Documents file exceeds maximum safety size cap: " + fileLength);
            }

            try (FileInputStream fis = new FileInputStream(storageFile)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream((int) Math.max(32, fileLength));
                byte[] buffer = new byte[8192];
                int n;
                while ((n = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, n);
                    if (baos.size() > MAX_DOCUMENT_FILE_SIZE) {
                        throw new java.io.IOException("Documents file stream exceeds maximum safety size cap");
                    }
                }
                byte[] data = baos.toByteArray();
                if (data.length > 0) {
                    String jsonStr = new String(data, StandardCharsets.UTF_8).trim();
                    if (!jsonStr.isEmpty()) {
                        JSONArray array = new JSONArray(jsonStr);
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            CanvasDocument doc = CanvasDocument.fromJsonObject(obj);
                            if (doc != null) {
                                list.add(doc);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof java.io.IOException) {
                    throw (java.io.IOException) e;
                }
                throw new java.io.IOException("Failed to read documents from " + storageFile.getAbsolutePath(), e);
            }
            return list;
        }
    }

    public void saveAllDocuments(List<CanvasDocument> documents) throws java.io.IOException {
        synchronized (GLOBAL_FILE_LOCK) {
            try {
                JSONArray array = new JSONArray();
                if (documents != null) {
                    for (CanvasDocument doc : documents) {
                        array.put(doc.toJsonObject());
                    }
                }
                byte[] payload = array.toString().getBytes(StandardCharsets.UTF_8);
                if (payload.length > MAX_DOCUMENT_FILE_SIZE) {
                    throw new java.io.IOException("Serialized document payload exceeds safety size cap");
                }

                File parent = storageFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                File tempFile = File.createTempFile("code_canvas_tmp_", ".tmp", parent != null ? parent : storageDir);
                try {
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(payload);
                        fos.flush();
                        try {
                            fos.getFD().sync();
                        } catch (Exception ignored) {
                        }
                    }

                    // Atomic replace via java.nio.file.Files.move ATOMIC_MOVE with fallback to REPLACE_EXISTING
                    // Never delete before rename
                    try {
                        Files.move(tempFile.toPath(), storageFile.toPath(),
                                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tempFile.toPath(), storageFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            } catch (Exception e) {
                if (e instanceof java.io.IOException) {
                    throw (java.io.IOException) e;
                }
                throw new java.io.IOException("Failed to save documents to " + storageFile.getAbsolutePath(), e);
            }
        }
    }

    public CanvasDocument findById(String id) throws java.io.IOException {
        synchronized (GLOBAL_FILE_LOCK) {
            if (id == null) return null;
            for (CanvasDocument doc : getAllDocuments()) {
                if (id.equals(doc.getId())) {
                    return doc;
                }
            }
            return null;
        }
    }

    /** The document is not in the store: deleted elsewhere, or the id never existed. */
    public static class DocumentMissingException extends java.io.IOException {
        public DocumentMissingException(String id) {
            super("document not found: " + id);
        }
    }

    /**
     * A newer revision is already stored, so this write is obsolete. Dropping it is the whole
     * point: a save queued before a later edit must not win the race and silently revert content.
     */
    public static class StaleWriteException extends java.io.IOException {
        public StaleWriteException(String id, long expected, long actual) {
            super("stale write for " + id + ": expected revision " + expected + ", stored " + actual);
        }
    }

    /**
     * Update an existing document's content in place. Returns the revision now stored.
     *
     * <p>Two guarantees this method exists for: it <b>never creates</b> (a missing id means the
     * user deleted the file while a save was in flight, and recreating it would silently undo
     * their deletion), and it <b>refuses stale writes</b> (if the store already moved past
     * {@code expectedRevision}, an older queued save can no longer clobber the newer content).</p>
     */
    public long updateDocument(String id, String title, String content, long expectedRevision)
            throws java.io.IOException {
        synchronized (GLOBAL_FILE_LOCK) {
            if (id == null) {
                throw new DocumentMissingException("null");
            }
            List<CanvasDocument> docs = getAllDocuments();
            for (int i = 0; i < docs.size(); i++) {
                CanvasDocument stored = docs.get(i);
                if (!id.equals(stored.getId())) {
                    continue;
                }
                if (stored.getRevision() != expectedRevision) {
                    throw new StaleWriteException(id, expectedRevision, stored.getRevision());
                }
                CanvasDocument next = new CanvasDocument(id, title, content,
                        System.currentTimeMillis(), stored.getRevision() + 1);
                docs.set(i, next);
                saveAllDocuments(docs);
                return next.getRevision();
            }
            throw new DocumentMissingException(id);
        }
    }

    /**
     * Insert or update a document wholesale, returning the revision now in the store.
     * Used when creating a document (home screen new/import); the editor uses
     * {@link #updateDocument} so a stale save cannot clobber newer content.
     */
    public long saveDocument(CanvasDocument doc) throws java.io.IOException {
        synchronized (GLOBAL_FILE_LOCK) {
            if (doc == null) return -1L;
            List<CanvasDocument> docs = getAllDocuments();
            boolean found = false;
            CanvasDocument docCopy = doc.snapshot();
            for (int i = 0; i < docs.size(); i++) {
                if (docs.get(i).getId().equals(docCopy.getId())) {
                    // Reachable from the home screen (rename, import). Keep revisions monotonic so
                    // an editor holding an older revision is correctly treated as stale.
                    docCopy.setRevision(Math.max(docs.get(i).getRevision(), docCopy.getRevision()) + 1);
                    docs.set(i, docCopy);
                    found = true;
                    break;
                }
            }
            if (!found) {
                docCopy.setRevision(Math.max(1L, docCopy.getRevision()));
                docs.add(0, docCopy);
            }
            saveAllDocuments(docs);
            return docCopy.getRevision();
        }
    }

    public void deleteDocument(String id) throws java.io.IOException {
        synchronized (GLOBAL_FILE_LOCK) {
            List<CanvasDocument> docs = getAllDocuments();
            List<CanvasDocument> remaining = new ArrayList<>();
            for (CanvasDocument d : docs) {
                if (!d.getId().equals(id)) {
                    remaining.add(d);
                }
            }
            saveAllDocuments(remaining);
        }
    }

    public void seedDefaultTemplatesIfEmpty() throws java.io.IOException {
        synchronized (GLOBAL_FILE_LOCK) {
            // Check persistent seed marker: seed once ever
            if (seedMarkerFile.exists()) {
                return;
            }

            List<CanvasDocument> current = getAllDocuments();
            if (!current.isEmpty()) {
                // If existing documents already present, mark as seeded and do not overwrite
                createSeedMarkerSafely();
                return;
            }

            List<CanvasDocument> seeds = new ArrayList<>();

            // 1. HTML5 Canvas & Interaction Demo
            String htmlCode =                     "<!DOCTYPE html>\n" +
                    "<html lang=\"zh\">\n" +
                    "<head>\n" +
                    "  <meta charset=\"utf-8\">\n" +
                    "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "  <style>\n" +
                    "    :root { color-scheme: light dark; --ink:#172033; --muted:#526078; --line:#D2DAE6; --accent:#2456C6; --on:#FFFFFF; --chip:#EEF1F7; }\n" +
                    "    @media (prefers-color-scheme: dark) { :root { --ink:#EDF1F8; --muted:#A7B2C4; --line:#31405A; --accent:#7FA5FF; --on:#0B1424; --chip:#1B2536; } }\n" +
                    "    html, body { height:100%; }\n" +
                    "    body { margin:0; padding:24px 20px; box-sizing:border-box; display:flex; flex-direction:column;\n" +
                    "           align-items:center; justify-content:center; text-align:center; background:transparent;\n" +
                    "           color:var(--ink); font-family:-apple-system,'Noto Sans SC','PingFang SC',sans-serif; }\n" +
                    "    .chip { display:inline-block; padding:3px 10px; border-radius:999px; background:var(--chip);\n" +
                    "            color:var(--muted); font-size:11px; font-weight:600; letter-spacing:.08em; }\n" +
                    "    h1 { margin:14px 0 8px; font-size:22px; line-height:1.35; font-weight:700; }\n" +
                    "    p { margin:0 0 22px; max-width:18em; font-size:13px; line-height:1.6; color:var(--muted); }\n" +
                    "    button { font:inherit; font-size:14px; font-weight:600; padding:11px 22px; border-radius:10px;\n" +
                    "             border:none; background:var(--accent); color:var(--on); }\n" +
                    "    button:active { transform:translateY(1px); }\n" +
                    "    .foot { margin-top:16px; font-size:11px; color:var(--muted); }\n" +
                    "  </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "  <span class=\"chip\">CODE CANVAS</span>\n" +
                    "  <h1>极简代码画布</h1>\n" +
                    "  <p>粘贴代码，直接看渲染效果</p>\n" +
                    "  <button onclick=\"this.textContent='交互正常'\">点我测试交互</button>\n" +
                    "  <div class=\"foot\">脚本开关关闭时，这个按钮不会响应</div>\n" +
                    "</body>\n" +
                    "</html>\n";
            seeds.add(new CanvasDocument(UUID.randomUUID().toString(), "welcome.html", htmlCode, System.currentTimeMillis() - 3000));

            // 2. Beautiful SVG Vector Art
            String svgCode =                     "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\" width=\"100%\" height=\"100%\">\n" +
                    "  <defs>\n" +
                    "    <linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n" +
                    "      <stop offset=\"0\" stop-color=\"#2456C6\"/>\n" +
                    "      <stop offset=\"1\" stop-color=\"#7FA5FF\"/>\n" +
                    "    </linearGradient>\n" +
                    "  </defs>\n" +
                    "  <rect width=\"400\" height=\"300\" rx=\"16\" fill=\"#EEF1F7\"/>\n" +
                    "  <circle cx=\"200\" cy=\"124\" r=\"58\" fill=\"url(#g)\"/>\n" +
                    "  <path d=\"M178 106 L228 106 L203 150 Z\" fill=\"#FFFFFF\" opacity=\"0.92\"/>\n" +
                    "  <text x=\"200\" y=\"220\" text-anchor=\"middle\" fill=\"#172033\" font-family=\"sans-serif\" font-size=\"17\" font-weight=\"700\">SVG 矢量画布</text>\n" +
                    "  <text x=\"200\" y=\"245\" text-anchor=\"middle\" fill=\"#526078\" font-family=\"sans-serif\" font-size=\"12\">缩放不失真 · 自适应容器</text>\n" +
                    "</svg>\n";
            seeds.add(new CanvasDocument(UUID.randomUUID().toString(), "vector-art.svg", svgCode, System.currentTimeMillis() - 2000));

            // 3. XML Structure Demo
            String xmlCode = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<canvas-config version=\"1.0\">\n" +
                    "    <meta>\n" +
                    "        <name>代码画布配置文件</name>\n" +
                    "        <author>Nous Research</author>\n" +
                    "    </meta>\n" +
                    "    <editor-settings>\n" +
                    "        <theme>system</theme>\n" +
                    "        <font-family>monospace</font-family>\n" +
                    "        <syntax-highlight enabled=\"true\" debounced=\"true\"/>\n" +
                    "        <security-sandbox xxe-protection=\"enabled\" js-gated=\"true\"/>\n" +
                    "    </editor-settings>\n" +
                    "</canvas-config>";
            seeds.add(new CanvasDocument(UUID.randomUUID().toString(), "config.xml", xmlCode, System.currentTimeMillis() - 1000));

            saveAllDocuments(seeds);
            createSeedMarkerSafely();
        }
    }

    private void createSeedMarkerSafely() {
        try {
            if (!seedMarkerFile.exists()) {
                File parent = seedMarkerFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(seedMarkerFile)) {
                    fos.write("seeded=true\n".getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
        }
    }
}

