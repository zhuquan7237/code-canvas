package com.nous.codecanvas.update;

import org.json.JSONObject;

import java.net.URI;
import java.util.Locale;

/**
 * A parsed, validated update manifest.
 *
 * <p>The download address is the one field an attacker would love to control, so it is checked
 * against an explicit allow-list of hosts and path shapes rather than trusted merely for arriving
 * over HTTPS. That list used to contain only github.com — which made the entire update channel
 * useless on mainland networks, because even a perfectly good manifest pointing at this project's
 * own server was rejected as "untrusted".</p>
 */
public final class UpdateManifest {

    public final int versionCode;
    public final String versionName;
    public final String apkUrl;
    /** Optional second download address, tried when the primary one fails. */
    public final String fallbackApkUrl;
    public final String sha256;
    public final String notes;
    public final long size;
    /**
     * The Android API level the package was built for, when the publisher states it. An update that
     * needs a newer system than this device has must be refused here rather than failing inside the
     * installer with an opaque error.
     */
    public final int minSdk;
    /** Free-form channel marker; only {@code stable} (or absent) is offered for installation. */
    public final String channel;

    private static final long MAX_APK_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_MANIFEST_CHARS = 65536;

    private UpdateManifest(JSONObject j) throws Exception {
        if (j.getInt("schemaVersion") != 1) {
            throw new IllegalArgumentException("更新清单版本不支持");
        }
        versionCode = j.getInt("versionCode");
        versionName = j.getString("versionName");
        apkUrl = j.getString("apkUrl");
        sha256 = j.getString("sha256");
        size = j.getLong("size");
        notes = j.optString("notes", "");
        minSdk = j.optInt("minSdk", 0);
        String declaredChannel = j.optString("channel", "stable").trim().toLowerCase(Locale.ROOT);
        channel = declaredChannel.isEmpty() ? "stable" : declaredChannel;
        // A bad fallback address is not fatal — it is simply never used.
        String candidate = j.optString("fallbackApkUrl", "").trim();
        fallbackApkUrl = (candidate.isEmpty() || !trustedDownloadAddress(candidate)) ? null : candidate;
        if (versionCode <= 0
                || !versionName.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || !sha256.matches("[a-fA-F0-9]{64}")
                || size < 1
                || size > MAX_APK_BYTES) {
            throw new IllegalArgumentException("更新清单无效");
        }
        // A manifest that says "beta" must never be installed by the stable channel: the whole
        // point of the channel field is that a pre-release cannot masquerade as a release.
        if (!"stable".equals(channel)) {
            throw new IllegalArgumentException("这不是正式版本（channel=" + channel + "），不提供安装");
        }
        if (!trustedDownloadAddress(apkUrl)) {
            throw new IllegalArgumentException("不受信任的更新地址");
        }
    }

    /** Every address worth trying, primary first. */
    public java.util.List<String> downloadUrls() {
        java.util.List<String> urls = new java.util.ArrayList<>(2);
        urls.add(apkUrl);
        if (fallbackApkUrl != null && !fallbackApkUrl.equals(apkUrl)) urls.add(fallbackApkUrl);
        return urls;
    }

    public static UpdateManifest parse(String json) throws Exception {
        if (json == null || json.length() > MAX_MANIFEST_CHARS) {
            throw new IllegalArgumentException("清单过大");
        }
        return new UpdateManifest(new JSONObject(json));
    }

    /** True when this update can run on a device with {@code deviceSdk} (0 = unknown, allow). */
    public boolean supportsDevice(int deviceSdk) {
        return minSdk <= 0 || deviceSdk <= 0 || deviceSdk >= minSdk;
    }

    /** A short, honest description of the system requirement, or null when there is none. */
    public String minSdkNote(int deviceSdk) {
        if (supportsDevice(deviceSdk)) return null;
        return "这个版本需要 Android API " + minSdk + " 及以上，当前设备是 API " + deviceSdk;
    }

    /** Where an installer package may come from: this project's own host, or GitHub's. */
    public static boolean trustedDownloadAddress(String url) {
        try {
            URI u = URI.create(url);
            if (!"https".equalsIgnoreCase(u.getScheme())
                    || u.getUserInfo() != null
                    || u.getPort() != -1
                    || u.getFragment() != null) {
                return false;
            }
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String path = u.getPath() == null ? "" : u.getPath();
            if ("relay.zhuquan.xyz".equals(host)) {
                return path.startsWith("/dl/") && path.toLowerCase(Locale.ROOT).endsWith(".apk");
            }
            if ("github.com".equals(host)) {
                return path.startsWith("/zhuquan7237/code-canvas/releases/download/")
                        && path.toLowerCase(Locale.ROOT).endsWith(".apk");
            }
            // GitHub redirects release downloads to asset hosts whose exact paths are not
            // predictable; for those, scheme and host are the guarantee.
            return "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    /** Where a manifest may be read from. */
    public static boolean trustedManifestAddress(String url) {
        try {
            URI u = URI.create(url);
            if (!"https".equalsIgnoreCase(u.getScheme())
                    || u.getUserInfo() != null
                    || u.getPort() != -1) {
                return false;
            }
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String path = u.getPath() == null ? "" : u.getPath();
            if ("relay.zhuquan.xyz".equals(host)) {
                return path.startsWith("/dl/") && path.toLowerCase(Locale.ROOT).endsWith(".json");
            }
            if ("raw.githubusercontent.com".equals(host)) {
                return path.equals("/zhuquan7237/code-canvas/main/docs/update/latest.json");
            }
            return "api.github.com".equals(host)
                    && path.equals("/repos/zhuquan7237/code-canvas/contents/docs/update/latest.json");
        } catch (Exception e) {
            return false;
        }
    }
}
