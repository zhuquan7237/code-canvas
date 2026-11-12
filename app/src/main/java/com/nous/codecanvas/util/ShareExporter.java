package com.nous.codecanvas.util;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stages a document for sharing and hands back a URI that actually resolves.
 *
 * <p>Both activities used to write {@code shares/<title>} and then build the URI by string
 * concatenation. Two bugs fell out of that: a title containing {@code #} (legal, and produced by
 * the rename sanitizer) turned the rest of the name into a URI fragment so the receiver opened
 * nothing, and two documents sharing a title overwrote each other in the cache — the receiver
 * could end up with a different document than the sender confirmed.</p>
 *
 * <p>So: one unique directory per share, a name that is safe as both a file and a URI segment,
 * and {@link Uri.Builder} instead of concatenation.</p>
 */
public final class ShareExporter {

    /** Old share directories are pruned, but never the one just created. */
    private static final long MAX_AGE_MILLIS = 60L * 60L * 1000L;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private ShareExporter() {
    }

    /** Everything an ACTION_SEND caller needs. */
    public static final class Staged {
        public final Uri uri;
        public final String mimeType;
        public final File file;

        Staged(Uri uri, String mimeType, File file) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.file = file;
        }
    }

    /**
     * Write {@code source} to a fresh cache file and return its content URI.
     *
     * @param authority the provider authority ({@code <package>.fileprovider})
     */
    public static Staged stage(Context context, String authority, String displayName, String source)
            throws IOException {
        return stageBytes(context, authority, displayName, source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Stage raw bytes. Images and PDFs go through here so what the receiver gets is exactly what
     * the file was — a text-encoded copy would corrupt them.
     */
    public static Staged stageBytes(Context context, String authority, String displayName, byte[] bytes)
            throws IOException {
        File cacheDir = context.getCacheDir();
        File sharesRoot = new File(cacheDir, "shares");
        if (!sharesRoot.exists() && !sharesRoot.mkdirs()) {
            throw new IOException("无法创建分享缓存目录");
        }
        prune(sharesRoot, null);

        // A unique directory per share is what stops two documents with the same title from
        // overwriting each other while a receiver is still reading the first one.
        String token = Long.toString(System.currentTimeMillis(), 36) + "-" + SEQUENCE.incrementAndGet();
        File dir = new File(sharesRoot, token);
        if (!dir.mkdirs()) {
            throw new IOException("无法创建分享目录");
        }

        String safeName = shareSafeName(displayName);
        File target = new File(dir, safeName);
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(bytes);
            fos.flush();
        }

        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(authority)
                .appendPath(token)
                .appendPath(safeName)
                .build();

        prune(sharesRoot, dir);
        return new Staged(uri, mimeTypeFor(safeName), target);
    }

    /** Drop share directories older than the retention window, except the one in use. */
    private static void prune(File sharesRoot, File keep) {
        File[] children = sharesRoot.listFiles();
        if (children == null) return;
        long cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS;
        for (File child : children) {
            if (keep != null && child.equals(keep)) continue;
            if (child.isDirectory() && child.lastModified() < cutoff) {
                File[] files = child.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                child.delete();
            }
        }
    }

    /**
     * A name that survives being a file name and a URI path segment. {@code #} and {@code %} are
     * not URI-hostile once encoded, but they are exactly the characters that broke the old
     * concatenated URIs and confuse receivers, so they never reach a share.
     */
    static String shareSafeName(String displayName) {
        String name = FileUtils.sanitizeFileName(displayName);
        name = name.replace('#', '_').replace('%', '_');
        name = name.replaceAll("\\s+", " ").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "canvas.txt";
        }
        // A very long name is awkward on the receiving side and can exceed path limits.
        if (name.length() > 96) {
            String ext = FileUtils.getExtension(name);
            String base = name.substring(0, 96 - (ext.isEmpty() ? 0 : ext.length() + 1));
            name = ext.isEmpty() ? base : base + "." + ext;
        }
        return name;
    }

    public static String mimeTypeFor(String fileName) {
        String ext = FileUtils.getExtension(fileName);
        if (!ext.isEmpty()) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
            if (mime != null) return mime;
        }
        return "text/plain";
    }

    /** Share several staged files at once. A single file uses the one-item chooser. */
    public static Intent chooserIntent(Context context, java.util.List<Staged> staged, String chooserTitle) {
        if (staged == null || staged.isEmpty()) {
            throw new IllegalArgumentException("nothing to share");
        }
        if (staged.size() == 1) {
            return chooserIntent(context, staged.get(0), chooserTitle);
        }
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
        String mime = staged.get(0).mimeType;
        boolean mixed = false;
        for (Staged item : staged) {
            uris.add(item.uri);
            if (item.mimeType != null && !item.mimeType.equals(mime)) mixed = true;
        }
        send.setType(mixed ? "*/*" : mime);
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip = ClipData.newUri(context.getContentResolver(), "分享", uris.get(0));
        for (int i = 1; i < uris.size(); i++) {
            clip.addItem(new ClipData.Item(uris.get(i)));
        }
        send.setClipData(clip);
        return Intent.createChooser(send, chooserTitle);
    }

    /** Build the send intent, with the read grant attached to the URI itself. */
    public static Intent chooserIntent(Context context, Staged staged, String chooserTitle) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(staged.mimeType);
        send.putExtra(Intent.EXTRA_STREAM, staged.uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Some receivers only honour the URI grant when it also travels in the clip data.
        //
        // The resolver argument is NOT optional: ClipData.newUri calls
        // resolver.getType(uri) to fill in the clip description, so passing null threw a
        // NullPointerException on the UI thread and the app died the instant you tapped 分享.
        // That was the reported "一点分享就卡退".
        ClipData clip = ClipData.newUri(context.getContentResolver(), staged.file.getName(), staged.uri);
        send.setClipData(clip);
        return Intent.createChooser(send, chooserTitle);
    }
}
