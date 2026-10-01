package com.nous.codecanvas.update;
import org.json.JSONObject;import java.net.URI;
public final class UpdateManifest {
 public final int versionCode; public final String versionName,apkUrl,sha256,notes; public final long size;
 private UpdateManifest(JSONObject j)throws Exception {
  if(j.getInt("schemaVersion")!=1)throw new IllegalArgumentException("更新清单版本不支持");
  versionCode=j.getInt("versionCode");versionName=j.getString("versionName");apkUrl=j.getString("apkUrl");sha256=j.getString("sha256");size=j.getLong("size");notes=j.optString("notes","");
  if(versionCode<=0||!versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!sha256.matches("[a-fA-F0-9]{64}")||size<1||size>8*1024*1024)throw new IllegalArgumentException("更新清单无效");
  URI u=URI.create(apkUrl);if(!"https".equals(u.getScheme())||!"github.com".equals(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1||!u.getPath().startsWith("/zhuquan7237/code-canvas/releases/download/"))throw new IllegalArgumentException("不受信任的更新地址");
 }
 public static UpdateManifest parse(String json)throws Exception {if(json.length()>65536)throw new IllegalArgumentException("清单过大");return new UpdateManifest(new JSONObject(json));}
}
