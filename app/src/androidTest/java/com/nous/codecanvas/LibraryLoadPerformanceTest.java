package com.nous.codecanvas;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.widget.TextView;

import com.nous.codecanvas.data.DocumentRepository;
import com.nous.codecanvas.model.CanvasDocument;
import com.nous.codecanvas.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * A large library must open quickly and stay responsive.
 *
 * <p>Reported as "重点优化一下多文件存储时打开软件加载的稳定性和速度还有流畅性". The costs that grew with the
 * library were all in the list's metadata: measuring the UTF-8 length by materialising a second copy
 * of every document, counting lines with a regex split, and re-detecting each document's type on
 * every filter pass. This seeds a realistic library and measures what a user feels.</p>
 */
public class LibraryLoadPerformanceTest extends InstrumentationTestCase {

    private static final int DOCUMENTS = 120;

    private final List<String> created = new ArrayList<>();

    private static String body(int i) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 800 600\">\n");
        sb.append("<rect width=\"800\" height=\"600\" fill=\"#eef\"/>\n");
        for (int k = 0; k < 40; k++) {
            sb.append("<circle cx=\"").append(20 + k * 19).append("\" cy=\"").append(100 + (k % 7) * 60)
                    .append("\" r=\"9\" fill=\"#2f6fdb\"/>\n");
        }
        sb.append("<text x=\"40\" y=\"560\" font-size=\"28\">document ").append(i).append("</text>\n");
        sb.append("</svg>\n");
        return sb.toString();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context c = getInstrumentation().getTargetContext();
        DocumentRepository repository = new DocumentRepository(c);
        // Clear anything a previous run left behind, so the measurement is of exactly DOCUMENTS.
        try {
            for (CanvasDocument existing : repository.getAllDocuments()) {
                String title = existing.getTitle();
                if (title != null && title.startsWith("art-") && title.endsWith(".svg")) {
                    repository.deleteDocument(existing.getId());
                }
            }
        } catch (Exception ignored) {
        }
        for (int i = 0; i < DOCUMENTS; i++) {
            String id = "lib-" + System.nanoTime() + "-" + i;
            CanvasDocument doc = new CanvasDocument(id, "art-" + i + ".svg", body(i), System.currentTimeMillis());
            doc.setKindName("SVG");
            repository.saveDocument(doc);
            created.add(id);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        Context c = getInstrumentation().getTargetContext();
        DocumentRepository repository = new DocumentRepository(c);
        for (String id : created) {
            try {
                repository.deleteDocument(id);
            } catch (Exception ignored) {
            }
        }
        // Verify the cleanup actually worked. A previous version swept by title, which silently
        // matched nothing, and 278 leftover documents then broke every test that counts files.
        try {
            int remaining = 0;
            for (CanvasDocument doc : repository.getAllDocuments()) {
                if (created.contains(doc.getId())) remaining++;
            }
            assertEquals("性能测试必须把自己建的文件全部删掉，否则会污染后续测试", 0, remaining);
        } catch (Exception e) {
            throw new AssertionError("清理性能测试数据失败", e);
        }
        created.clear();
        super.tearDown();
    }

    /** Opening the app with this many files must not stall. */
    public void testLibraryOpensQuickly() throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Warm the activity class once so the measurement is the load, not class initialisation.
        MainActivity warm = (MainActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> {
            if (!warm.isFinishing()) warm.finish();
        });
        getInstrumentation().waitForIdleSync();
        Thread.sleep(400);

        long[] samples = new long[3];
        MainActivity activity = null;
        for (int n = 0; n < samples.length; n++) {
            if (activity != null) {
                final MainActivity previous = activity;
                getInstrumentation().runOnMainSync(() -> {
                    if (!previous.isFinishing()) previous.finish();
                });
                getInstrumentation().waitForIdleSync();
                Thread.sleep(300);
            }
            long t0 = SystemClock.uptimeMillis();
            activity = (MainActivity) getInstrumentation().startActivitySync(i);
            getInstrumentation().waitForIdleSync();
            samples[n] = SystemClock.uptimeMillis() - t0;
        }
        java.util.Arrays.sort(samples);
        long median = samples[1];
        android.util.Log.i("CCLIB", DOCUMENTS + " files open samples="
                + java.util.Arrays.toString(samples) + " median=" + median + " ms");
        assertTrue(DOCUMENTS + " 个文件打开耗时中位数 " + median + "ms（样本 "
                + java.util.Arrays.toString(samples) + "），超过 2000ms 用户会感到卡",
                median < 2000);

        // The list must actually show them, not just start fast.
        final TextView count = activity.findViewById(R.id.txt_file_count);
        assertNotNull(count);
        final String text = count.getText().toString();
        android.util.Log.i("CCLIB", "count label=" + text);
        // The device may already hold documents from earlier tests, so assert a lower bound
        // rather than an exact count: what matters is that the whole seeded library is listed.
        int shown = -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        while (m.find()) shown = Math.max(shown, Integer.parseInt(m.group(1)));
        assertTrue("列表必须列出全部 " + DOCUMENTS + " 个文件，实际显示 \"" + text + "\"",
                shown >= DOCUMENTS);

        final MainActivity last = activity;
        getInstrumentation().runOnMainSync(() -> {
            if (!last.isFinishing()) last.finish();
        });
        getInstrumentation().waitForIdleSync();
    }

    /** Typing in the home search box must not re-scan every document's text per keystroke. */
    public void testHomeSearchStaysResponsive() throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final MainActivity activity = (MainActivity) getInstrumentation().startActivitySync(i);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(600);

        final android.widget.EditText search = activity.findViewById(R.id.edit_search);
        assertNotNull(search);

        // The list refilter is debounced, so the per-keystroke cost is what the user feels while
        // typing; the rebuild happens once the burst settles. Assert both: each keystroke must be
        // cheap, and the final result must be correct.
        // Measure the work a keystroke does *now*, not the debounced rebuild that follows it.
        // runOnMainSync waits for the main looper to go idle, which would also wait out the
        // debounce delay and report it as keystroke latency.
        long worst = 0;
        for (char ch : "document 42".toCharArray()) {
            final long[] elapsed = new long[1];
            getInstrumentation().runOnMainSync(() -> {
                long t0 = SystemClock.uptimeMillis();
                search.setText(search.getText().toString() + ch);
                elapsed[0] = SystemClock.uptimeMillis() - t0;
            });
            worst = Math.max(worst, elapsed[0]);
        }
        android.util.Log.i("CCLIB", "worst home-search keystroke=" + worst + " ms");
        assertTrue("主页搜索按键延迟 " + worst + "ms，超过 300ms 就是明显卡顿", worst < 300);

        // Let the debounce fire, then confirm the filter actually ran.
        getInstrumentation().waitForIdleSync();
        Thread.sleep(600);
        getInstrumentation().waitForIdleSync();
        final android.widget.TextView countLabel = activity.findViewById(R.id.txt_file_count);
        final String after = countLabel.getText().toString();
        android.util.Log.i("CCLIB", "after search: " + after);
        assertNotNull(after);
        assertTrue("搜索后计数必须更新，实际 \"" + after + "\"", after.contains("·"));

        getInstrumentation().runOnMainSync(() -> {
            if (!activity.isFinishing()) activity.finish();
        });
        getInstrumentation().waitForIdleSync();
    }
}
