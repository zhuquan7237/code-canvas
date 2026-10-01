package com.nous.codecanvas.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.nous.codecanvas.R;
import com.nous.codecanvas.provider.CanvasFileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app updates that work without reaching GitHub.
 *
 * <p>Every address used to be pinned to GitHub — the manifest URL, the redirect allow-list and the
 * download allow-list. On a mainland network that made the feature dead weight: the manifest could
 * not be fetched, and even a manifest served from the project's own host was refused as an
 * untrusted address. The project's own static host is now the primary source and GitHub is the
 * fallback, so the common case is one request to a host that actually answers.</p>
 *
 * <p>One update task exists at a time and it carries its own immutable manifest: no repeated
 * tapping can start a second download, and a verification can never consult a manifest that a
 * later check replaced. The downloaded file is checked for length, SHA-256, package name, version
 * and signing certificate before the system installer is ever shown.</p>
 */
public final class AppUpdater {

    /** Tried in order. The first is this project's own host; GitHub is kept as a fallback. */
    private static final String[] MANIFEST_SOURCES = {
            "https://relay.zhuquan.xyz/dl/codecanvas-latest.json",
            "https://raw.githubusercontent.com/zhuquan7237/code-canvas/main/docs/update/latest.json",
            "https://api.github.com/repos/zhuquan7237/code-canvas/contents/docs/update/latest.json?ref=main"
    };

    private static final long AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    /** Backoff after consecutive check failures, so a blocked network is not hammered. */
    private static final long[] RETRY_BACKOFF_MS = {0L, 15L * 60L * 1000L, 60L * 60L * 1000L, 6L * 60L * 60L * 1000L};

    private static final String PREFS = "updates";
    private static final String KEY_AUTO = "auto";
    private static final String KEY_CHECKED_AT = "checked";
    private static final String KEY_FAILURES = "failures";
    private static final String KEY_READY_VERSION = "ready_version";
    private static final String KEY_READY_SHA = "ready_sha";

    private final Activity activity;
    private final Button button;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final int installedCode;
    private final String installedName;

    private boolean closed;
    private boolean awaitingInstallPermission;
    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;
    private AlertDialog dialog;
    private UpdateManifest available;
    private UpdateManifest downloading;

