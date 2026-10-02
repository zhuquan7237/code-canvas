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
import com.nous.codecanvas.util.PreviewHtml;
import com.nous.codecanvas.util.PreviewKey;
import com.nous.codecanvas.util.RenderKind;
import com.nous.codecanvas.util.RenderKindDetector;

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

    private final Activity activity;
    private final Deque<CanvasDocument> queue = new ArrayDeque<>();
    private final Runnable onThumbnailStored;
    private WebView web;
    private boolean running;

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
            if (hasThumbnail(doc)) continue;
            queue.addLast(doc);
            addAnything = true;
            taken++;
        }
        if (addAnything && !running) startNext();
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
        if (!f.isFile()) return false;
        Bitmap existing = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
        if (existing == null) return false;
        boolean blank = PreviewThumbnailCache.looksBlank(existing);
        existing.recycle();
        if (blank) {
            f.delete();
            return false;
        }
        return true;
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
        CanvasDocument doc = queue.pollFirst();
        if (doc == null) {
            running = false;
            return;
        }
        running = true;
        if (web == null && !createWebView()) {
            running = false;
            return;
        }
        final String key = darkKeyFor(doc);
        final RenderKind kind = RenderKindDetector.detect(doc.getTitle(), doc.getContent());
        final boolean dark = isDark();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Give the page its first paint before asking for pixels.
                view.postVisualStateCallback(nextRequestId(), new WebView.VisualStateCallback() {
                    @Override
                    public void onComplete(long id) {
                        if (view != web) return;
                        Bitmap shot = PreviewThumbnailCache.capture(view);
                        if (shot != null) {
                            PreviewThumbnailCache.store(activity.getApplicationContext(), key, shot);
                            if (onThumbnailStored != null) onThumbnailStored.run();
                        }
                        startNext();
                    }
                });
            }
        });
        web.loadDataWithBaseURL("https://codecanvas.invalid/gallery/" + Math.abs(key.hashCode()) + "/",
                PreviewHtml.forDocument(doc.getContent(), kind, dark), "text/html", "UTF-8", null);
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
            web.setVisibility(View.INVISIBLE);
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

    /** Documents with no usable capture, for the caller to decide about. */
    static List<CanvasDocument> missingThumbnails(Activity activity, List<CanvasDocument> docs) {
        List<CanvasDocument> missing = new ArrayList<>();
        for (CanvasDocument doc : docs) {
            boolean dark = (activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            String key = PreviewKey.forDocument(doc.getId(), doc.getContent(), dark);
            if (!PreviewThumbnailCache.file(activity.getApplicationContext(), key).isFile()) {
                missing.add(doc);
            }
        }
        return missing;
    }
}
