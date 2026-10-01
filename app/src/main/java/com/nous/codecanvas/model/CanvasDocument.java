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

    public JSONObject toJsonObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("content", content);
        obj.put("updatedAt", updatedAt);
        obj.put("revision", revision);
        return obj;
    }

    public static CanvasDocument fromJsonObject(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
        String title = obj.optString("title", "untitled.txt");
        String content = obj.optString("content", "");
        long updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
        long revision = obj.optLong("revision", 0L);
        return new CanvasDocument(id, title, content, updatedAt, revision);
    }

    public CanvasDocument snapshot() {
        return new CanvasDocument(this.id, this.title, this.content, this.updatedAt, this.revision);
    }
}
