package com.nous.codecanvas.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;

/** Small screenshots of previously opened artwork. No WebViews on the home list. */
public final class PreviewThumbnailCache {
 private PreviewThumbnailCache(){}
 public static File file(Context c,String key){ return new File(new File(c.getCacheDir(),"previews"),key+".png"); }
 /**
  * Capture at the preview's own aspect ratio. A fixed 16:9 frame left white bands under short
  * pages and cut tall ones off, and the list then centre-cropped that twice over - which is how
  * the same app showed thumbnails that looked either empty or oddly zoomed in.
  */
 public static Bitmap capture(View view){
  int w=view.getWidth(),h=view.getHeight();
  if(w<=0||h<=0) return null;
  int outW=480;
  int outH=Math.max(1,Math.round(outW*(float)h/w));
  // A very tall page would otherwise produce an enormous bitmap for a 44dp tile. Clamping the
  // height keeps the memory cost bounded; the aspect ratio is still whatever the page had, so the
  // tile shows the whole page rather than a crop of it.
  if(outH>outW*3){ outH=outW*3; }
  Bitmap b=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(b); canvas.drawColor(Color.WHITE);
  float scale=(float)outW/w; canvas.scale(scale,scale); view.draw(canvas);
  return b;
 }
 public static void store(Context c,String key,Bitmap bitmap){
  if(lacksContent(bitmap)) return;   // nothing readable was painted; keep the type tile
  File f=file(c,key); File dir=f.getParentFile();
  try {
   if(!dir.exists()&&!dir.mkdirs()) return;
   File temp=new File(dir,key+".tmp");
   try(FileOutputStream out=new FileOutputStream(temp)){ bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
   java.nio.file.Files.move(temp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   File[] entries=dir.listFiles((d,n)->n.endsWith(".png"));
   if(entries!=null&&entries.length>40){ Arrays.sort(entries,Comparator.comparingLong(File::lastModified).reversed()); for(int i=40;i<entries.length;i++) entries[i].delete(); }
  }catch(Exception ignored){ /* Optional preview cache never affects source persistence. */ }
  finally { bitmap.recycle(); }
 }
 /**
  * True when a capture is still the untouched white base coat, i.e. the page painted nothing.
  * {@link #capture} paints white first, so a frame that never painted otherwise gets stored as a
  * white tile that replaces the file-type cover and reads as a broken thumbnail. A flat *colour*
  * frame, by contrast, is real content and is kept.
  */
 public static boolean looksBlank(Bitmap b){
  if(b==null||b.getWidth()<2||b.getHeight()<2) return true;
  Bitmap small=Bitmap.createScaledBitmap(b,16,16,true);
  int min=255,max=0;
  for(int y=0;y<16;y++){ for(int x=0;x<16;x++){
   int p=small.getPixel(x,y);
   int lum=(Color.red(p)*299+Color.green(p)*587+Color.blue(p)*114)/1000;
   if(lum<min)min=lum; if(lum>max)max=lum;
  }}
  if(small!=b) small.recycle();
  // "Nothing painted" is precisely "still the white base coat". A flat *colour* is real content:
  // a page that paints only a background must keep its thumbnail, not fall back to the cover.
  // Tightened: >=248 also swallowed the light page background (#F8FAFC is exactly 248,250,252),
  // so an ordinary light HTML page was judged "nothing painted" and the home list fell back to
  // type covers. Only near-pure white now counts as the untouched base coat.
  return min>=252&&(max-min)<=4;
 }

 /**
  * True when the capture has no readable mark: a blank page, or a page that is only the
  * paper colour. Markdown captured before its bundled script runs looks like this.
  */
 public static boolean lacksContent(Bitmap b) {
  if (b == null || looksBlank(b)) return true;
  Bitmap small = Bitmap.createScaledBitmap(b, 48, 48, true);
  int dark = 0;
  for (int y = 0; y < 48; y++) {
   for (int x = 0; x < 48; x++) {
    int p = small.getPixel(x, y);
    int lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000;
    if (lum < 210) dark++;
   }
  }
  if (small != b) small.recycle();
  return dark < 10;
 }

 /**
  * Cuts away the empty paper around a drawing. A Mermaid chart sits in the middle of a
  * tall page; without this the tile is almost entirely background.
  */
 public static Bitmap cropToInk(Bitmap source) {
  if (source == null) return null;
  int w = source.getWidth();
  int h = source.getHeight();
  int step = 3;
  int minX = w, minY = h, maxX = -1, maxY = -1;
  for (int y = 0; y < h; y += step) {
   for (int x = 0; x < w; x += step) {
    int p = source.getPixel(x, y);
    int lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000;
    if (lum < 215) {
     if (x < minX) minX = x;
     if (y < minY) minY = y;
     if (x > maxX) maxX = x;
     if (y > maxY) maxY = y;
    }
   }
  }
  if (maxX < 0) return null;
  int pad = 12;
  minX = Math.max(0, minX - pad);
  minY = Math.max(0, minY - pad);
  maxX = Math.min(w - 1, maxX + pad);
  maxY = Math.min(h - 1, maxY + pad);
  int cw = maxX - minX + 1;
  int ch = maxY - minY + 1;
  if (cw < 16 || ch < 16) return null;
  return Bitmap.createBitmap(source, minX, minY, cw, ch);
 }

 /**
  * Crops away a flat letterbox whose colour matches the corners. A wide SVG rendered in a tall
  * page becomes the drawing itself, at its own aspect ratio, instead of a thin strip on black.
  */
 public static Bitmap cropToContent(Bitmap source) {
  if (source == null) return null;
  int w = source.getWidth();
  int h = source.getHeight();
  int[] pixels = new int[w * h];
  source.getPixels(pixels, 0, w, 0, 0, w, h);
  int[] box = com.nous.codecanvas.util.InkBounds.box(pixels, w, h);
  if (box == null) return source;
  return Bitmap.createBitmap(source, box[0], box[1], box[2], box[3]);
 }

 /** The home slot is about 480px wide. Larger captures only cost memory. */
 public static Bitmap fitSlot(Bitmap source, int maxEdge) {
  if (source == null) return null;
  int w = source.getWidth();
  int h = source.getHeight();
  int edge = Math.max(w, h);
  if (edge <= maxEdge || maxEdge < 8) return source;
  float scale = maxEdge / (float) edge;
  return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(w * scale)),
          Math.max(1, Math.round(h * scale)), true);
 }
}
