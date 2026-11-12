package com.nous.codecanvas.util;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the bundled preview libraries out of the APK.
 *
 * <p>Markdown, formulas and Mermaid have to work with the phone in flight mode, so the libraries
 * ship inside the app instead of being fetched from a CDN. They are delivered through a request
 * interceptor on a reserved host rather than {@code file://} URLs, because the preview WebView
 * deliberately runs with {@code allowFileAccess(false)} and {@code blockNetworkLoads(true)}: a
 * local document must not be able to reach the filesystem, and nothing may reach the network.</p>
 */
public final class PreviewAssets {

    /** Reserved host. Nothing resolves to it; every request is answered from assets. */
    public static final String HOST = "codecanvas.invalid";
    public static final String PREFIX = "/preview-assets/";

    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("js", "application/javascript");
        MIME.put("mjs", "application/javascript");
        MIME.put("css", "text/css");
        MIME.put("woff2", "font/woff2");
        MIME.put("woff", "font/woff");
        MIME.put("ttf", "font/ttf");
        MIME.put("json", "application/json");
        MIME.put("html", "text/html");
    }

    private PreviewAssets() {
    }

    /** Absolute URL for an asset path such as {@code marked.min.js}. */
    public static String url(String assetPath) {
        return "https://" + HOST + PREFIX + assetPath;
    }

    /** True when this request is one of ours, so the caller knows to intercept it. */
    public static boolean handles(String url) {
        return url != null && url.startsWith("https://" + HOST + PREFIX);
    }

    /**
     * @return the response for a bundled asset, or {@code null} when the path is not one of ours
     *         (which the WebView then treats as a normal — blocked — request).
     */
    public static WebResourceResponse respond(Context context, String url) {
        if (!handles(url)) return null;
        String path = url.substring(("https://" + HOST + PREFIX).length());
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        // No traversal, no absolute paths: only files we shipped.
        if (path.isEmpty() || path.contains("..") || path.startsWith("/")) return null;

        String assetName = "preview/" + path;
        String mime = mimeOf(path);
        try {
            InputStream in = context.getAssets().open(assetName);
            WebResourceResponse response = new WebResourceResponse(mime, "utf-8", in);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "max-age=86400");
            response.setResponseHeaders(headers);
            return response;
        } catch (IOException e) {
            return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(("missing asset: " + path).getBytes()));
        }
    }

    private static String mimeOf(String path) {
        int dot = path.lastIndexOf('.');
        String ext = dot >= 0 ? path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
        String mime = MIME.get(ext);
        return mime != null ? mime : "application/octet-stream";
    }
}
