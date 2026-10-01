package com.nous.codecanvas.util;
public final class PreviewKey {
 private PreviewKey(){}
 public static String forDocument(String id,String content,boolean dark){
  try {
   byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest((id+"\u0000"+dark+"\u0000"+content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   StringBuilder key=new StringBuilder(64);
   for(byte b:bytes){ key.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16)); }
   return key.toString();
  }catch(java.security.NoSuchAlgorithmException e){ throw new IllegalStateException(e); }
 }
}
