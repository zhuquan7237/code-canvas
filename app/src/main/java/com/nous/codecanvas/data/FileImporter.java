package com.nous.codecanvas.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.TextCodec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Reads a file's bytes once, then hands out a document that knows what it is.
 *
 * <p>The rule this class exists to enforce: <b>bytes first, decoding second</b>. Every import keeps
 * the original payload on disk under {@code files/blobs/<id>}, so an image or PDF can be exported
 * again with a byte-identical hash, and a text file remembers the charset and line ending it was
 * read with instead of silently rewriting it as UTF-8 with LF.</p>
 */
public final class FileImporter {

    /** Hard ceiling for a text file we are willing to put in an EditText. */
    public static final long MAX_TEXT_BYTES = 8L * 1024L * 1024L;
    /** Hard ceiling for a binary payload we are willing to copy into app storage. */
    public static final long MAX_BLOB_BYTES = 64L * 1024L * 1024L;
    /** How many leading bytes are enough to sniff magic numbers. */
    private static final int SNIFF_BYTES = 8192;

    private FileImporter() {
    }

    /** Everything needed to build a document, and to explain a refusal. */
    public static final class Imported {
        public final CanvasDocument document;
        public final DocumentKind kind;
        public final byte[] bytes;

        Imported(CanvasDocument document, DocumentKind kind, byte[] bytes) {
            this.document = document;
            this.kind = kind;
            this.bytes = bytes;
        }
    }

