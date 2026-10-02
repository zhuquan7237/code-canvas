package com.nous.codecanvas.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.nous.codecanvas.R;
import com.nous.codecanvas.util.AppearanceManager;
import com.nous.codecanvas.util.CanvasPrefs;

import java.io.File;
import java.util.Locale;

/**
 * Settings: appearance, preview defaults, cache and version.
 *
 * <p>Every switch here writes through {@link CanvasPrefs}, so the value a screen reads is the same
 * value this screen wrote. The appearance control is the one case that cannot simply take effect on
 * return: this Activity pinned its own uiMode at onCreate, so it recreates itself immediately and
 * the home screen notices the changed preference in its own onResume.</p>
 */
public class SettingsActivity extends Activity {

    private int appearanceShown;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppearanceManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View root = findViewById(R.id.root_settings);
        if (root != null) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
                return insets;
            });
        }

        appearanceShown = AppearanceManager.getMode(this);

        findViewById(R.id.btn_settings_back).setOnClickListener(v -> finish());

        setupAppearance();
        setupSwitches();
        setupCache();
        setupAbout();
    }

    // --- appearance ----------------------------------------------------------------------------

    private void setupAppearance() {
        Button light = findViewById(R.id.btn_appearance_light);
        Button dark = findViewById(R.id.btn_appearance_dark);
        Button system = findViewById(R.id.btn_appearance_system);

        light.setOnClickListener(v -> chooseAppearance(AppearanceManager.MODE_LIGHT));
        dark.setOnClickListener(v -> chooseAppearance(AppearanceManager.MODE_DARK));
        system.setOnClickListener(v -> chooseAppearance(AppearanceManager.MODE_SYSTEM));

        renderAppearanceSelection();
    }

    private void chooseAppearance(int mode) {
        if (mode == appearanceShown) {
            return;
        }
        AppearanceManager.setMode(this, mode);
        appearanceShown = mode;
        // Apply the new uiMode to this screen as well, so the change is visible here rather than
        // only after backing out. Both screens then read res/values-night/* from the same decision.
        recreate();
    }

    /** The selected pill is driven by view state, which is what bg_segment_item styles. */
    private void renderAppearanceSelection() {
        ((Button) findViewById(R.id.btn_appearance_light))
                .setSelected(appearanceShown == AppearanceManager.MODE_LIGHT);
        ((Button) findViewById(R.id.btn_appearance_dark))
                .setSelected(appearanceShown == AppearanceManager.MODE_DARK);
        ((Button) findViewById(R.id.btn_appearance_system))
                .setSelected(appearanceShown == AppearanceManager.MODE_SYSTEM);

        TextView value = findViewById(R.id.txt_appearance_value);
        value.setText(AppearanceManager.label(this, appearanceShown));
    }

    // --- preview switches ----------------------------------------------------------------------

    private void setupSwitches() {
        Switch scripts = findViewById(R.id.switch_scripts);
        scripts.setChecked(CanvasPrefs.scriptsAllowed(this));
        scripts.setOnCheckedChangeListener((v, checked) ->
                CanvasPrefs.setScriptsAllowed(SettingsActivity.this, checked));

        Switch thumbnails = findViewById(R.id.switch_thumbnails);
        thumbnails.setChecked(CanvasPrefs.thumbnailsEnabled(this));
        thumbnails.setOnCheckedChangeListener((v, checked) ->
                CanvasPrefs.setThumbnailsEnabled(SettingsActivity.this, checked));

        Switch network = findViewById(R.id.switch_network);
        network.setChecked(CanvasPrefs.networkAllowedByDefault(this));
        network.setOnCheckedChangeListener((v, checked) ->
                CanvasPrefs.setNetworkAllowedByDefault(SettingsActivity.this, checked));
    }

    // --- cache ---------------------------------------------------------------------------------

    private void setupCache() {
        refreshCacheLabel();

        findViewById(R.id.btn_clear_cache).setOnClickListener(v -> {
            long freed = clearPreviewCache();
            refreshCacheLabel();
            String msg = freed > 0
                    ? "已清理 " + humanSize(freed)
                    : "没有可清理的缓存";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshCacheLabel() {
        TextView label = findViewById(R.id.txt_cache_size);
        if (label == null) {
            return;
        }
        long bytes = previewCacheSize();
        label.setText(bytes > 0
                ? humanSize(bytes) + " · 清除后下次打开会重新生成"
                : "暂无缓存");
    }

    private long previewCacheSize() {
        File dir = new File(getCacheDir(), "previews");
        return directorySize(dir);
    }

    private static long directorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        File[] entries = dir.listFiles();
        if (entries == null) {
            return 0;
        }
        long total = 0;
        for (File f : entries) {
            total += f.isDirectory() ? directorySize(f) : f.length();
        }
        return total;
    }

    /** Deletes only the preview cache; documents live in the repository and are never touched. */
    private long clearPreviewCache() {
        File dir = new File(getCacheDir(), "previews");
        long freed = directorySize(dir);
        deleteRecursively(dir);
        return freed;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] entries = file.listFiles();
        if (entries != null) {
            for (File f : entries) {
                deleteRecursively(f);
            }
        }
        file.delete();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0f);
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0f * 1024.0f));
    }

    // --- about ---------------------------------------------------------------------------------

    private void setupAbout() {
        TextView version = findViewById(R.id.txt_about_version);
        TextView detail = findViewById(R.id.txt_about_detail);

        String versionName = "0.0.0";
        int versionCode = 0;
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = info.versionName;
            versionCode = info.versionCode;
        } catch (Exception ignored) {
            // Keep the placeholder rather than showing a wrong number.
        }

        version.setText("代码画布 " + versionName);
        detail.setText("任意后缀代码画布 · 原生 Java + 系统 WebView\n"
                + "版本 " + versionName + "（" + versionCode + "）\n"
                + "离线优先：不联网、不上传、不收集内容");
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
