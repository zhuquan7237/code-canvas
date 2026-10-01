package com.nous.codecanvas.ui;
import java.io.*;import java.nio.file.Files;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.regex.*;
public final class Palette {
 public static final String DAY="day",NIGHT="night";private final Map<String,Integer> tokens;private Palette(Map<String,Integer> t){tokens=t;}
 public int color(String n){Integer v=tokens.get(n);if(v==null)throw new IllegalArgumentException(n);return v;}public boolean has(String n){return tokens.containsKey(n);}
 public static Palette load(String theme)throws IOException {
  String xml=read("app/src/main/res/"+(NIGHT.equals(theme)?"values-night":"values")+"/colors.xml");Map<String,String> raw=new HashMap<>();
  Matcher m=Pattern.compile("<color\\s+name=\"([^\"]+)\">([^<]+)</color>").matcher(xml);while(m.find())raw.put(m.group(1),m.group(2).trim());
  Map<String,Integer> parsed=new LinkedHashMap<>();for(String n:raw.keySet())parsed.put(n.startsWith("canvas_")?n.substring(7):n,resolve(raw,n,0));return new Palette(parsed);
 }
 private static int resolve(Map<String,String> raw,String key,int depth){if(depth>10)throw new IllegalArgumentException("alias cycle");String v=raw.get(key);if(v.startsWith("@color/"))return resolve(raw,v.substring(7),depth+1);long c=Long.parseLong(v.substring(1),16);return (int)(v.length()==7?c|0xFF000000L:c);}
 static Pattern solidColorPattern(){return Pattern.compile("<color\\s+name=\"(?:canvas_)?([a-z_]+)\">#([0-9A-Fa-f]{6,8})</color>");}
 static String read(String path)throws IOException {File f=new File("").getAbsoluteFile();for(int i=0;i<5&&f!=null;i++,f=f.getParentFile()){File p=new File(f,path);if(p.isFile())return new String(Files.readAllBytes(p.toPath()),StandardCharsets.UTF_8);}throw new IOException(path);}
}
