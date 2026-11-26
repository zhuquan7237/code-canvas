package com.nous.codecanvas.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.data.DocumentKind;
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
     * Only content-checked bitmaps enter this cache. They must not be modified after insertion,
     * so readers can reuse that decision without scanning pixels on every bind or refresh.
     */
    private static final class MemoryThumbnail {
        final Bitmap bitmap;
        final int byteCount;

        MemoryThumbnail(Bitmap bitmap) {
            this.bitmap = bitmap;
            // LruCache also asks for size on removal, after a bitmap may have been recycled.
            this.byteCount = bitmap.getByteCount();
        }
    }

    private static final android.util.LruCache<String, MemoryThumbnail> MEMORY =
            new android.util.LruCache<String, MemoryThumbnail>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, MemoryThumbnail value) {
                    return value.byteCount;
                }
            };

    static Bitmap memoryGet(String key) {
        synchronized (MEMORY) {
            MemoryThumbnail entry = MEMORY.get(key);
            if (entry != null && entry.bitmap.isRecycled()) {
                MEMORY.remove(key);
                return null;
            }
            return entry == null ? null : entry.bitmap;
        }
    }

    static boolean memoryPut(String key, Bitmap bitmap) {
        if (key == null || bitmap == null || bitmap.isRecycled()
                || PreviewThumbnailCache.lacksContent(bitmap)) return false;
        memoryPutContent(key, bitmap);
        return true;
    }

    /** The caller has already checked lacksContent on this bitmap (or its identical copy). */
    static void memoryPutContent(String key, Bitmap bitmap) {
        if (key != null && bitmap != null && !bitmap.isRecycled()) {
            MEMORY.put(key, new MemoryThumbnail(bitmap));
        }
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
    /** The document whose capture is in flight, so a scroll can put it back on the queue. */
    private CanvasDocument inflight;

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
            if (!canThumbnail(doc) || hasThumbnail(doc)
                    || (inflight != null && doc.getId().equals(inflight.getId()))
                    || queue.stream().anyMatch(queued -> doc.getId().equals(queued.getId()))) continue;
            queue.addLast(doc);
            addAnything = true;
            taken++;
        }
        if (addAnything && !running) startNext();
    }

    void setPaused(boolean value) {
        if (value) {
            paused = true;
            if (inflight != null) {
                queue.addFirst(inflight);
                inflight = null;
            }
            // A WebView left in the window is what makes the list stutter while it scrolls.
            releaseWeb();
            running = false;
            return;
        }
        if (!paused) return;
        paused = false;
        if (!running && !queue.isEmpty()) startNext();
    }

    void stop() {
        running = false;
        paused = true;
        inflight = null;
        queue.clear();
        releaseWeb();
    }

    private boolean hasThumbnail(CanvasDocument doc) {
        String key = darkKeyFor(doc);
        if (memoryGet(key) != null) return true;
        java.io.File f = PreviewThumbnailCache.file(activity.getApplicationContext(), key);
        // Decoding the PNG here used to happen on the UI thread for every row.
        return f.isFile() && f.length() > 32;
    }

    /**
     * The same key the editor stores under. That key is built from the raw source plus the theme,
     * not from the rendered HTML, so the editor's own capture and this one agree instead of
     * writing two different files for the same document.
     */
    private String darkKeyFor(CanvasDocument doc) {
        return PreviewKey.forThumbnail(doc.getId(), doc.kind().name(), doc.getContent(), isDark(), CanvasPrefs.scriptsAllowed(activity));
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
            releaseWeb();
            return;
        }
        running = true;
        inflight = doc;
        final String key = darkKeyFor(doc);
        final boolean dark = isDark();
        if (!canThumbnail(doc)) {
            startNext();
            return;
        }
        if (doc.kind() == com.nous.codecanvas.data.DocumentKind.TEXT
                || doc.kind() == com.nous.codecanvas.data.DocumentKind.CODE) {
            releaseWeb();
            final String text = doc.getContent();
            tiles.execute(() -> {
                Bitmap tile = TextTile.draw(text, dark, PreviewThumbnailCache.CARD_W, PreviewThumbnailCache.CARD_H);
                Bitmap shown = tile.copy(Bitmap.Config.ARGB_8888, false);
                if (!memoryPut(key, shown) && shown != null) shown.recycle();
                PreviewThumbnailCache.store(activity.getApplicationContext(), key, tile);
                activity.runOnUiThread(() -> {
                    inflight = null;
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
        sizeWebView();
        PreviewPage.Request request = new PreviewPage.Request();
        request.kind = doc.kind().name();
        request.content = doc.getContent();
        request.fileName = doc.getTitle();
        request.dark = dark;
        request.allowScripts = CanvasPrefs.scriptsAllowed(activity);
        final String page = PreviewPage.build(request);
        final long ticket = nextRequestId();
        final DocumentKind kind = doc.kind();
        final boolean bundledEngine = kind == DocumentKind.MARKDOWN || kind == DocumentKind.MERMAID
                || kind == DocumentKind.SVG;
        final boolean scripts = bundledEngine || (kind == DocumentKind.HTML && request.allowScripts);
        web.getSettings().setJavaScriptEnabled(scripts);
        web.getSettings().setDomStorageEnabled(kind == DocumentKind.HTML && scripts);
        web.getSettings().setLoadWithOverviewMode(kind == DocumentKind.HTML);
        final String origin = "https://gallery-" + PreviewKey.forDocument(doc.getId(), "", false).substring(0, 32)
                + ".codecanvas.invalid/" + ticket;
        web.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, android.webkit.WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                return com.nous.codecanvas.util.PreviewAssets.respond(
                        activity, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // Art can render but a background thumbnail never follows navigation.
                return request.isForMainFrame() || !"blob".equals(request.getUrl().getScheme());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (view == web && ticket == requestId) {
                    releaseWeb();
                    finish(ticket);
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!origin.equals(url)) return;
                captureWhenReady(view, key, kind, scripts, ticket, 0);
            }
        });
        WebView view = web;
        view.loadDataWithBaseURL(origin, page, "text/html", "UTF-8", null);
        // Bound failed/slow pages too: one broken thumbnail must not block the whole list.
        view.postDelayed(() -> {
            if (!current(view, ticket)) return;
            releaseWeb();
            finish(ticket);
        }, 6500);
    }

    private boolean current(WebView view, long ticket) {
        return view == web && !paused && ticket == requestId;
    }

    private void finish(long ticket) {
        if (ticket != requestId) return;
        nextRequestId(); // callbacks for this capture are no longer eligible
        inflight = null;
        startNext();
    }

    private static final String BOUNDS = """
            (()=>{
              let node=document.querySelector('#cc-mermaid svg');
              if(!node && document.body){
                const children=[...document.body.children].filter(e=>!['SCRIPT','STYLE','TEMPLATE','NOSCRIPT'].includes(e.tagName));
                const only=children.length===1&&children[0];
                if(only&&((only.localName==='svg'&&only.hasAttribute('viewBox'))||only.localName==='canvas'))node=only;
              }
              let r=window.CCViewport ? window.CCViewport.bounds() :
                    node ? node.getBoundingClientRect() : null;
              if(!r)return null;
              return [r.x,r.y,r.w??r.width,r.h??r.height,
                      visualViewport.width,visualViewport.height];
            })()
            """;

    private void captureWhenReady(WebView view, String key, DocumentKind kind,
                                  boolean scripts, long ticket, int attempt) {
        view.postDelayed(() -> {
            if (!current(view, ticket)) return;
            if (!scripts) {
                captureFrame(view, key, kind, null, false, ticket, attempt);
                return;
            }
            view.evaluateJavascript(BOUNDS, value -> {
                if (!current(view, ticket)) return;
                view.postVisualStateCallback(ticket, new WebView.VisualStateCallback() {
                    @Override public void onComplete(long id) {
                        if (current(view, ticket)) {
                            captureFrame(view, key, kind, value, true, ticket, attempt);
                        }
                    }
                });
            });
        }, attempt == 0 ? 350 : 450);
    }

    private void captureFrame(WebView view, String key, DocumentKind kind, String value,
                              boolean scripts, long ticket, int attempt) {
        boolean drawing = kind == DocumentKind.SVG || kind == DocumentKind.MERMAID;
        Bitmap shot = null, cropped = null, framed = null;
        float[] rect = null;
        if (value != null && !"null".equals(value)) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(value);
                rect = new float[6];
                for (int i = 0; i < 6; i++) rect[i] = (float) arr.getDouble(i);
            } catch (Exception ignored) {
                rect = null;
            }
        }
        if (!drawing || rect != null) {
            shot = PreviewThumbnailCache.capture(view);
            cropped = PreviewThumbnailCache.cropViewportRect(shot, rect);
            framed = drawing || rect != null
                    ? PreviewThumbnailCache.letterbox(cropped)
                    : PreviewThumbnailCache.topSlice(cropped);
        }
        if (cropped != null && cropped != shot && cropped != framed) cropped.recycle();
        if (shot != null && shot != framed) shot.recycle();
        if (framed != null && !PreviewThumbnailCache.lacksContent(framed)) {
            Bitmap copy = framed.copy(Bitmap.Config.ARGB_8888, false);
            memoryPutContent(key, copy);
            // PNG compression must not delay a home screen frame.
            Bitmap result = framed;
            tiles.execute(() -> PreviewThumbnailCache.store(activity.getApplicationContext(), key, result));
            if (onThumbnailStored != null) onThumbnailStored.run();
            finish(ticket);
            return;
        }
        if (framed != null) framed.recycle();
        if (attempt >= 8) {
            finish(ticket);
        } else {
            captureWhenReady(view, key, kind, scripts, ticket, attempt + 1);
        }
    }

    /**
     * Use a phone viewport in CSS units for all web thumbnails. A square/landscape screenshot
     * feeds different media queries from the editor and can show a different artwork altogether.
     */
    private void sizeWebView() {
        if (web == null) return;
        android.util.DisplayMetrics display = activity.getResources().getDisplayMetrics();
        int w = Math.max(1, display.widthPixels - Math.round(16 * display.density));
        int h = Math.max(1, display.heightPixels - Math.round(210 * display.density));
        web.setLayoutParams(new FrameLayout.LayoutParams(w, h));
        int ws = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
        int hs = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        web.measure(ws, hs);
        web.layout(0, 0, w, h);
    }

    private void releaseWeb() {
        if (web == null) return;
        WebView dying = web;
        web = null;
        try {
            dying.stopLoading();
            if (dying.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) dying.getParent()).removeView(dying);
            }
            dying.destroy();
        } catch (Exception ignored) {
        }
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
            web.setLayerType(View.LAYER_TYPE_NONE, null);
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
            String key = PreviewKey.forThumbnail(doc.getId(), doc.kind().name(), doc.getContent(), dark, CanvasPrefs.scriptsAllowed(activity));
            java.io.File file = PreviewThumbnailCache.file(activity.getApplicationContext(), key);
            if (!file.isFile() || file.length() <= 32) missing.add(doc);
        }
        return missing;
    }
}
