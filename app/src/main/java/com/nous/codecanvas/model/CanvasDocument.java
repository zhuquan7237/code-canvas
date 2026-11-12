package com.nous.codecanvas.model;

import org.json.JSONException;
import org.json.JSONObject;

public class CanvasDocument {
    private String id;
    private String title;
    private String content;
    private long updatedAt;

    /**
     * Monotonic revision of the stored content. Every accepted write bumps it, and a write that
     * arrives carrying an older revision is refused, so a save queued before a newer edit can
     * never overwrite it. Files written before revisions existed load as 0.
     */
    private long revision;

    // --- file identity -------------------------------------------------------------------------

    /** Original location this document was opened from, or null for app-created documents. */
    private String sourceUri;
    /** MIME type reported by the provider, or guessed from the extension. */
    private String mimeType;
    /** {@link com.nous.codecanvas.data.DocumentKind} name; null means "derive it from the title". */
    private String kind;
    /** Charset the text was decoded from; null means UTF-8. */
    private String encoding;
    /** Dominant line terminator of the original file: "\n", "\r\n" or "\r". */
    private String lineEnding;
    /** True when the payload lives in a blob file instead of {@link #content} (images, PDFs). */
    private boolean blob;
    /** Size of the original bytes, when known. */
    private long byteSize;

    public CanvasDocument(String id, String title, String content, long updatedAt) {
        this(id, title, content, updatedAt, 0L);
    }

    public CanvasDocument(String id, String title, String content, long updatedAt, long revision) {
        this.id = id;
        this.title = title;
        this.content = content != null ? content : "";
        this.updatedAt = updatedAt;
        this.revision = revision;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getKindName() {
        return kind;
    }

    public void setKindName(String kind) {
        this.kind = kind;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getLineEnding() {
        return lineEnding;
    }

    public void setLineEnding(String lineEnding) {
        this.lineEnding = lineEnding;
    }

    public boolean isBlob() {
        return blob;
    }

    public void setBlob(boolean blob) {
        this.blob = blob;
    }

    public long getByteSize() {
        return byteSize;
    }

    public void setByteSize(long byteSize) {
        this.byteSize = byteSize;
    }

    /** The kind of file this is, derived from the stored name when nothing was recorded. */
    public com.nous.codecanvas.data.DocumentKind kind() {
        com.nous.codecanvas.data.DocumentKind recorded =
                com.nous.codecanvas.data.DocumentKind.fromName(kind);
        if (recorded != null) return recorded;
        com.nous.codecanvas.data.DocumentKind guessed =
                com.nous.codecanvas.data.FileKindDetector.fromExtension(
                        com.nous.codecanvas.data.FileKindDetector.extensionOf(title));
        if (guessed != null) return guessed;
        return com.nous.codecanvas.data.DocumentKind.UNKNOWN;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("content", content);
        obj.put("updatedAt", updatedAt);
        obj.put("revision", revision);
        if (sourceUri != null) obj.put("sourceUri", sourceUri);
        if (mimeType != null) obj.put("mimeType", mimeType);
        if (kind != null) obj.put("kind", kind);
        if (encoding != null) obj.put("encoding", encoding);
        if (lineEnding != null) obj.put("lineEnding", lineEnding);
        if (blob) obj.put("blob", true);
        if (byteSize > 0) obj.put("byteSize", byteSize);
        return obj;
    }

    public static CanvasDocument fromJsonObject(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
        String title = obj.optString("title", "untitled.txt");
        String content = obj.optString("content", "");
        long updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
        long revision = obj.optLong("revision", 0L);
        CanvasDocument doc = new CanvasDocument(id, title, content, updatedAt, revision);
        doc.sourceUri = emptyToNull(obj.optString("sourceUri", null));
        doc.mimeType = emptyToNull(obj.optString("mimeType", null));
        doc.kind = emptyToNull(obj.optString("kind", null));
        doc.encoding = emptyToNull(obj.optString("encoding", null));
        doc.lineEnding = emptyToNull(obj.optString("lineEnding", null));
        doc.blob = obj.optBoolean("blob", false);
        doc.byteSize = obj.optLong("byteSize", 0L);
        return doc;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public CanvasDocument snapshot() {
        CanvasDocument copy = new CanvasDocument(this.id, this.title, this.content, this.updatedAt, this.revision);
        copy.sourceUri = this.sourceUri;
        copy.mimeType = this.mimeType;
        copy.kind = this.kind;
        copy.encoding = this.encoding;
        copy.lineEnding = this.lineEnding;
        copy.blob = this.blob;
        copy.byteSize = this.byteSize;
        return copy;
    }
}
