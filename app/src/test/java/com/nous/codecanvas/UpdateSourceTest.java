package com.nous.codecanvas;

import com.nous.codecanvas.update.AppUpdater;
import com.nous.codecanvas.update.UpdateManifest;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The update channel has to reach a host that actually answers on a mainland network, while still
 * refusing to install from anywhere else. These cases pin both halves down.
 */
public class UpdateSourceTest {

    private static String manifest(String apkUrl) {
        return "{\"schemaVersion\":1,\"versionName\":\"0.1.4\",\"versionCode\":5,"
                + "\"apkUrl\":\"" + apkUrl + "\",\"size\":80000,\"sha256\":\"" + "a".repeat(64) + "\"}";
    }

    @Test
    public void acceptsTheProjectsOwnHostAsUpdateSource() throws Exception {
        assertNotNull(UpdateManifest.parse(
                manifest("https://relay.zhuquan.xyz/dl/code-canvas-0.1.4.apk")));
        assertTrue(UpdateManifest.trustedManifestAddress("https://relay.zhuquan.xyz/dl/codecanvas-latest.json"));
    }

    @Test
    public void stillAcceptsGitHubAsFallback() throws Exception {
        assertNotNull(UpdateManifest.parse(manifest(
                "https://github.com/zhuquan7237/code-canvas/releases/download/v0.1.4/code-canvas-0.1.4.apk")));
        assertTrue(UpdateManifest.trustedManifestAddress(
                "https://raw.githubusercontent.com/zhuquan7237/code-canvas/main/docs/update/latest.json"));
    }

    @Test
    public void refusesImpostorsOfBothHosts() {
        for (String bad : new String[]{
                "https://relay.zhuquan.xyz.evil.example/dl/code-canvas-0.1.4.apk",
                "https://evil.example/dl/code-canvas-0.1.4.apk",
                "http://relay.zhuquan.xyz/dl/code-canvas-0.1.4.apk",
                "https://relay.zhuquan.xyz/other/code-canvas-0.1.4.apk",
                "https://relay.zhuquan.xyz/dl/code-canvas-0.1.4.apk.exe",
                "https://user:pass@relay.zhuquan.xyz/dl/code-canvas-0.1.4.apk",
                "https://relay.zhuquan.xyz:8443/dl/code-canvas-0.1.4.apk",
                "https://relay.zhuquan.xyz/dl/code-canvas-0.1.4.apk#frag"}) {
            assertFalse(bad + " must not be trusted", UpdateManifest.trustedDownloadAddress(bad));
            try {
                UpdateManifest.parse(manifest(bad));
                throw new AssertionError("unsafe manifest accepted: " + bad);
            } catch (AssertionError propagate) {
                throw propagate;
            } catch (Exception expected) {
                // rejected, as intended
            }
        }
    }

    @Test
    public void manifestSourcesLeadWithTheSelfHostedMirror() {
        String[] sources = AppUpdater.manifestSourcesForTest();
        assertTrue("first source must be the self-hosted mirror, was " + sources[0],
                sources[0].startsWith("https://relay.zhuquan.xyz/dl/"));
        for (String source : sources) {
            assertTrue(source + " must be an allowed manifest address",
                    UpdateManifest.trustedManifestAddress(source));
        }
    }
}
