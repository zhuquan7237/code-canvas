package com.nous.codecanvas.update;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * How the app chooses among its manifest sources.
 *
 * <p>The rule used to be "the first source that answers", and that is what produced a silent
 * failure: the self-hosted mirror is queried first, so the moment the mirror lagged one release
 * behind, the app answered with the old manifest and reported "已是最新" while a newer one sat one
 * source away. No exception, no log line, no update. These cases pin the corrected rule: every
 * source that answers is considered, and the newest wins.
 */
public class UpdateSourceSelectionTest {

    private static UpdateManifest of(int versionCode) throws Exception {
        String json = "{\"schemaVersion\":1,\"versionName\":\"" + versionCode + ".0.0\","
                + "\"versionCode\":" + versionCode + ","
                + "\"apkUrl\":\"https://relay.zhuquan.xyz/dl/code-canvas-0." + versionCode + ".apk\","
                + "\"size\":80000,\"sha256\":\"" + "a".repeat(64) + "\"}";
        return UpdateManifest.parse(json);
    }

    /** The exact shape of the real defect: mirror first, older, and it used to win. */
    @Test
    public void aLaggingMirrorMustNotHideANewerRelease() throws Exception {
        UpdateManifest mirror = of(10);
        UpdateManifest upstream = of(30);
        assertEquals(30, AppUpdater.newestOf(Arrays.asList(mirror, upstream)).versionCode);
    }

    @Test
    public void orderOfAnswersDoesNotDecide() throws Exception {
        assertEquals(30, AppUpdater.newestOf(Arrays.asList(of(30), of(10))).versionCode);
        assertEquals(30, AppUpdater.newestOf(Arrays.asList(of(10), of(30))).versionCode);
    }

    /** One lone source still counts; being the only voice is not the same as being wrong. */
    @Test
    public void aSingleAnswerIsUsed() throws Exception {
        assertEquals(10, AppUpdater.newestOf(Collections.singletonList(of(10))).versionCode);
    }

    @Test
    public void failedSourcesContributeNothing() throws Exception {
        List<UpdateManifest> mixed = new ArrayList<>();
        mixed.add(null);
        mixed.add(of(30));
        mixed.add(null);
        assertEquals(30, AppUpdater.newestOf(mixed).versionCode);
    }

    @Test
    public void noAnswerAtAllIsNull() {
        assertNull(AppUpdater.newestOf(Collections.<UpdateManifest>emptyList()));
        assertNull(AppUpdater.newestOf(Arrays.<UpdateManifest>asList(null, null)));
    }

    /** Two sources agreeing is the normal case, and it must not flip-flop between them. */
    @Test
    public void equalVersionsKeepTheFirstAnswer() throws Exception {
        UpdateManifest first = of(30);
        assertSame(first, AppUpdater.newestOf(Arrays.asList(first, of(30))));
    }
}
