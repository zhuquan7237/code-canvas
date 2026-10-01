package com.nous.codecanvas;
import org.junit.Test; import static org.junit.Assert.*;
public class UpgradeFeaturesTest {
 private Class<?> feature(String n) {try{return Class.forName("com.nous.codecanvas."+n);}catch(Exception e){fail("Missing feature "+n);return null;}}
 @Test public void editorSymbolsReplaceSelectionAndPairCursor() throws Exception {
  Class<?> c=feature("editor.EditorCommands");Object result=c.getMethod("insert",String.class,int.class,int.class,String.class).invoke(null,"hello",1,4,"{}");
  assertEquals("h{}o",result.getClass().getField("text").get(result));assertEquals(2,result.getClass().getField("cursor").get(result));
 }
 @Test public void previewKeepsUnchangedRendererAndRejectsOldCallbacks() throws Exception {
  Class<?> c=feature("editor.PreviewState");Object state=c.getConstructor().newInstance();
  assertEquals(true,c.getMethod("request",String.class,boolean.class).invoke(state,"A",false));
  long first=(long)c.getField("generation").get(state);
  assertEquals(false,c.getMethod("request",String.class,boolean.class).invoke(state,"A",false));
  assertEquals(true,c.getMethod("request",String.class,boolean.class).invoke(state,"B",false));
  assertEquals(false,c.getMethod("ready",long.class).invoke(state,first));
  assertEquals(true,c.getMethod("ready",long.class).invoke(state,c.getField("generation").get(state)));
 }
 @Test public void manifestRejectsUnsafeUrlAndBadDigest() throws Exception {
  Class<?> c=feature("update.UpdateManifest");
  String valid="{\"schemaVersion\":1,\"versionName\":\"0.1.2\",\"versionCode\":3,\"apkUrl\":\"https://github.com/zhuquan7237/code-canvas/releases/download/v0.1.2/code-canvas-0.1.2.apk\",\"size\":50000,\"sha256\":\""+"a".repeat(64)+"\"}";
  assertNotNull(c.getMethod("parse",String.class).invoke(null,valid));
  for(String bad:new String[]{valid.replace("https://github.com","http://github.com"),valid.replace("github.com","evil.example"),valid.replace("a".repeat(64),"short")}) {
   try{c.getMethod("parse",String.class).invoke(null,bad);fail("Unsafe manifest accepted");}catch(java.lang.reflect.InvocationTargetException expected){}
  }
 }
}
