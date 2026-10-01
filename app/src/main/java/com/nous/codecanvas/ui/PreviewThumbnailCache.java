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
 public static Bitmap capture(View view){
  if(view.getWidth()<=0||view.getHeight()<=0) return null;
  Bitmap b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(b); canvas.drawColor(Color.WHITE);
  float scale=320f/view.getWidth(); canvas.scale(scale,scale); view.draw(canvas);
  return b;
 }
 public static void store(Context c,String key,Bitmap bitmap){
  if(looksBlank(bitmap)) return;   // nothing was painted; the type cover is the truthful fallback
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
  return min>=248&&(max-min)<=8;
 }
}
