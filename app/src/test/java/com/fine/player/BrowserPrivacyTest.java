package com.fine.player;

import android.webkit.WebView;
import android.webkit.CookieManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class BrowserPrivacyTest {
    @Test public void trackerMatchRespectsHostBoundaries() {
        assertTrue(BrowserPrivacy.knownTracker("https://www.google-analytics.com/collect"));
        assertTrue(BrowserPrivacy.knownTracker("https://hm.baidu.com/hm.js"));
        assertFalse(BrowserPrivacy.knownTracker("https://google-analytics.com.evil.test/a"));
        assertFalse(BrowserPrivacy.knownTracker("https://notgoogle-analytics.com/a"));
        assertFalse(BrowserPrivacy.knownTracker("https://example.com/?next=doubleclick.net"));
    }
    @Test public void privacyDefaultsAndSwitchesApplyToEveryWebView() {
        BrowserPrivacy privacy = new BrowserPrivacy(RuntimeEnvironment.getApplication());
        WebView first = new WebView(RuntimeEnvironment.getApplication());
        WebView second = new WebView(RuntimeEnvironment.getApplication());
        try {
            privacy.apply(first);
            assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(first));
            assertFalse(first.getSettings().getAllowFileAccess());
            assertFalse(first.getSettings().getAllowFileAccessFromFileURLs());
            assertFalse(first.getSettings().getAllowUniversalAccessFromFileURLs());
            assertTrue(first.getSettings().getJavaScriptEnabled());
            privacy.set("disable_js", true); privacy.apply(first); privacy.apply(second);
            assertFalse(first.getSettings().getJavaScriptEnabled());
            assertFalse(second.getSettings().getJavaScriptEnabled());
            privacy.set("trackers", false); assertFalse(privacy.blockTrackers);
        } finally { first.destroy(); second.destroy(); }
    }
}
