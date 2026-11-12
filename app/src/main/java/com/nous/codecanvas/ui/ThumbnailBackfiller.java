package com.nous.codecanvas.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.CanvasPrefs;
import com.nous.codecanvas.util.PreviewKey;
import com.nous.codecanvas.util.PreviewPage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Renders documents that have no thumbnail yet, off-screen, one at a time.
 *
 * <p>Thumbnails used to exist only for documents the user had already opened in the preview, so a
 * fresh home screen showed nothing but file-type covers and the app looked like it had no
 * previews at all. This walks the list once and fills the gaps.</p>
 *
 * <p>Deliberately sequential on the UI thread: WebView work has to be, and a queue of one keeps
 * the cost invisible. It stops the moment the screen is left, and never overwrites an existing
 * capture.</p>
 */
final class ThumbnailBackfiller {

    /** How many documents one visit may render. The home screen is useful long before all of them. */
    private static final int MAX_PER_VISIT = 12;

    /**
     * Process-wide thumbnail memory cache. The home list used to decode every PNG from disk on
     * every bind — and, worse, paint the type cover first and swap the bitmap in a frame later,
     * which is the visible "flash" on every cold open. Binds now consult this cache first; a hit
     * lands the finished thumbnail in the same frame, and the cache is filled once per key.
     */
    private static final android.util.LruCache<String, Bitmap> MEMORY =
            new android.util.LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    static Bitmap memoryGet(String key) {
        return MEMORY.get(key);
    }

    static void memoryPut(String key, Bitmap bitmap) {
        if (key != null && bitmap != null && !bitmap.isRecycled()) MEMORY.put(key, bitmap);
    }

    private final Activity activity;
    private final Deque<CanvasDocument> queue = new ArrayDeque<>();
    private final Runnable onThumbnailStored;
    private final java.util.concurrent.ExecutorService tiles =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private WebView web;
    private boolean running;
    /** While the list is flinging, don't start another WebView capture. */
    private boolean paused;

    ThumbnailBackfiller(Activity activity, Runnable onThumbnailStored) {
        this.activity = activity;
        this.onThumbnailStored = onThumbnailStored;
    }

    void enqueue(List<CanvasDocument> documents) {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        // Generating thumbnails is exactly the work this switch is meant to stop. Skipping the
        // queue keeps the promise instead of quietly rendering in the background anyway.
        if (!CanvasPrefs.thumbnailsEnabled(activity)) return;
        boolean addAnything = false;
        int taken = 0;
        for (CanvasDocument doc : documents) {
            if (taken >= MAX_PER_VISIT) break;
            if (!canThumbnail(doc) || hasThumbnail(doc)) continue;
            queue.addLast(doc);
            addAnything = true;
            taken++;
        }
        if (addAnything && !running) startNext();
    }

    void setPaused(boolean value) {
        paused = value;
        if (!value && !running && !queue.isEmpty()) startNext();
    }

    void stop() {
        running = false;
        queue.clear();
        if (web != null) {
            try {
                web.stopLoading();
                web.destroy();
            } catch (Exception ignored) {
            }
            web = null;
        }
    }

    private boolean hasThumbnail(CanvasDocument doc) {
        java.io.File f = PreviewThumbnailCache.file(
                activity.getApplicationContext(), darkKeyFor(doc));
        // Decoding the PNG here used to happen on the UI thread for every row.
        return f.isFile() && f.length() > 32;
    }

    /**
     * The same key the editor stores under. That key is built from the raw source plus the theme,
     * not from the rendered HTML, so the editor's own capture and this one agree instead of
     * writing two different files for the same document.
     */
    private String darkKeyFor(CanvasDocument doc) {
        return PreviewKey.forDocument(doc.getId(), doc.getContent(), isDark());
    }

