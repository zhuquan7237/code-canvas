package com.nous.codecanvas.util;

import android.content.Context;
import android.net.Uri;

import com.nous.codecanvas.data.FileImporter;
import com.nous.codecanvas.model.CanvasDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The one place that turns a document back into bytes.
 *
 * <p>Three rules, each of which the old code broke:</p>
 * <ul>
 *   <li>An unmodified image or PDF exports the <b>original bytes</b>, so its hash is unchanged.</li>
 *   <li>A text file is re-encoded with the charset and line ending it was read with, instead of
 *       always becoming UTF-8 with LF.</li>
 *   <li>Saving to the original location is attempted first and reported honestly when the provider
 *       refuses; it never silently becomes "saved into the app's private store".</li>
 * </ul>
 */
public final class SourceWriter {

    private SourceWriter() {
    }

    /** Where a save actually went, so the UI can say something true. */
    public enum Target { SOURCE, APP_STORE }

    /**
     * The exact bytes this document should produce.
     *
     * @param content the text currently in the editor (ignored for binary documents)
     */
    public static byte[] payloadBytes(Context context, CanvasDocument doc, String content) throws IOException {
        if (doc.isBlob() || !doc.kind().editable()) {
            return FileImporter.readBlob(context, doc.getId());
        }
        String text = content != null ? content : doc.getContent();
        String lineEnding = doc.getLineEnding();
        String encoding = doc.getEncoding();
        String normalized = TextCodec.normalizeLineEndings(text, lineEnding);
        if (encoding != null && !TextCodec.canEncode(normalized, encoding)) {
            // The user typed a character the original charset cannot represent. Writing it anyway
            // would silently replace it with '?', so fall back to UTF-8 and say so.
            return normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return TextCodec.encode(normalized, encoding);
    }

    /** True when the payload had to be re-encoded because the original charset could not hold it. */
    public static boolean needsEncodingUpgrade(CanvasDocument doc, String content) {
        if (doc.isBlob() || !doc.kind().editable()) return false;
        String encoding = doc.getEncoding();
        if (encoding == null) return false;
        String normalized = TextCodec.normalizeLineEndings(content, doc.getLineEnding());
        return !TextCodec.canEncode(normalized, encoding);
    }

    /** Write to the document's original location. Returns false when the provider refused. */
    public static boolean saveToSource(Context context, CanvasDocument doc, byte[] bytes) {
        return FileImporter.writeBackToSource(context, doc, bytes);
    }

    /** Write to a destination the user picked in the system file picker. */
    public static void writeToUri(Context context, Uri target, byte[] bytes) throws IOException {
        try (OutputStream out = context.getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) throw new IOException("无法写入所选位置");
            out.write(bytes);
            out.flush();
        }
    }

    /** Stage bytes in the share cache and return a content URI the receiver can actually open. */
    public static com.nous.codecanvas.util.ShareExporter.Staged stageForShare(
            Context context, String authority, String displayName, byte[] bytes) throws IOException {
        return ShareExporter.stageBytes(context, authority, displayName, bytes);
    }

    /** Keep an app-private copy of a binary payload so it survives a cache wipe. */
    public static void persistBlob(Context context, String documentId, byte[] bytes) throws IOException {
        FileImporter.writeBlob(context, documentId, bytes);
    }

    public static File blobFile(Context context, String documentId) {
        return FileImporter.blobFile(context, documentId);
    }

    public static boolean hasBlob(Context context, String documentId) {
        return FileImporter.hasBlob(context, documentId);
    }

    /** MIME type to hand to the share sheet and the file picker. */
    public static String mimeFor(CanvasDocument doc) {
        String recorded = doc.getMimeType();
        if (recorded != null && !recorded.isEmpty() && !recorded.equals("application/octet-stream")) {
            return recorded;
        }
        String ext = com.nous.codecanvas.data.FileKindDetector.extensionOf(doc.getTitle());
        String guessed = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return guessed != null ? guessed : "text/plain";
    }

    /** Write bytes to a plain file (used by the share staging and by tests). */
    public static void writeFile(File target, byte[] bytes) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录");
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(bytes);
            out.flush();
        }
    }
}
