package com.nous.codecanvas;

import android.content.Context;
import android.test.InstrumentationTestCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Puts the format fixtures where {@link FormatSupportTest} expects them.
 *
 * <p>They live in the app's own {@code files/fixtures} because scoped storage blocks {@code /sdcard}
 * for this uid. A plain {@code adb push} does not survive an emulator restart — the app's data
 * directory is recreated — so the suite seeds them itself from the APK's assets and can therefore
 * be run on a fresh device without any manual setup step.</p>
 */
public class FixtureSeederTest extends InstrumentationTestCase {

    /** Fixture name -> asset path. Kept in one place so the two suites cannot drift. */
    static final String[][] FIXTURES = {
            {"工艺卡片.md", "fixtures/工艺卡片.md"},
            {"flow.mmd", "fixtures/flow.mmd"},
            {"data.json", "fixtures/data.json"},
            {"records.jsonl", "fixtures/records.jsonl"},
            {"samples.csv", "fixtures/samples.csv"},
            {"config.yaml", "fixtures/config.yaml"},
            {"weld.py", "fixtures/weld.py"},
            {"gb18030.txt", "fixtures/gb18030.txt"},
            {"crlf.txt", "fixtures/crlf.txt"},
            {"wide.svg", "fixtures/wide.svg"},
            {"negative-origin.svg", "fixtures/negative-origin.svg"},
            {"percent-size.svg", "fixtures/percent-size.svg"},
            {"filtered.svg", "fixtures/filtered.svg"},
            {"clipped.svg", "fixtures/clipped.svg"},
            {"photo.png", "fixtures/photo.png"},
            {"report.pdf", "fixtures/report.pdf"},
            {"tiny.gif", "fixtures/tiny.gif"},
            {"mystery.bin", "fixtures/mystery.bin"},
    };

    public void testSeedFixturesFromAssets() throws Throwable {
        Context c = getInstrumentation().getTargetContext();
        File dir = new File(c.getFilesDir(), "fixtures");
        if (!dir.exists() && !dir.mkdirs()) fail("cannot create " + dir);
        int copied = 0;
        for (String[] entry : FIXTURES) {
            File target = new File(dir, entry[0]);
            try (InputStream in = getInstrumentation().getContext().getAssets().open(entry[1]);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                copied++;
            }
        }
        android.util.Log.i("CCFIXTURE", "seeded " + copied + " fixtures into " + dir);
        assertTrue("at least the core fixtures must be present", copied >= 10);
        assertTrue("photo.png must be non-empty", new File(dir, "photo.png").length() > 1000);
        assertTrue("report.pdf must be non-empty", new File(dir, "report.pdf").length() > 100);
    }

    /** The seeder is idempotent: running twice must not corrupt anything. */
    public void testSeedingTwiceIsSafe() throws Throwable {
        testSeedFixturesFromAssets();
        testSeedFixturesFromAssets();
        Context c = getInstrumentation().getTargetContext();
        File photo = new File(new File(c.getFilesDir(), "fixtures"), "photo.png");
        assertTrue(photo.isFile() && photo.length() > 1000);
    }
}