    public static Imported importFrom(Context context, Uri uri) throws IOException {
        String name = displayName(context, uri);
        String mime = mimeType(context, uri);

        byte[] head = new byte[0];
        byte[] all;
        try (InputStream in = open(context, uri)) {
            if (in == null) throw new IOException("无法读取这个文件");
            head = readAtMost(in, SNIFF_BYTES);
            all = head;
            if (head.length == SNIFF_BYTES) {
                // Only now read the rest: the sniff already told us whether this is binary.
                ByteArrayOutputStream rest = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                long total = head.length;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_BLOB_BYTES) throw new IOException("文件超过 64MB 上限，暂不支持");
                    rest.write(buffer, 0, n);
                }
                byte[] tail = rest.toByteArray();
                all = new byte[head.length + tail.length];
                System.arraycopy(head, 0, all, 0, head.length);
                System.arraycopy(tail, 0, all, head.length, tail.length);
            }
        }

        DocumentKind kind = FileKindDetector.detect(name, mime, head);
        if (kind == DocumentKind.BINARY) {
            throw new IOException("这是二进制文件（不是文本、图片或 PDF），本阶段不打开");
        }

        CanvasDocument doc = new CanvasDocument(UUID.randomUUID().toString(), name, "", System.currentTimeMillis());
        doc.setSourceUri(uri.toString());
        doc.setMimeType(mime);
        doc.setKindName(kind.name());
        doc.setByteSize(all.length);

        if (kind.editable()) {
            if (all.length > MAX_TEXT_BYTES) {
                throw new IOException("文本超过 8MB，编辑器打开会很慢，暂不支持");
            }
            TextCodec.Decoded decoded = TextCodec.decode(all);
            doc.setContent(decoded.text);
            doc.setEncoding(decoded.encoding);
            doc.setLineEnding(decoded.lineEnding);
            doc.setBlob(false);
        } else {
            doc.setBlob(true);
            doc.setContent("");
        }
        return new Imported(doc, kind, all);
    }

    /** Persist the original bytes so the document survives a cache wipe and can be exported. */
    public static File writeBlob(Context context, String documentId, byte[] bytes) throws IOException {
        File dir = blobDir(context);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建缓存目录");
        File target = new File(dir, documentId);
        File temp = new File(dir, documentId + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(bytes);
            out.flush();
        }
        if (target.exists() && !target.delete()) throw new IOException("无法替换旧的原始文件");
        if (!temp.renameTo(target)) throw new IOException("无法保存原始文件");
        return target;
    }

    public static File blobFile(Context context, String documentId) {
        return new File(blobDir(context), documentId);
    }

    public static boolean hasBlob(Context context, String documentId) {
        File f = blobFile(context, documentId);
        return f.isFile() && f.length() > 0;
    }

    public static byte[] readBlob(Context context, String documentId) throws IOException {
        File f = blobFile(context, documentId);
        if (!f.isFile()) throw new IOException("原始文件已不在本地缓存里");
        try (InputStream in = new FileInputStream(f)) {
            return TextCodec.readAll(in);
        }
    }

    private static File blobDir(Context context) {
        return new File(context.getFilesDir(), "blobs");
    }

    /** Write bytes to the location the document came from. Returns false when not permitted. */
    public static boolean writeBackToSource(Context context, CanvasDocument doc, byte[] bytes) {
        String uriString = doc.getSourceUri();
        if (uriString == null || uriString.isEmpty()) return false;
        Uri uri = Uri.parse(uriString);
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) return false;
            out.write(bytes);
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean canWriteSource(Context context, CanvasDocument doc) {
        String uriString = doc.getSourceUri();
        if (uriString == null || uriString.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(uriString);
            // Documents opened with a read grant only cannot be written back; ask the provider.
            try (OutputStream probe = context.getContentResolver().openOutputStream(uri, "wt")) {
                return probe != null;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static String displayName(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) name = value;
                }
            }
        } catch (Exception ignored) {
        }
        if (name == null) {
            String last = uri.getLastPathSegment();
            if (last != null && !last.trim().isEmpty()) {
                int slash = last.lastIndexOf('/');
                name = slash >= 0 ? last.substring(slash + 1) : last;
            }
        }
        if (name == null) name = "imported.txt";
        return com.nous.codecanvas.util.FileUtils.sanitizeFileName(name);
    }

    public static String mimeType(Context context, Uri uri) {
        try {
            String type = context.getContentResolver().getType(uri);
            if (type != null && !type.isEmpty()) return type;
        } catch (Exception ignored) {
        }
        String ext = FileKindDetector.extensionOf(displayName(context, uri));
        String guessed = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return guessed != null ? guessed : "application/octet-stream";
    }

    /**
     * Open the document's bytes.
     *
     * <p>A {@code file://} URI is not readable directly on Android 10+: scoped storage forbids an
     * app from opening {@code /sdcard/...} by path, and the attempt fails with
     * {@code EACCES (Permission denied)}. That is exactly what happened when another app handed a
     * file over as {@code file:///sdcard/Download/x.md} — the import failed, the toast was covered
     * by the screen returning to the list, and the file simply "would not open".</p>
     *
     * <p>So a {@code file} URI is resolved through the media/document providers first, which is the
     * supported route, and only falls back to a direct read when the path really is readable (an
     * app-private file, or a device with legacy storage).</p>
     */
    private static InputStream open(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            File direct = new File(uri.getPath());
            // A path this process can already read is the file the user named. Looking it up by
            // display name first imported whichever media row happened to share that name.
            if (direct.isFile() && direct.canRead()) {
                return new FileInputStream(direct);
            }
            InputStream viaProvider = openFileUriThroughProvider(context, uri, direct);
            if (viaProvider != null) return viaProvider;
            try {
                return new FileInputStream(uri.getPath());
            } catch (IOException e) {
                throw new IOException("无法读取这个文件（系统不允许按路径访问）。"
                        + "请在应用里用「导入」重新选择它。", e);
            }
        }
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("无法读取这个文件");
        return in;
    }

    /**
     * Resolve a {@code file://} path through the document providers, or null when none match.
     *
     * <p>Several indexed files can share a display name. A single hit is opened; several hits are
     * opened only when exactly one of them has the same byte length as the path. Anything still
     * ambiguous is refused, so the importer cannot silently open a different {@code report.csv}.</p>
     */
    private static InputStream openFileUriThroughProvider(Context context, Uri uri, File direct) {
        String path = uri.getPath();
        if (path == null) return null;
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (fileName.isEmpty()) return null;
        Uri collections = android.provider.MediaStore.Files.getContentUri("external");
        String[] projection = {
                android.provider.MediaStore.Files.FileColumns._ID,
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                android.provider.MediaStore.Files.FileColumns.SIZE };
        java.util.List<Uri> hits = new java.util.ArrayList<>();
        java.util.List<Long> sizes = new java.util.ArrayList<>();
        try (android.database.Cursor cursor = context.getContentResolver().query(
                collections, projection,
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME + "=?",
                new String[]{fileName}, null)) {
            if (cursor == null) return null;
            int sizeColumn = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.SIZE);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                hits.add(android.content.ContentUris.withAppendedId(collections, id));
                sizes.add(sizeColumn >= 0 ? cursor.getLong(sizeColumn) : -1L);
            }
        } catch (Exception ignored) {
            return null;
        }
        Uri chosen = null;
        if (hits.size() == 1) {
            chosen = hits.get(0);
        } else if (hits.size() > 1 && direct != null && direct.exists()) {
            long expected = direct.length();
            for (int i = 0; i < hits.size(); i++) {
                if (sizes.get(i) == expected) {
                    if (chosen != null) return null;
                    chosen = hits.get(i);
                }
            }
        }
        if (chosen == null) return null;
        try {
            return context.getContentResolver().openInputStream(chosen);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] readAtMost(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(limit);
        byte[] buffer = new byte[4096];
        int n;
        while (out.size() < limit && (n = in.read(buffer, 0, Math.min(buffer.length, limit - out.size()))) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
