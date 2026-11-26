package com.nous.codecanvas.util;
public final class PreviewKey {
 private PreviewKey(){}
 public static String forThumbnail(String id,String kind,String content,boolean dark,boolean scripts){
  String renderVersion = "HTML".equals(kind) ? "thumbnail-v9" : "thumbnail-v8";
  return forDocument(id+"\u0000"+kind+"\u0000"+scripts+"\u0000"+renderVersion,content,dark);
 }
 public static String forDocument(String id,String content,boolean dark){
  try {
   byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((id+"\u0000"+dark+"\u0000v7\u0000"+content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   StringBuilder key=new StringBuilder(64);
   for(byte b:bytes){ key.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16)); }
   return key.toString();
  }catch(java.security.NoSuchAlgorithmException e){ throw new IllegalStateException(e); }
 }
}
