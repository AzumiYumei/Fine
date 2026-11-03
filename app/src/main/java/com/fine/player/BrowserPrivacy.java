package com.fine.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;
import android.webkit.WebView;
import java.net.URI;
import java.util.Locale;

final class BrowserPrivacy {
    private final SharedPreferences prefs;
    volatile boolean blockTrackers;
    BrowserPrivacy(Context context) {
        prefs = context.getSharedPreferences("browser_privacy", 0);
        blockTrackers = enabled("trackers", true);
    }
    boolean enabled(String key, boolean fallback) { return prefs.getBoolean(key, fallback); }
    void set(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        blockTrackers = enabled("trackers", true);
    }
    void apply(WebView web) {
        web.getSettings().setGeolocationEnabled(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) web.getSettings().setSafeBrowsingEnabled(true);
        web.getSettings().setJavaScriptEnabled(!enabled("disable_js", false));
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, !enabled("third_party", true));
    }
    // Deliberately small, auditable list. This is not comprehensive tracking protection.
    static boolean knownTracker(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : new String[]{"google-analytics.com", "doubleclick.net", "googletagmanager.com",
                    "scorecardresearch.com", "hotjar.com", "clarity.ms", "hm.baidu.com"})
                if (host.equals(domain) || host.endsWith("." + domain)) return true;
        } catch (Exception ignored) { }
        return false;
    }
}
