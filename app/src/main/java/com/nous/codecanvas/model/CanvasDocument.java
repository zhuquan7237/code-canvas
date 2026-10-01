package com.nous.codecanvas.model;

import org.json.JSONException;
import org.json.JSONObject;

public class CanvasDocument {
    private String id;
    private String title;
    private String content;
    private long updatedAt;

    public CanvasDocument(String id, String title, String content, long updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content != null ? content : "";
        this.updatedAt = updatedAt;
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

    public JSONObject toJsonObject() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("content", content);
        obj.put("updatedAt", updatedAt);
        return obj;
    }

    public static CanvasDocument fromJsonObject(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
        String title = obj.optString("title", "untitled.txt");
        String content = obj.optString("content", "");
        long updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
        return new CanvasDocument(id, title, content, updatedAt);
    }

    public CanvasDocument snapshot() {
        return new CanvasDocument(this.id, this.title, this.content, this.updatedAt);
    }
}