    private boolean isDark() {
        return (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private long requestId;

    private long nextRequestId() {
        return ++requestId;
    }

    private void startNext() {
        if (paused) {
            running = false;
            return;
        }
        CanvasDocument doc = queue.pollFirst();
        if (doc == null) {
            running = false;
            return;
        }
        running = true;
        final String key = darkKeyFor(doc);
        final boolean dark = isDark();
        if (!canThumbnail(doc)) {
            startNext();
            return;
        }
        if (doc.kind() == com.nous.codecanvas.data.DocumentKind.TEXT
                || doc.kind() == com.nous.codecanvas.data.DocumentKind.CODE) {
            final String text = doc.getContent();
            tiles.execute(() -> {
                Bitmap tile = TextTile.draw(text, dark, 480, 270);
                Bitmap shown = tile.copy(Bitmap.Config.ARGB_8888, false);
                memoryPut(key, shown);
                PreviewThumbnailCache.store(activity.getApplicationContext(), key, tile);
                activity.runOnUiThread(() -> {
                    if (onThumbnailStored != null) onThumbnailStored.run();
                    startNext();
                });
            });
            return;
        }
        if (web == null && !createWebView()) {
            running = false;
            return;
        }
        sizeWebView(doc);
        PreviewPage.Request request = new PreviewPage.Request();
        request.kind = doc.kind().name();
        request.content = doc.getContent();
        request.fileName = doc.getTitle();
        request.dark = dark;
        request.allowScripts = CanvasPrefs.scriptsAllowed(activity);
        final String page = PreviewPage.build(request);

        boolean bundledEngine = doc.kind() == com.nous.codecanvas.data.DocumentKind.MARKDOWN
                || doc.kind() == com.nous.codecanvas.data.DocumentKind.MERMAID;
        web.getSettings().setJavaScriptEnabled(bundledEngine || CanvasPrefs.scriptsAllowed(activity));
        web.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, android.webkit.WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                return com.nous.codecanvas.util.PreviewAssets.respond(
                        activity, request.getUrl().toString());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Markdown and Mermaid fill in after deferred scripts. The first paint is
                // an empty page; wait until there is ink, then give up and keep the tile.
                captureWhenReady(view, key, 0);
            }
        });
        web.loadDataWithBaseURL("https://codecanvas.invalid/gallery/" + Math.abs(key.hashCode()) + "/",
                page, "text/html", "UTF-8", null);
    }

    private void captureWhenReady(WebView view, String key, int attempt) {
        view.postDelayed(() -> {
            if (view != web) return;
            Bitmap shot = PreviewThumbnailCache.capture(view);
            Bitmap framed = PreviewThumbnailCache.cropToContent(shot);
            if (framed != null && framed != shot && shot != null) shot.recycle();
            shot = PreviewThumbnailCache.fitSlot(framed, 480);
            if (shot != null && shot != framed && framed != null) framed.recycle();
            boolean ready = shot != null;
            boolean last = attempt >= 14;
            if (ready) {
                Bitmap copy = shot.copy(shot.getConfig() != null
                        ? shot.getConfig() : android.graphics.Bitmap.Config.ARGB_8888, false);
                memoryPut(key, copy);
                PreviewThumbnailCache.store(activity.getApplicationContext(), key, shot);
                if (onThumbnailStored != null) onThumbnailStored.run();
                startNext();
                return;
            }
            if (shot != null) shot.recycle();
            if (last) {
                startNext();
            } else {
                captureWhenReady(view, key, attempt + 1);
            }
        }, attempt == 0 ? 400 : 700);
    }

    /**
     * SVG is captured at its own viewBox aspect, so a wide drawing fills the bitmap.
     * HTML is captured as the first phone screen, then cropped.
     */
    private void sizeWebView(CanvasDocument doc) {
        if (web == null) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int w = Math.round(360 * density);
        int h = Math.round(640 * density);
        if (doc.kind() == com.nous.codecanvas.data.DocumentKind.SVG) {
            com.nous.codecanvas.util.SvgGeometry.Box box =
                    com.nous.codecanvas.util.SvgGeometry.measure(doc.getContent());
            if (box != null && box.isValid() && box.aspect() > 0) {
                h = Math.max(Math.round(72 * density), Math.round(w / (float) box.aspect()));
                int cap = Math.round(720 * density);
                if (h > cap) {
                    h = cap;
                    w = Math.max(1, Math.round(h * (float) box.aspect()));
                }
            }
        }
        web.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1, w), Math.max(1, h)));
        int ws = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
        int hs = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        web.measure(ws, hs);
        web.layout(0, 0, w, h);
    }

    /** Off-screen but attached and laid out: a WebView that is not in the hierarchy will not paint. */
    @SuppressWarnings("deprecation")
    private boolean createWebView() {
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return false;
        try {
            web = new WebView(activity);
            int w = activity.getResources().getDisplayMetrics().widthPixels;
            int h = activity.getResources().getDisplayMetrics().heightPixels;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.max(1, w), Math.max(1, h));
            web.setLayoutParams(lp);
            web.setTranslationX(-30000f);
            // VISIBLE so deferred scripts actually paint. It sits off-screen at alpha 0.
            web.setVisibility(View.VISIBLE);
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            web.setBackgroundColor(Color.WHITE);
            WebSettings s = web.getSettings();
            // Same sandbox as the editor: no file/content access, no network, zoomed to page width.
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            s.setAllowFileAccessFromFileURLs(false);
            s.setAllowUniversalAccessFromFileURLs(false);
            s.setBlockNetworkLoads(true);
            s.setDomStorageEnabled(false);
            s.setDatabaseEnabled(false);
            // Same answer as the editor's switch; a thumbnail must not disagree with the preview.
            s.setJavaScriptEnabled(CanvasPrefs.scriptsAllowed(activity));
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            web.setAlpha(0f);
            root.addView(web);
            return true;
        } catch (Exception e) {
            web = null;
            return false;
        }
    }

    /** Visual previews worth a thumbnail. Images and PDFs stay on their type cover. */
    private static boolean canThumbnail(CanvasDocument doc) {
        if (doc == null || doc.isBlob()) return false;
        String content = doc.getContent();
        if (content == null || content.length() > 200_000) return false;
        switch (doc.kind()) {
            case HTML:
            case SVG:
            case MARKDOWN:
            case MERMAID:
            case JSON:
            case JSONL:
            case CSV:
            case YAML:
            case XML:
            case CODE:
            case TEXT:
                return true;
            default:
                return false;
        }
    }

    /** Documents with no usable capture, for the caller to decide about. */
    static List<CanvasDocument> missingThumbnails(Activity activity, List<CanvasDocument> docs) {
        List<CanvasDocument> missing = new ArrayList<>();
        for (CanvasDocument doc : docs) {
            boolean dark = (activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            String key = PreviewKey.forDocument(doc.getId(), doc.getContent(), dark);
            java.io.File file = PreviewThumbnailCache.file(activity.getApplicationContext(), key);
            if (!file.isFile() || file.length() <= 32) missing.add(doc);
        }
        return missing;
    }
}
