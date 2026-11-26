package com.nous.codecanvas.util;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.PersistableBundle;

/** Clipboard paste accepts actual text only, never opens a URI or follows an Intent. */
public final class CanvasClipboard {
    public static final int MAX_PASTE_CHARS = 2 * 1024 * 1024;
    private static final int MAX_OFFER_CHARS = 256 * 1024;
    // Keep compatibility with apps marking their clips before API 33's public constant.
    public static final String SENSITIVE = "android.content.extra.IS_SENSITIVE";

    private CanvasClipboard() {}

    public enum Status { TEXT, EMPTY, NOT_TEXT, TOO_LARGE, SENSITIVE, UNAVAILABLE }

    public static final class Read {
        public final Status status;
        public final String text;
        public final long token;

        private Read(Status status, String text, long token) {
            this.status = status;
            this.text = text;
            this.token = token;
        }

        public String message() {
            switch (status) {
                case NOT_TEXT: return "剪贴板里没有可粘贴的文本。文件请通过分享或导入打开。";
                case TOO_LARGE: return "剪贴板文本过长。请分段复制，或另存文件后导入。";
                case UNAVAILABLE: return "暂时无法读取剪贴板，请再试一次。";
                default: return "剪贴板没有内容，请先复制文本。";
            }
        }
    }

    private static Read status(Status status) { return new Read(status, null, Long.MIN_VALUE); }

    /** Invoke on a worker. Read metadata first on opt-in auto offers so sensitive clips aren't fetched. */
    public static Read read(Context context, boolean forOffer) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return status(Status.UNAVAILABLE);
            if (forOffer) {
                ClipDescription description = clipboard.getPrimaryClipDescription();
                if (description == null) return status(Status.EMPTY);
                if (sensitive(description)) return status(Status.SENSITIVE);
                if (!description.hasMimeType("text/*")) return status(Status.NOT_TEXT);
            }
            return fromClip(clipboard.getPrimaryClip(), forOffer);
        } catch (RuntimeException e) {
            return status(Status.UNAVAILABLE);
        }
    }

    public static Read fromClip(ClipData clip, boolean forOffer) {
        if (clip == null || clip.getItemCount() == 0) return status(Status.EMPTY);
        ClipDescription description = clip.getDescription();
        if (forOffer && sensitive(description)) return status(Status.SENSITIVE);
        CharSequence value = clip.getItemAt(0).getText();
        if (value == null) return status(Status.NOT_TEXT);
        if (value.length() == 0) return status(Status.EMPTY);
        if (value.length() > (forOffer ? MAX_OFFER_CHARS : MAX_PASTE_CHARS)) return status(Status.TOO_LARGE);
        String text = value.toString();
        long time = description == null ? 0 : description.getTimestamp();
        // Timestamp alone has only millisecond precision. Mix in the exact UTF-16 text so
        // an updated clipboard within the same millisecond does not reuse the offer token.
        long token = 0xcbf29ce484222325L ^ time;
        for (int i = 0; i < text.length(); i++) token = (token ^ text.charAt(i)) * 0x100000001b3L;
        return new Read(Status.TEXT, text, token == Long.MIN_VALUE ? Long.MIN_VALUE + 1 : token);
    }

    private static boolean sensitive(ClipDescription description) {
        PersistableBundle data = description == null ? null : description.getExtras();
        return data != null && data.getBoolean(SENSITIVE, false);
    }
}
