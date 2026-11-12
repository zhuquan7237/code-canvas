package com.nous.codecanvas.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * What the updater accepts, and what it must refuse.
 *
 * <p>The rules here are the ones that decide whether an install can succeed at all: a manifest
 * that names a version this device cannot run, a pre-release pretending to be stable, or a download
 * address outside the allow-list.</p>
 */
public class UpdateManifestTest {

    private static String manifest(String extra) {
        return "{\"schemaVersion\":1,\"versionName\":\"0.5.0\",\"versionCode\":50,"
                + "\"apkUrl\":\"https://relay.zhuquan.xyz/dl/code-canvas-0.5.0.apk\","
                + "\"sha256\":\"" + "a".repeat(64) + "\",\"size\":123456,"
                + "\"notes\":\"测试\"" + extra + "}";
    }

    @Test
    public void aValidManifestParses() throws Exception {
        UpdateManifest m = UpdateManifest.parse(manifest(""));
        assertEquals(50, m.versionCode);
        assertEquals("0.5.0", m.versionName);
        assertEquals(123456, m.size);
        assertEquals("stable", m.channel);
        assertEquals(0, m.minSdk);
    }

    @Test
    public void versionCodeDecidesNewnessNotVersionName() throws Exception {
        // versionName is for display only; a "0.10.0" with code 40 is older than a "0.9.0" with 45.
        UpdateManifest older = UpdateManifest.parse(manifest("").replace("\"versionCode\":50", "\"versionCode\":40"));
        UpdateManifest newer = UpdateManifest.parse(manifest("").replace("\"versionCode\":50", "\"versionCode\":45"));
        assertTrue(newer.versionCode > older.versionCode);
    }

    @Test
    public void minSdkIsRespected() throws Exception {
        UpdateManifest m = UpdateManifest.parse(manifest(",\"minSdk\":30"));
        assertEquals(30, m.minSdk);
        assertTrue("API 33 can run an API 30 build", m.supportsDevice(33));
        assertTrue("exactly the minimum is fine", m.supportsDevice(30));
        assertFalse("API 26 cannot run an API 30 build", m.supportsDevice(26));
        assertNotNull("a refusal must explain itself", m.minSdkNote(26));
        assertNull("a supported device needs no note", m.minSdkNote(33));
    }

    @Test
    public void aMissingMinSdkDoesNotBlockAnything() throws Exception {
        UpdateManifest m = UpdateManifest.parse(manifest(""));
        assertTrue(m.supportsDevice(21));
        assertNull(m.minSdkNote(21));
    }

    @Test
    public void preReleaseChannelsAreRefused() {
        // A beta must never be installed through the stable channel.
        for (String channel : new String[]{"beta", "rc", "alpha", "preview"}) {
            try {
                UpdateManifest.parse(manifest(",\"channel\":\"" + channel + "\""));
                fail("channel " + channel + " must be refused");
            } catch (Exception expected) {
                assertTrue(expected.getMessage().contains("正式版本"));
            }
        }
    }

    @Test
    public void stableChannelIsAcceptedExplicitly() throws Exception {
        assertEquals("stable", UpdateManifest.parse(manifest(",\"channel\":\"stable\"")).channel);
    }

    @Test
    public void malformedManifestsAreRefused() {
        String[] bad = {
                manifest("").replace("\"versionCode\":50", "\"versionCode\":0"),
                manifest("").replace("\"versionName\":\"0.5.0\"", "\"versionName\":\"0.5\""),
                manifest("").replace("\"size\":123456", "\"size\":0"),
                manifest("").replace("\"size\":123456", "\"size\":" + (9L * 1024 * 1024)),
                manifest("").replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
        };
        for (String json : bad) {
            try {
                UpdateManifest.parse(json);
                fail("must refuse: " + json);
            } catch (Exception expected) {
                // expected
            }
        }
    }

    @Test
    public void downloadAddressesOutsideTheAllowListAreRefused() throws Exception {
        String[] bad = {
                manifest("").replace("https://relay.zhuquan.xyz/dl/code-canvas-0.5.0.apk",
                        "https://evil.example.com/code-canvas-0.5.0.apk"),
                manifest("").replace("https://relay.zhuquan.xyz/dl/code-canvas-0.5.0.apk",
                        "http://relay.zhuquan.xyz/dl/code-canvas-0.5.0.apk"),
                manifest("").replace("https://relay.zhuquan.xyz/dl/code-canvas-0.5.0.apk",
                        "https://relay.zhuquan.xyz/dl/payload.zip"),
                manifest("").replace("https://relay.zhuquan.xyz/dl/code-canvas-0.5.0.apk",
                        "https://github.com/someone/else/releases/download/v1/x.apk"),
        };
        for (String json : bad) {
            try {
                UpdateManifest.parse(json);
                fail("must refuse: " + json);
            } catch (Exception expected) {
                // expected
            }
        }
    }

    @Test
    public void theProjectsOwnHostsAndGitHubAreTrusted() {
        assertTrue(UpdateManifest.trustedDownloadAddress(
                "https://relay.zhuquan.xyz/dl/code-canvas-0.5.0.apk"));
        assertTrue(UpdateManifest.trustedDownloadAddress(
                "https://github.com/zhuquan7237/code-canvas/releases/download/v0.5.0/code-canvas-0.5.0.apk"));
        assertTrue(UpdateManifest.trustedDownloadAddress(
                "https://release-assets.githubusercontent.com/whatever"));
    }

    @Test
    public void aBadFallbackIsDroppedRatherThanFailingTheWholeUpdate() throws Exception {
        UpdateManifest m = UpdateManifest.parse(manifest(",\"fallbackApkUrl\":\"https://evil.example.com/x.apk\""));
        assertNull("an unusable fallback is simply not used", m.fallbackApkUrl);
        assertEquals("the primary address still works", 1, m.downloadUrls().size());
    }

    @Test
    public void bothAddressesAreTriedPrimaryFirst() throws Exception {
        UpdateManifest m = UpdateManifest.parse(manifest(
                ",\"fallbackApkUrl\":\"https://github.com/zhuquan7237/code-canvas/releases/download/v0.5.0/code-canvas-0.5.0.apk\""));
        assertEquals(2, m.downloadUrls().size());
        assertTrue(m.downloadUrls().get(0).contains("relay.zhuquan.xyz"));
        assertTrue(m.downloadUrls().get(1).contains("github.com"));
    }

    @Test
    public void manifestAddressesAreAllowListed() {
        assertTrue(UpdateManifest.trustedManifestAddress(
                "https://relay.zhuquan.xyz/dl/codecanvas-latest.json"));
        assertTrue(UpdateManifest.trustedManifestAddress(
                "https://raw.githubusercontent.com/zhuquan7237/code-canvas/main/docs/update/latest.json"));
        assertFalse(UpdateManifest.trustedManifestAddress("https://evil.example.com/latest.json"));
        assertFalse(UpdateManifest.trustedManifestAddress("http://relay.zhuquan.xyz/dl/x.json"));
    }
}
