package com.nous.codecanvas;
import android.test.InstrumentationTestCase;
import android.content.Intent;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.data.DocumentRepository;
import java.io.File;
public class ThumbnailCaptureTest extends InstrumentationTestCase {
 public void testPreviewStoresRealThumbnail() throws Exception {
  android.content.Context c=getInstrumentation().getTargetContext();
  CanvasDocument d=new CanvasDocument("thumbnail-"+System.nanoTime(),"封面.html","<html><body style='background:#0f766e'><h1>真实预览封面</h1></body></html>",System.currentTimeMillis());
  new DocumentRepository(c).saveDocument(d);
  Intent i=new Intent(c,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EditorActivity.EXTRA_DOC_ID,d.getId()).putExtra(EditorActivity.EXTRA_START_PREVIEW,true);
  EditorActivity a=(EditorActivity)getInstrumentation().startActivitySync(i);
  try {
   boolean dark=(a.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
   File f=new File(new File(c.getCacheDir(),"previews"),com.nous.codecanvas.util.PreviewKey.forDocument(d.getId(),d.getContent(),dark)+".png");
   long deadline=System.currentTimeMillis()+8000;
   while(!f.exists()&&System.currentTimeMillis()<deadline) Thread.sleep(100);
   assertTrue("实际预览必须生成封面缓存",f.exists());
   android.graphics.Bitmap b=android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
   assertNotNull("封面必须是真实可解码图片",b);
   assertEquals(320,b.getWidth());
   // Check color of rendered thumbnail: verify it rendered the teal/colored body (#0f766e) instead of pure white/blank
   boolean foundRenderedColor = false;
   int nonWhitePixelCount = 0;
   for (int x = 10; x < b.getWidth() - 10; x += 10) {
       for (int y = 10; y < b.getHeight() - 10; y += 10) {
           int pixel = b.getPixel(x, y);
           int red = android.graphics.Color.red(pixel);
           int green = android.graphics.Color.green(pixel);
           int blue = android.graphics.Color.blue(pixel);
           // Not blank white
           if (red < 240 || green < 240 || blue < 240) {
               nonWhitePixelCount++;
           }
           // Check for teal hue: #0f766e -> green & blue noticeably higher than red
           if (green > 80 && blue > 80 && red < 50) {
               foundRenderedColor = true;
           }
       }
   }
   assertTrue("Thumbnail must have non-white rendered content, found: " + nonWhitePixelCount, nonWhitePixelCount > 0);
   assertTrue("Thumbnail must contain actual rendered HTML color (#0f766e)", foundRenderedColor);
   b.recycle();
  } finally { getInstrumentation().runOnMainSync(a::finish); new DocumentRepository(c).deleteDocument(d.getId()); }
 }
}