    public AppUpdater(Activity activity, Button button) {
        this.activity = activity;
        this.button = button;
        this.prefs = activity.getSharedPreferences(PREFS, 0);
        int code = 0;
        String name = "?";
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            code = p.versionCode;
            name = p.versionName;
        } catch (Exception ignored) {
        }
        installedCode = code;
        installedName = name;
        showIdleLabel();
        button.setOnClickListener(v -> {
            if (available != null) {
                offer(available);
            } else if (downloading != null) {
                Toast.makeText(activity, "正在下载更新…", Toast.LENGTH_SHORT).show();
            } else {
                check(true);
            }
        });
        button.setOnLongClickListener(v -> {
            new AlertDialog.Builder(activity)
                    .setTitle("更新设置")
                    .setMessage("检查只读取一个公开的版本清单，不上传任何文件内容。下载和安装都需要你确认。\n\n"
                            + "主源：relay.zhuquan.xyz（国内可直连）\n备源：GitHub")
                    .setMultiChoiceItems(new String[]{"启动时自动检查（每 6 小时）"},
                            new boolean[]{prefs.getBoolean(KEY_AUTO, true)},
                            (d, i, on) -> prefs.edit().putBoolean(KEY_AUTO, on).apply())
                    .setPositiveButton("确定", null)
                    .show();
            return true;
        });
    }

    private void ui(Runnable r) {
        activity.runOnUiThread(() -> {
            if (!closed && !activity.isDestroyed() && !activity.isFinishing()) {
                r.run();
            }
        });
    }

    private void showIdleLabel() {
        button.setText("检查更新");
        button.setTextColor(activity.getResources().getColor(R.color.canvas_ink_secondary));
    }

    /** Called from the host activity's onResume. */
    public void onResume() {
        if (awaitingInstallPermission && prefs.contains(KEY_READY_VERSION)
                && activity.getPackageManager().canRequestPackageInstalls()) {
            awaitingInstallPermission = false;
            installReadyPackage();
            return;
        }
        if (!prefs.getBoolean(KEY_AUTO, true)) {
            return;
        }
        long since = System.currentTimeMillis() - prefs.getLong(KEY_CHECKED_AT, 0L);
        if (since < AUTO_CHECK_INTERVAL_MS + backoff()) {
            return;
        }
        check(false);
    }

    private long backoff() {
        int failures = prefs.getInt(KEY_FAILURES, 0);
        if (failures <= 0) return 0L;
        return RETRY_BACKOFF_MS[Math.min(failures, RETRY_BACKOFF_MS.length - 1)];
    }

    // ---------------------------------------------------------------- checking

    public void check(final boolean manual) {
        if (downloading != null) {
            if (manual) Toast.makeText(activity, "正在下载更新，先完成这一步", Toast.LENGTH_SHORT).show();
            return;
        }
        if (manual) button.setText("检查中…");
        io.execute(() -> {
            UpdateManifest manifest = null;
            String lastError = null;
            StringBuilder tried = new StringBuilder();
            for (String source : MANIFEST_SOURCES) {
                if (closed) return;
                try {
                    String json = readText(source + (source.contains("?") ? "&" : "?") + "t="
                            + System.currentTimeMillis(), false);
                    manifest = UpdateManifest.parse(json);
                    break;
                } catch (Exception e) {
                    lastError = describe(e);
                    tried.append(sourceOf(source)).append("：").append(lastError).append('\n');
                }
            }
            if (manifest == null) {
                final String detail = tried.length() > 0 ? tried.toString().trim() : lastError;
                prefs.edit()
                        .putInt(KEY_FAILURES, Math.min(prefs.getInt(KEY_FAILURES, 0) + 1, RETRY_BACKOFF_MS.length - 1))
                        .apply();
                ui(() -> {
                    showIdleLabel();
                    if (manual) {
                        new AlertDialog.Builder(activity)
                                .setTitle("检查更新失败")
                                .setMessage("所有源都取不到版本清单。\n\n" + detail
                                        + "\n\n稍后会自动重试，也可以直接去下载页手动安装。")
                                .setPositiveButton("重试", (d, w) -> check(true))
                                .setNegativeButton("关闭", null)
                                .show();
                    }
                });
                return;
            }
            prefs.edit().putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                    .putInt(KEY_FAILURES, 0).apply();
            final UpdateManifest result = manifest;
            ui(() -> {
                if (result.versionCode > installedCode) {
                    available = result;
                    button.setText("新版本 " + result.versionName + " · 更新");
                    button.setTextColor(activity.getResources().getColor(R.color.canvas_primary));
                    if (manual) offer(result);
                } else {
                    available = null;
                    button.setText("已是最新 · " + installedName);
                    button.setTextColor(activity.getResources().getColor(R.color.canvas_ink_secondary));
                    if (manual) Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static String sourceOf(String url) {
        if (url.contains("relay.zhuquan.xyz")) return "主源";
        if (url.contains("api.github.com")) return "GitHub API";
        return "GitHub";
    }

    // ---------------------------------------------------------------- downloading

    private void offer(final UpdateManifest m) {
        new AlertDialog.Builder(activity)
                .setTitle("更新到 " + m.versionName)
                .setMessage(m.notes + "\n\n下载 " + m.size + " 字节。校验通过后交给系统安装，已有文件会保留。")
                .setPositiveButton("下载并安装", (d, w) -> download(m))
                .setNegativeButton("稍后", null)
                .setNeutralButton("重新检查", (d, w) -> {
                    available = null;
                    check(true);
                })
                .show();
    }

    private void download(final UpdateManifest m) {
        if (downloading != null) return;
        downloading = m;
        cancelled = false;

        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(activity);
        box.setPadding(pad, pad, pad, pad);
        box.addView(progress, new LinearLayout.LayoutParams(-1, -2));

        dialog = new AlertDialog.Builder(activity)
                .setTitle("正在下载更新")
                .setView(box)
                .setNegativeButton("取消", (d, w) -> cancelDownload())
                .setOnCancelListener(d -> cancelDownload())
                .show();

        io.execute(() -> {
            final File part = new File(activity.getCacheDir(), "updates/update.part");
            try {
                part.getParentFile().mkdirs();
                // Primary address first; if the host is unreachable (a real risk on mainland
                // networks) fall through to the mirror instead of failing the whole update.
                String gotHash = null;
                IOException lastError = null;
                for (String url : m.downloadUrls()) {
                    try {
                        gotHash = fetchTo(url, part, m, progress);
                        lastError = null;
                        break;
                    } catch (IOException e) {
                        if (cancelled) throw e;
                        lastError = e;
                    }
                }
                if (lastError != null) throw lastError;
                if (cancelled) throw new IOException("已取消");
                // Say which check failed: "校验失败" sent every mismatch (truncated transfer, stale
                // CDN copy, wrong manifest) down the same opaque path.
                if (gotHash == null || part.length() != m.size) {
                    throw new IOException("安装包大小与清单不符（收到 " + part.length() + " 字节，清单 " + m.size + " 字节）");
                }
                if (!gotHash.equalsIgnoreCase(m.sha256)) {
                    throw new IOException("安装包 SHA-256 校验失败（收到 " + gotHash.substring(0, 12) + "…，清单 " + m.sha256.substring(0, 12) + "…）");
                }

                File target = new File(part.getParentFile(), "update.apk");
                if (target.exists() && !target.delete()) throw new IOException("无法移除旧安装包");
                if (!part.renameTo(target)) throw new IOException("无法保存安装包");

                verifyPackage(target, m);
                prefs.edit()
                        .putInt(KEY_READY_VERSION, m.versionCode)
                        .putString(KEY_READY_SHA, m.sha256)
                        .apply();

                ui(() -> {
                    dismissDialog();
                    downloading = null;
                    installReadyPackage();
                });
            } catch (final Exception e) {
                part.delete();
                ui(() -> {
                    dismissDialog();
                    downloading = null;
                    if (!cancelled) {
                        new AlertDialog.Builder(activity)
                                .setTitle("更新下载失败")
                                .setMessage(describe(e) + "\n\n两个地址都没成时，可以自己去下载页拿安装包。")
                                .setPositiveButton("重试", (d, w) -> download(m))
                                .setNeutralButton("手动下载", (d, w) -> openInBrowser(m.apkUrl))
                                .setNegativeButton("关闭", null)
                                .show();
                    }
                });
            }
        });
    }

    private void cancelDownload() {
        cancelled = true;
        HttpURLConnection c = activeConnection;
        if (c != null) c.disconnect();
        downloading = null;
    }

    /** Escape hatch for the failure dialog: hand the user the same download the app would use. */
    private void openInBrowser(String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(view);
        } catch (Exception e) {
            Toast.makeText(activity, "没有可用的浏览器，地址：" + url, Toast.LENGTH_LONG).show();
        }
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = null;
    }

    // ---------------------------------------------------------------- verifying

    /**
     * Streams one address to {@code part} and returns its SHA-256. Split out of {@link #download} so
     * the caller can retry the whole transfer against a second address.
     */
    private String fetchTo(String url, File part, UpdateManifest m, ProgressBar progress) throws IOException {
        HttpURLConnection c = open(url, true);
        activeConnection = c;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[8192];
                int n;
                int shown = -1;
                while ((n = in.read(buffer)) != -1) {
                    if (cancelled) throw new IOException("已取消");
                    total += n;
                    if (total > m.size) throw new IOException("安装包长度与清单不符");
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    int percent = (int) (total * 100 / m.size);
                    if (percent != shown) {
                        shown = percent;
                        final int value = percent;
                        ui(() -> progress.setProgress(value));
                    }
                }
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b));
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("设备缺少 SHA-256 支持", e);
        } finally {
            c.disconnect();
            activeConnection = null;
        }
    }

    private void verifyPackage(File file, UpdateManifest m) throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageManager pm = activity.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(file.getPath(), flags);
        PackageInfo current = pm.getPackageInfo(activity.getPackageName(), flags);
        if (archive == null
                || !activity.getPackageName().equals(archive.packageName)
                || archive.versionCode != m.versionCode
                || !m.versionName.equals(archive.versionName)) {
            throw new IOException("安装包版本或应用标识不匹配");
        }
        Signature[] downloaded = Build.VERSION.SDK_INT >= 28
                ? archive.signingInfo.getApkContentsSigners() : archive.signatures;
        Signature[] installed = Build.VERSION.SDK_INT >= 28
                ? current.signingInfo.getApkContentsSigners() : current.signatures;
        if (downloaded == null || installed == null
                || downloaded.length != 1 || installed.length != 1
                || !Arrays.equals(downloaded[0].toByteArray(), installed[0].toByteArray())) {
            throw new IOException("安装包签名与已装版本不一致");
        }
    }

    // ---------------------------------------------------------------- installing

    private void installReadyPackage() {
        final File file = new File(activity.getCacheDir(), "updates/update.apk");
        if (!file.exists()) {
            prefs.edit().remove(KEY_READY_VERSION).remove(KEY_READY_SHA).apply();
            return;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            awaitingInstallPermission = true;
            new AlertDialog.Builder(activity)
                    .setTitle("允许安装更新")
                    .setMessage("安装包已校验。请在系统设置里允许代码画布安装应用，然后回到这里继续。"
                            + "这不会改动其他安全设置。")
                    .setPositiveButton("去设置", (d, w) -> {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (Exception e) {
                            Toast.makeText(activity, "请在系统设置中允许安装应用", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("稍后", null)
                    .show();
            return;
        }
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(activity.getPackageName() + CanvasFileProvider.AUTHORITY_SUFFIX)
                .appendPath("updates")
                .appendPath("update.apk")
                .build();
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(install);
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception ex) {
                Toast.makeText(activity, "无法打开系统安装器：" + describe(ex), Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------- networking

    private static String describe(Throwable t) {
        if (t == null) return "未知错误";
        String message = t.getMessage();
        if (message == null || message.isEmpty()) return t.getClass().getSimpleName();
        return message.length() > 120 ? message.substring(0, 120) + "…" : message;
    }

    private String readText(String url, boolean apk) throws IOException {
        HttpURLConnection c = open(url, apk);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 65536) throw new IOException("更新清单过大");
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Open a whitelisted URL, following redirects only to other whitelisted hosts. */
    private HttpURLConnection open(String value, boolean apk) throws IOException {
        URI uri = URI.create(value);
        for (int hop = 0; hop < 5; hop++) {
            boolean trusted = apk
                    ? UpdateManifest.trustedDownloadAddress(uri.toString())
                    : UpdateManifest.trustedManifestAddress(uri.toString());
            if (!trusted) {
                throw new IOException("不受信任的更新地址：" + uri.getHost());
            }
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "CodeCanvas/" + installedName);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("Accept", "application/vnd.github.raw+json, application/json, */*");
            int status = c.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new IOException("无效重定向");
                uri = uri.resolve(location);
                continue;
            }
            if (status != 200) {
                c.disconnect();
                throw new IOException("HTTP " + status);
            }
            return c;
        }
        throw new IOException("重定向次数过多");
    }

    public void close() {
        closed = true;
        cancelled = true;
        HttpURLConnection c = activeConnection;
        if (c != null) c.disconnect();
        dismissDialog();
        io.shutdownNow();
    }

    // ---------------------------------------------------------------- test hooks

    public static String[] manifestSourcesForTest() {
        return MANIFEST_SOURCES.clone();
    }

    public int getInstalledVersionCodeForTest() {
        return installedCode;
    }
}
