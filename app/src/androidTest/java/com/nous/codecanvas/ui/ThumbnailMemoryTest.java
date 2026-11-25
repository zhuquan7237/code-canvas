package com.nous.codecanvas.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import com.nous.codecanvas.data.DocumentKind;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.util.CanvasPrefs;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

/** Real bitmap admission and the shared grid/list/drawer binding path. */
public class ThumbnailMemoryTest extends InstrumentationTestCase {
    private MainActivity home;
    private boolean thumbnailsEnabled;
    private Method bind;
    private Field scrollState;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        thumbnailsEnabled = CanvasPrefs.thumbnailsEnabled(context);
        // Keep unrelated repository documents from starting captures during these focused tests.
        CanvasPrefs.setThumbnailsEnabled(context, false);
        home = (MainActivity) getInstrumentation().startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        bind = MainActivity.class.getDeclaredMethod("bindThumbnail", ImageView.class,
                String.class, DocumentKind.class, CanvasDocument.class);
        bind.setAccessible(true);
        scrollState = MainActivity.class.getDeclaredField("listScrollState");
        scrollState.setAccessible(true);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (home != null) getInstrumentation().runOnMainSync(home::finish);
            CanvasPrefs.setThumbnailsEnabled(getInstrumentation().getTargetContext(), thumbnailsEnabled);
        } finally {
            super.tearDown();
        }
    }

    public void testTextTileAdmissionIsReusedAcrossRepeatedBinds() {
        assertRepeatedBinds(false);
    }

    public void testAlreadyCheckedCaptureOrDecodeIsReusedAcrossRepeatedBinds() {
        assertRepeatedBinds(true);
    }

    private void assertRepeatedBinds(boolean alreadyChecked) {
        getInstrumentation().runOnMainSync(() -> {
            Bitmap bitmap = frame(Color.BLACK);
            String key = "memory-hit-" + System.nanoTime();
            try {
                if (alreadyChecked) {
                    assertFalse(PreviewThumbnailCache.lacksContent(bitmap));
                    ThumbnailBackfiller.memoryPutContent(key, bitmap);
                } else {
                    assertTrue(ThumbnailBackfiller.memoryPut(key, bitmap));
                }
                // Any attempt to schedule a disk decode now fails immediately, even while idle.
                Field executor = MainActivity.class.getDeclaredField("thumbnailExecutor");
                executor.setAccessible(true);
                ((ExecutorService) executor.get(home)).shutdown();
                CanvasPrefs.setThumbnailsEnabled(home, true);
                ImageView cover = cover(key);
                scrollState.setInt(home, AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
                bind(cover, key);
                assertSame(bitmap, displayed(cover));

                // A test-only sentinel: production cache entries are never mutated. Turning the
                // pixels white makes any repeated lacksContent scan visibly choose the fallback.
                bitmap.eraseColor(Color.WHITE);
                for (int i = 0; i < 20; i++) {
                    scrollState.setInt(home, i % 2 == 0
                            ? AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                            : AbsListView.OnScrollListener.SCROLL_STATE_FLING);
                    bind(cover, key);
                    assertSame("reuse the admission decision without scanning or decoding",
                            bitmap, displayed(cover));
                    assertSame(bitmap, ThumbnailBackfiller.memoryGet(key));
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                CanvasPrefs.setThumbnailsEnabled(home, false);
                bitmap.recycle();
                assertNull(ThumbnailBackfiller.memoryGet(key));
            }
        });
    }

    public void testBlankCaptureAndMissingEntryKeepTheTypePlaceholder() {
        getInstrumentation().runOnMainSync(() -> {
            CanvasPrefs.setThumbnailsEnabled(home, true);
            try {
                scrollState.setInt(home, AbsListView.OnScrollListener.SCROLL_STATE_FLING);
                assertPlaceholder("missing-" + System.nanoTime());
                for (int color : new int[]{Color.WHITE, Color.rgb(252, 252, 252),
                        Color.rgb(248, 250, 252)}) {
                    String key = "blank-" + System.nanoTime();
                    View view = new View(home);
                    view.layout(0, 0, 390, 844);
                    view.setBackgroundColor(color);
                    Bitmap shot = PreviewThumbnailCache.capture(view);
                    Bitmap blank = PreviewThumbnailCache.topSlice(shot);
                    if (shot != blank) shot.recycle();
                    try {
                        PreviewThumbnailCache.store(home, key, blank);
                        assertFalse("blank captures must not replace the type cover on disk",
                                PreviewThumbnailCache.file(home, key).exists());
                        assertFalse(ThumbnailBackfiller.memoryPut(key, blank));
                        assertNull(ThumbnailBackfiller.memoryGet(key));
                        assertPlaceholder(key);
                    } finally {
                        blank.recycle();
                    }
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                CanvasPrefs.setThumbnailsEnabled(home, false);
            }
        });
    }

    public void testRecycledEntryFallsBackEvenWhenPreviouslyBound() {
        getInstrumentation().runOnMainSync(() -> {
            String key = "recycled-" + System.nanoTime();
            Bitmap bitmap = frame(Color.BLACK);
            try {
                assertTrue(ThumbnailBackfiller.memoryPut(key, bitmap));
                CanvasPrefs.setThumbnailsEnabled(home, true);
                scrollState.setInt(home, AbsListView.OnScrollListener.SCROLL_STATE_FLING);
                ImageView cover = cover(key);
                bind(cover, key);
                assertSame(bitmap, displayed(cover));
                bitmap.recycle();
                bind(cover, key);
                assertNull(ThumbnailBackfiller.memoryGet(key));
                assertSame(KindPlaceholder.bitmap(home, DocumentKind.HTML), displayed(cover));
                assertFalse(ThumbnailBackfiller.memoryPut(key, bitmap));
                assertFalse(ThumbnailBackfiller.memoryPut(key, null));
                // Recycling must not corrupt LruCache's byte accounting on removal/replacement.
                Bitmap replacement = frame(Color.BLUE);
                try {
                    assertTrue(ThumbnailBackfiller.memoryPut(key, replacement));
                    assertSame(replacement, ThumbnailBackfiller.memoryGet(key));
                } finally {
                    replacement.recycle();
                    assertNull(ThumbnailBackfiller.memoryGet(key));
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                CanvasPrefs.setThumbnailsEnabled(home, false);
                if (!bitmap.isRecycled()) bitmap.recycle();
            }
        });
    }

    public void testDisabledThumbnailsKeepTheTypePlaceholderOnMemoryHit() {
        getInstrumentation().runOnMainSync(() -> {
            String key = "disabled-" + System.nanoTime();
            Bitmap bitmap = frame(Color.BLACK);
            try {
                assertTrue(ThumbnailBackfiller.memoryPut(key, bitmap));
                assertPlaceholder(key);
            } finally {
                bitmap.recycle();
                assertNull(ThumbnailBackfiller.memoryGet(key));
            }
        });
    }

    public void testVisibleRefreshReusesContentDecisionAndHonorsTheSwitch() {
        getInstrumentation().runOnMainSync(() -> {
            String key = "refresh-" + System.nanoTime();
            Bitmap bitmap = frame(Color.BLACK);
            try {
                assertTrue(ThumbnailBackfiller.memoryPut(key, bitmap));
                ImageView cover = cover(key);
                cover.setId(android.R.id.icon);
                cover.setLayoutParams(new AbsListView.LayoutParams(320, 200));
                ListView list = new ListView(home);
                list.setAdapter(new ArrayAdapter<String>(home,
                        android.R.layout.simple_list_item_1, new String[]{key}) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        return cover;
                    }
                });
                list.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
                list.layout(0, 0, 320, 200);
                assertEquals(1, list.getChildCount());
                Method paint = MainActivity.class.getDeclaredMethod("paintVisible",
                        AbsListView.class, int.class);
                paint.setAccessible(true);
                assertPlaceholder(key);
                cover.setImageBitmap(KindPlaceholder.bitmap(home, DocumentKind.HTML));
                paint.invoke(home, list, android.R.id.icon);
                assertSame(KindPlaceholder.bitmap(home, DocumentKind.HTML), displayed(cover));
                CanvasPrefs.setThumbnailsEnabled(home, true);
                bitmap.eraseColor(Color.WHITE); // same classification sentinel as repeated binds
                paint.invoke(home, list, android.R.id.icon);
                assertSame(bitmap, displayed(cover));
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                CanvasPrefs.setThumbnailsEnabled(home, false);
                bitmap.recycle();
                assertNull(ThumbnailBackfiller.memoryGet(key));
            }
        });
    }

    public void testLateDecodeCannotRepaintDisabledOrReusedCover() {
        getInstrumentation().runOnMainSync(() -> {
            String key = "queued-" + System.nanoTime();
            Bitmap bitmap = frame(Color.BLUE);
            ImageView cover = cover(key);
            Bitmap placeholder = KindPlaceholder.bitmap(home, DocumentKind.HTML);
            try {
                Method apply = MainActivity.class.getDeclaredMethod("applyThumbnail",
                        ImageView.class, String.class, Bitmap.class, boolean.class);
                apply.setAccessible(true);
                CanvasPrefs.setThumbnailsEnabled(home, false);
                cover.setAlpha(0.25f);
                bind(cover, key);
                assertSame(placeholder, displayed(cover));
                assertEquals(1f, cover.getAlpha(), 0f);

                apply.invoke(home, cover, key, bitmap, false);
                assertSame("late decode while disabled", placeholder, displayed(cover));

                CanvasPrefs.setThumbnailsEnabled(home, true);
                cover.setTag(key + "-reused");
                apply.invoke(home, cover, key, bitmap, false);
                assertSame("late decode for recycled row", placeholder, displayed(cover));

                cover.setTag(key);
                apply.invoke(home, cover, key, bitmap, false);
                assertSame("matching result still fills an enabled cover", bitmap, displayed(cover));
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                CanvasPrefs.setThumbnailsEnabled(home, false);
                cover.setImageDrawable(null);
                bitmap.recycle();
            }
        });
    }

    private Bitmap frame(int color) {
        Bitmap bitmap = Bitmap.createBitmap(PreviewThumbnailCache.CARD_W,
                PreviewThumbnailCache.CARD_H, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private ImageView cover(String key) {
        ImageView cover = new ImageView(home);
        cover.setTag(key);
        return cover;
    }

    private Bitmap displayed(ImageView cover) {
        assertTrue(cover.getDrawable() instanceof BitmapDrawable);
        return ((BitmapDrawable) cover.getDrawable()).getBitmap();
    }

    private void assertPlaceholder(String key) {
        ImageView cover = cover(key);
        bind(cover, key);
        assertSame(KindPlaceholder.bitmap(home, DocumentKind.HTML), displayed(cover));
    }

    private void bind(ImageView cover, String key) {
        try {
            bind.invoke(home, cover, key, DocumentKind.HTML,
                    new CanvasDocument(key, "memory.html", "<html></html>", 1));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
