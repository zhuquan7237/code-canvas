package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.test.InstrumentationTestCase;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;
import com.nous.codecanvas.ui.PreviewThumbnailCache;
import com.nous.codecanvas.util.CanvasPrefs;
import com.nous.codecanvas.util.PreviewKey;

import java.io.File;

/**
 * The complaint this covers: "主页怎么没有预览图" — a document that the user has never opened had no
 * thumbnail at all, so the home list showed nothing but file-type covers and the app looked broken.
 *
 * <p>A real document with real content is created, the home screen is opened, and the thumbnail has
 * to appear without anyone opening the preview.</p>
 */
public class ThumbnailBackfillTest extends InstrumentationTestCase {

    public void testUnopenedDocumentGetsAThumbnailFromTheHomeScreen() throws Exception {
        final Context c = getInstrumentation().getTargetContext();
        DocumentRepository repo = new DocumentRepository(c);
        CanvasPrefs.setScriptsAllowed(c, true);   // the page proves scripts ran by rewriting itself

        final String content = "<html><head><style>body{background:#0f766e;margin:0;padding:20px}"
                + "h1{color:#ffffff;font-family:sans-serif;font-size:34px}</style></head>"
                + "<body><h1 id=\"t\">占位</h1><script>"
                + "document.getElementById('t').innerText='主页缩略图';</script></body></html>";
        final CanvasDocument doc = new CanvasDocument("backfill-" + System.nanoTime(), "backfill.html",
                content, System.currentTimeMillis());
        repo.saveDocument(doc);

        boolean dark = (c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        final File thumb = PreviewThumbnailCache.file(c,
                PreviewKey.forDocument(doc.getId(), doc.getContent(), dark));
        if (thumb.exists() && !thumb.delete()) {
            fail("could not clear the previous capture at " + thumb);
        }

        Intent intent = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity home = (MainActivity) getInstrumentation().startActivitySync(intent);
        try {
            long deadline = System.currentTimeMillis() + 25000;
            while (!thumb.exists() && System.currentTimeMillis() < deadline) Thread.sleep(250);

            assertTrue("主页必须为没打开过的作品生成缩略图（" + thumb + "）", thumb.exists());
            Bitmap b = BitmapFactory.decodeFile(thumb.getAbsolutePath());
            assertNotNull("缩略图必须能解码", b);
            assertFalse("生成的缩略图不能是空白帧", PreviewThumbnailCache.looksBlank(b));
            assertEquals("缩略图宽度固定为 480", 480, b.getWidth());

            // The page is teal with white text: the capture must show the painted background, which
            // is exactly what the old "min >= 248 means blank" rule threw away.
            int teal = 0;
            for (int x = 4; x < b.getWidth() - 4; x += 8) {
                for (int y = 4; y < b.getHeight() - 4; y += 8) {
                    int p = b.getPixel(x, y);
                    if (Color.green(p) > 90 && Color.blue(p) > 90 && Color.red(p) < 60) teal++;
                }
            }
            assertTrue("缩略图必须包含页面真实绘制的内容（teal）", teal > 0);
            b.recycle();
        } finally {
            final MainActivity toFinish = home;
            getInstrumentation().runOnMainSync(toFinish::finish);
            repo.deleteDocument(doc.getId());
            thumb.delete();
        }
    }
}
