package com.nous.codecanvas.data;

import android.content.Context;
import com.nous.codecanvas.model.CanvasDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DocumentRepository {
    private static final String DOCUMENTS_FILE = "code_canvas_docs.json";
    private final File storageFile;

    public DocumentRepository(Context context) {
        this.storageFile = new File(context.getFilesDir(), DOCUMENTS_FILE);
    }

    public DocumentRepository(File dir) {
        this.storageFile = new File(dir, DOCUMENTS_FILE);
    }

    public synchronized List<CanvasDocument> getAllDocuments() throws java.io.IOException {
        List<CanvasDocument> list = new ArrayList<>();
        if (!storageFile.exists()) {
            return list;
        }
        try (FileInputStream fis = new FileInputStream(storageFile)) {
            byte[] data = new byte[(int) storageFile.length()];
            int read = fis.read(data);
            if (read > 0) {
                String jsonStr = new String(data, 0, read, StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    CanvasDocument doc = CanvasDocument.fromJsonObject(obj);
                    if (doc != null) {
                        list.add(doc);
                    }
                }
            }
        } catch (Exception e) {
            throw new java.io.IOException("Failed to read documents from " + storageFile.getAbsolutePath(), e);
        }
        return list;
    }

    public synchronized void saveAllDocuments(List<CanvasDocument> documents) throws java.io.IOException {
        try {
            JSONArray array = new JSONArray();
            if (documents != null) {
                for (CanvasDocument doc : documents) {
                    array.put(doc.toJsonObject());
                }
            }
            File tempFile = new File(storageFile.getParentFile(), storageFile.getName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(array.toString().getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            if (storageFile.exists()) {
                if (!storageFile.delete()) {
                    throw new java.io.IOException("Failed to remove old storage file before atomic rename");
                }
            }
            if (!tempFile.renameTo(storageFile)) {
                throw new java.io.IOException("Failed to rename temporary storage file to " + storageFile.getAbsolutePath());
            }
        } catch (Exception e) {
            if (e instanceof java.io.IOException) {
                throw (java.io.IOException) e;
            }
            throw new java.io.IOException("Failed to save documents to " + storageFile.getAbsolutePath(), e);
        }
    }

    public synchronized CanvasDocument findById(String id) throws java.io.IOException {
        if (id == null) return null;
        for (CanvasDocument doc : getAllDocuments()) {
            if (id.equals(doc.getId())) {
                return doc;
            }
        }
        return null;
    }

    public synchronized void saveDocument(CanvasDocument doc) throws java.io.IOException {
        if (doc == null) return;
        List<CanvasDocument> docs = getAllDocuments();
        boolean found = false;
        CanvasDocument docCopy = doc.snapshot();
        for (int i = 0; i < docs.size(); i++) {
            if (docs.get(i).getId().equals(docCopy.getId())) {
                docs.set(i, docCopy);
                found = true;
                break;
            }
        }
        if (!found) {
            docs.add(0, docCopy);
        }
        saveAllDocuments(docs);
    }

    public synchronized void deleteDocument(String id) throws java.io.IOException {
        List<CanvasDocument> docs = getAllDocuments();
        List<CanvasDocument> remaining = new ArrayList<>();
        for (CanvasDocument d : docs) {
            if (!d.getId().equals(id)) {
                remaining.add(d);
            }
        }
        saveAllDocuments(remaining);
    }

    public synchronized void seedDefaultTemplatesIfEmpty() throws java.io.IOException {
        List<CanvasDocument> current = getAllDocuments();
        if (!current.isEmpty()) {
            return;
        }

        List<CanvasDocument> seeds = new ArrayList<>();
        
        // 1. HTML5 Canvas & Interaction Demo
        String htmlCode = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <style>\n" +
                "    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; text-align: center; }\n" +
                "    .badge { display: inline-block; padding: 4px 12px; background: #0d9488; color: #fff; border-radius: 999px; font-size: 12px; font-weight: bold; margin-bottom: 12px; }\n" +
                "    h1 { font-size: 24px; margin-bottom: 8px; }\n" +
                "    p { color: #94a3b8; font-size: 14px; line-height: 1.6; max-width: 400px; margin: 0 auto 20px auto; }\n" +
                "    .btn { background: #14b8a6; color: #fff; border: none; padding: 10px 20px; border-radius: 8px; font-weight: 600; cursor: pointer; transition: transform 0.1s; }\n" +
                "    .btn:active { transform: scale(0.96); }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"badge\">CodeCanvas 示例</div>\n" +
                "  <h1>极简代码画布</h1>\n" +
                "  <p>纯原生高能效渲染器。支持直接粘贴、无缝切换预览，支持任意格式文件后缀。</p>\n" +
                "  <button class=\"btn\" onclick=\"alert('代码画布已准备就绪！')\">点击测试互动</button>\n" +
                "</body>\n" +
                "</html>";
        seeds.add(new CanvasDocument(UUID.randomUUID().toString(), "welcome.html", htmlCode, System.currentTimeMillis() - 3000));

        // 2. Beautiful SVG Vector Art
        String svgCode = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 300\" width=\"100%\" height=\"100%\">\n" +
                "  <defs>\n" +
                "    <linearGradient id=\"grad1\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\n" +
                "      <stop offset=\"0%\" style=\"stop-color:#0D9488;stop-opacity:1\" />\n" +
                "      <stop offset=\"100%\" style=\"stop-color:#0284C7;stop-opacity:1\" />\n" +
                "    </linearGradient>\n" +
                "    <filter id=\"shadow\" x=\"-10%\" y=\"-10%\" width=\"130%\" height=\"130%\">\n" +
                "      <feDropShadow dx=\"0\" dy=\"8\" stdDeviation=\"12\" flood-color=\"#0D9488\" flood-opacity=\"0.2\"/>\n" +
                "    </filter>\n" +
                "  </defs>\n" +
                "  <rect width=\"400\" height=\"300\" rx=\"16\" fill=\"#1E293B\"/>\n" +
                "  <circle cx=\"200\" cy=\"130\" r=\"65\" fill=\"url(#grad1)\" filter=\"url(#shadow)\"/>\n" +
                "  <path d=\"M175 110 L235 110 L205 160 Z\" fill=\"#FFFFFF\" opacity=\"0.9\"/>\n" +
                "  <text x=\"200\" y=\"230\" text-anchor=\"middle\" fill=\"#F8FAFC\" font-family=\"monospace\" font-size=\"18\" font-weight=\"bold\">SVG 矢量画布</text>\n" +
                "  <text x=\"200\" y=\"255\" text-anchor=\"middle\" fill=\"#64748B\" font-family=\"sans-serif\" font-size=\"13\">响应式自适应容器</text>\n" +
                "</svg>";
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
    }
}
