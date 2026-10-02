package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import com.nous.codecanvas.ui.SettingsActivity;
import com.nous.codecanvas.util.AppearanceManager;
import com.nous.codecanvas.util.CanvasPrefs;

/**
 * The settings screen, driven for real: every switch has to write the preference that the rest of
 * the app reads, and the appearance picker has to reflect and store the chosen mode.
 *
 * <p>The failure this guards against is the one this app has already been burned by — a control
 * that looks like it works but only changes a local variable, so the setting silently reverts on
 * the next screen.</p>
 */
public class SettingsActivityTest extends InstrumentationTestCase {

    private SettingsActivity settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestAppearance.resetToSystem(getInstrumentation().getTargetContext());
        Intent intent = new Intent(getInstrumentation().getTargetContext(), SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        settings = (SettingsActivity) getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
    }

    @Override
    protected void tearDown() throws Exception {
        final SettingsActivity toFinish = settings;
        getInstrumentation().runOnMainSync(() -> {
            if (!toFinish.isFinishing()) toFinish.finish();
        });
        getInstrumentation().waitForIdleSync();
        Context c = getInstrumentation().getTargetContext();
        CanvasPrefs.setScriptsAllowed(c, true);
        CanvasPrefs.setThumbnailsEnabled(c, true);
        CanvasPrefs.setNetworkAllowedByDefault(c, false);
        TestAppearance.resetToSystem(c);
        super.tearDown();
    }

    /** Every control the screen claims to offer must actually exist and be wired to something. */
    public void testEveryControlIsPresentAndClickable() throws Throwable {
        final int[] ids = {
                R.id.btn_settings_back,
                R.id.btn_appearance_light,
                R.id.btn_appearance_dark,
                R.id.btn_appearance_system,
                R.id.switch_scripts,
                R.id.switch_thumbnails,
                R.id.switch_network,
                R.id.btn_clear_cache,
        };
        runTestOnUiThread(() -> {
            for (int id : ids) {
                View v = settings.findViewById(id);
                assertNotNull("设置项缺失: " + settings.getResources().getResourceEntryName(id), v);
                assertTrue("设置项必须可点击: " + settings.getResources().getResourceEntryName(id),
                        v.isClickable());
            }
        });
    }

    /** The three-way picker must mark exactly one option, or the current mode is unreadable. */
    public void testExactlyOneAppearanceOptionIsSelected() throws Throwable {
        runTestOnUiThread(() -> {
            Button light = settings.findViewById(R.id.btn_appearance_light);
            Button dark = settings.findViewById(R.id.btn_appearance_dark);
            Button system = settings.findViewById(R.id.btn_appearance_system);
            int selected = (light.isSelected() ? 1 : 0)
                    + (dark.isSelected() ? 1 : 0)
                    + (system.isSelected() ? 1 : 0);
            assertEquals("主题选择器必须恰好有一项处于选中态", 1, selected);
            assertTrue("默认应为跟随系统", system.isSelected());
        });
    }

    /** Choosing a mode must persist it, not just repaint the pill. */
    public void testChoosingAppearancePersistsTheMode() throws Throwable {
        final Context c = getInstrumentation().getTargetContext();

        runTestOnUiThread(() -> settings.findViewById(R.id.btn_appearance_dark).performClick());
        getInstrumentation().waitForIdleSync();
        assertEquals("选择深色必须写入偏好", AppearanceManager.MODE_DARK, AppearanceManager.getMode(c));

        // The activity recreates itself to apply the new uiMode; the stored value is the contract.
        assertEquals("设置页重开后仍应读到深色", AppearanceManager.MODE_DARK,
                CanvasPrefs.getInt(c, CanvasPrefs.KEY_APPEARANCE, -1));
    }

    /** Turning thumbnails off has to reach the same preference the home screen and backfiller read. */
    public void testThumbnailSwitchWritesTheSharedPreference() throws Throwable {
        final Context c = getInstrumentation().getTargetContext();
        assertTrue("缩略图默认开启", CanvasPrefs.thumbnailsEnabled(c));

        runTestOnUiThread(() -> {
            Switch s = settings.findViewById(R.id.switch_thumbnails);
            assertTrue("开关初始应为开启", s.isChecked());
            s.setChecked(false);
        });
        getInstrumentation().waitForIdleSync();

        assertFalse("关闭缩略图必须反映到共享偏好", CanvasPrefs.thumbnailsEnabled(c));
    }

    /** Scripts default to on and the screen must show that truthfully. */
    public void testScriptSwitchReflectsAndWritesPreference() throws Throwable {
        final Context c = getInstrumentation().getTargetContext();
        runTestOnUiThread(() -> {
            Switch s = settings.findViewById(R.id.switch_scripts);
            assertEquals("脚本开关须显示真实偏好", CanvasPrefs.scriptsAllowed(c), s.isChecked());
            s.setChecked(false);
        });
        getInstrumentation().waitForIdleSync();
        assertFalse("关闭脚本必须写入偏好", CanvasPrefs.scriptsAllowed(c));
    }

    /** The version shown must come from the package, not a hard-coded string. */
    public void testAboutSectionShowsTheRealVersion() throws Throwable {
        final String expected = settings.getPackageManager()
                .getPackageInfo(settings.getPackageName(), 0).versionName;
        runTestOnUiThread(() -> {
            TextView version = settings.findViewById(R.id.txt_about_version);
            TextView detail = settings.findViewById(R.id.txt_about_detail);
            assertNotNull(version);
            assertNotNull(detail);
            assertTrue("关于区必须显示真实版本号，期望包含 " + expected,
                    version.getText().toString().contains(expected));
            assertTrue("详情必须显示版本号与构建号",
                    detail.getText().toString().contains(expected));
        });
    }

    /** Clearing the cache must not touch the user's documents. */
    public void testClearingCacheLeavesDocumentsAlone() throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        com.nous.codecanvas.data.DocumentRepository repo =
                new com.nous.codecanvas.data.DocumentRepository(c);
        String id = "settings-cache-" + System.nanoTime();
        repo.saveDocument(new com.nous.codecanvas.model.CanvasDocument(
                id, "keepme.html", "<h1>不能被清理掉</h1>", System.currentTimeMillis()));
        try {
            runTestOnUiThread(() -> settings.findViewById(R.id.btn_clear_cache).performClick());
            getInstrumentation().waitForIdleSync();
            assertNotNull("清理缓存不得删除用户文档", repo.findById(id));
        } finally {
            repo.deleteDocument(id);
        }
    }
}
