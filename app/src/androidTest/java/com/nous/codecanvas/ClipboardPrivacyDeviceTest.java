package com.nous.codecanvas;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.EditorActivity;
import com.nous.codecanvas.ui.MainActivity;
import com.nous.codecanvas.ui.SettingsActivity;
import com.nous.codecanvas.util.CanvasClipboard;
import com.nous.codecanvas.util.CanvasPrefs;

import java.util.concurrent.atomic.AtomicBoolean;

public class ClipboardPrivacyDeviceTest extends InstrumentationTestCase {
    private static final String KEY = "clipboard_auto_detect";
    private static final String MARKUP = "<html><body><h1>qa-private-fixture</h1></body></html>";
    private static final String FENCES = "QA fixture\n~~~python\nprint('synthetic')\n~~~\n~~~json\n{\"ok\":true}\n~~~";
    private Context context;
    private ClipboardManager clipboard;
    private SharedPreferences prefs;
    private boolean hadKey, beforeValue;
    private MainActivity home;
    private Activity cover;
    private EditorActivity editor;
    private CanvasDocument fixture;
    private boolean clipboardSet;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        prefs = context.getSharedPreferences("codecanvas_prefs", Context.MODE_PRIVATE);
        hadKey = prefs.contains(KEY);
        beforeValue = prefs.getBoolean(KEY, false);
        prefs.edit().remove(KEY).commit();
    }

    @Override protected void tearDown() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            if (clipboardSet) clipboard.setPrimaryClip(ClipData.newPlainText("test cleanup", ""));
            if (editor != null && !editor.isDestroyed()) editor.finish();
            if (cover != null && !cover.isDestroyed()) cover.finish();
            if (home != null && !home.isDestroyed()) home.finishAndRemoveTask();
        });
        if (fixture != null) new DocumentRepository(context).deleteDocument(fixture.getId());
        if (hadKey) prefs.edit().putBoolean(KEY, beforeValue).commit();
        else prefs.edit().remove(KEY).commit();
        getInstrumentation().waitForIdleSync();
        super.tearDown();
    }

    private void clip(ClipData data) {
        getInstrumentation().runOnMainSync(() -> {
            clipboard.setPrimaryClip(data);
            clipboardSet = true;
        });
    }

    private void plain(String text) { clip(ClipData.newPlainText("synthetic QA", text)); }

    private void openHome() {
        home = (MainActivity) getInstrumentation().startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        getInstrumentation().waitForIdleSync();
        assertTrue(focus(home));
    }

    private boolean focus(Activity activity) {
        long stop = SystemClock.uptimeMillis() + 8000;
        AtomicBoolean focused = new AtomicBoolean();
        do {
            getInstrumentation().runOnMainSync(() -> focused.set(activity.hasWindowFocus()));
            if (focused.get()) return true;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < stop);
        return false;
    }

    private boolean banner() {
        AtomicBoolean showing = new AtomicBoolean();
        getInstrumentation().runOnMainSync(() -> showing.set(home.isClipboardBannerShowingForTest()));
        return showing.get();
    }

    private void awaitBanner(boolean expected) {
        long end = SystemClock.uptimeMillis() + 4500;
        do {
            if (banner() == expected) return;
            SystemClock.sleep(120);
        } while (SystemClock.uptimeMillis() < end);
        assertEquals(expected, banner());
    }

    private boolean hasText(AccessibilityNodeInfo n, String target) {
        if (n == null) return false;
        if (n.getText() != null && n.getText().toString().contains(target)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            if (hasText(n.getChild(i), target)) return true;
        }
        return false;
    }

    private boolean awaitText(String text, long ms) {
        long end = SystemClock.uptimeMillis() + ms;
        do {
            if (hasText(getInstrumentation().getUiAutomation().getRootInActiveWindow(), text)) return true;
            SystemClock.sleep(120);
        } while (SystemClock.uptimeMillis() < end);
        return false;
    }

    public void testAbsentPreferenceIsOffAndExplicitChoicesPersist() {
        assertFalse(CanvasPrefs.clipboardAutoDetect(context));
        CanvasPrefs.setClipboardAutoDetect(context, true);
        assertTrue(prefs.contains(KEY));
        assertTrue(CanvasPrefs.clipboardAutoDetect(context));
        CanvasPrefs.setClipboardAutoDetect(context, false);
        assertTrue(prefs.contains(KEY));
        assertFalse(CanvasPrefs.clipboardAutoDetect(context));
    }

    public void testOptInOffersAreRedactedAndClearAcrossBackgroundAndSettingChange() {
        openHome();
        plain(MARKUP);
        getInstrumentation().runOnMainSync(home::offerClipboardForTest);
        home.awaitClipboardReadForTest();
        assertFalse(banner());

        CanvasPrefs.setClipboardAutoDetect(context, true);
        getInstrumentation().runOnMainSync(home::offerClipboardForTest);
        awaitBanner(true);
        getInstrumentation().runOnMainSync(() ->
                assertEquals("剪贴板中有代码", ((TextView) home.findViewById(R.id.banner_title)).getText().toString()));
        assertFalse(awaitText("qa-private-fixture", 450));

        cover = getInstrumentation().startActivitySync(new Intent(context, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertTrue(focus(cover));
        assertFalse(banner());
        plain("ordinary message, no markup");
        getInstrumentation().runOnMainSync(cover::finish);
        assertTrue(focus(home));
        SystemClock.sleep(380);
        assertFalse(banner());

        plain(MARKUP);
        awaitBanner(true);
        getInstrumentation().runOnMainSync(() -> {
            home.offerClipboardForTest();
            CanvasPrefs.setClipboardAutoDetect(context, false);
        });
        home.awaitClipboardReadForTest();
        SystemClock.sleep(380);
        assertFalse(banner());

        CanvasPrefs.setClipboardAutoDetect(context, true);
        ClipData marked = ClipData.newPlainText("synthetic sensitive", MARKUP);
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(CanvasClipboard.SENSITIVE, true);
        marked.getDescription().setExtras(extras);
        clip(marked);
        getInstrumentation().runOnMainSync(home::offerClipboardForTest);
        home.awaitClipboardReadForTest();
        SystemClock.sleep(320);
        assertFalse(banner());
        assertFalse(awaitText("qa-private-fixture", 400));
    }

    public void testDirectTextReaderRejectsUriAndOversizeAndRespectsSensitiveFlag() {
        ClipData uri = ClipData.newRawUri("unresolved fixture", Uri.parse("content://test.example/data"));
        assertEquals(CanvasClipboard.Status.NOT_TEXT, CanvasClipboard.fromClip(uri, false).status);
        assertEquals(CanvasClipboard.Status.EMPTY, CanvasClipboard.fromClip(null, false).status);
        ClipData big = ClipData.newPlainText("synthetic large", "x".repeat(CanvasClipboard.MAX_PASTE_CHARS + 1));
        assertEquals(CanvasClipboard.Status.TOO_LARGE, CanvasClipboard.fromClip(big, false).status);
        ClipData text = ClipData.newHtmlText("synthetic rich", MARKUP, "<strong>not the raw text</strong>");
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(CanvasClipboard.SENSITIVE, true);
        text.getDescription().setExtras(extras);
        assertEquals(CanvasClipboard.Status.SENSITIVE, CanvasClipboard.fromClip(text, true).status);
        assertEquals(MARKUP, CanvasClipboard.fromClip(text, false).text);
    }

    public void testExplicitHomeAndEditorPasteStillOfferBothFencesWhenDetectionOff() throws Exception {
        openHome();
        plain(FENCES);
        getInstrumentation().runOnMainSync(() -> home.findViewById(R.id.btn_quick_paste_preview).performClick());
        assertTrue(awaitText("保留完整原文", 8000));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

        fixture = new CanvasDocument("paste-qa-" + System.nanoTime(), "paste-qa.txt", "existing draft",
                System.currentTimeMillis());
        new DocumentRepository(context).saveDocument(fixture);
        editor = (EditorActivity) getInstrumentation().startActivitySync(new Intent(context, EditorActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EditorActivity.EXTRA_DOC_ID, fixture.getId()));
        assertTrue(focus(editor));
        getInstrumentation().runOnMainSync(() -> editor.findViewById(R.id.btn_paste).performClick());
        assertTrue(awaitText("保留完整原文", 8000));
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        assertEquals("existing draft", new DocumentRepository(context).findById(fixture.getId()).getContent());
    }
}
