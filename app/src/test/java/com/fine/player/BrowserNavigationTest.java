package com.fine.player;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class BrowserNavigationTest {
    @Test public void sharedPageLoadsInRestrictedWebView() {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "网页 https://example.com/watch?id=1");
        try (ActivityController<BrowserActivity> controller = Robolectric.buildActivity(BrowserActivity.class, intent).create()) {
            BrowserActivity activity = controller.get();
            WebView web = activity.findViewById(R.id.browser_web);
            assertEquals("https://example.com/watch?id=1", shadowOf(web).getLastLoadedUrl());
            assertTrue(web.getSettings().getJavaScriptEnabled());
            assertFalse(web.getSettings().getAllowFileAccess());
            assertFalse(web.getSettings().getAllowContentAccess());
            activity.findViewById(R.id.browser_resources).performClick();
            assertTrue(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().isShowing());
        }
    }
    @Test public void externalIntentsCannotLoadLocalOrScriptUrls() {
        assertEquals("", BrowserActivity.incomingUrl(new Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/test"))));
        assertEquals("", BrowserActivity.incomingUrl(new Intent(Intent.ACTION_VIEW, Uri.parse("javascript:alert(1)"))));
        assertEquals("https://example.com/", BrowserActivity.incomingUrl(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))));
    }
    @Test public void tabsKeepTheirOwnWebViewAndClosingReturnsToPreviousPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/watch"));
        try (ActivityController<BrowserActivity> controller = Robolectric.buildActivity(BrowserActivity.class, intent).create()) {
            BrowserActivity activity = controller.get();
            WebView first = activity.findViewById(R.id.browser_web);
            activity.findViewById(R.id.browser_tabs).performClick();
            android.app.AlertDialog tabs = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            findText((android.view.ViewGroup) tabs.getWindow().getDecorView(), "＋ 新建标签页").performClick();
            WebView second = activity.findViewById(R.id.browser_web);
            assertNotSame(first, second);
            assertEquals(android.view.View.INVISIBLE, second.getVisibility());
            activity.onBackPressed();
            assertSame(first, activity.findViewById(R.id.browser_web));
            assertEquals("https://example.com/watch", shadowOf(first).getLastLoadedUrl());
            activity.findViewById(R.id.browser_home).performClick();
            assertEquals(android.view.View.INVISIBLE, first.getVisibility());
        }
    }
    private static android.widget.TextView findText(android.view.ViewGroup group, String text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) child).getText())) return (android.widget.TextView) child;
            if (child instanceof android.view.ViewGroup) { android.widget.TextView result = findText((android.view.ViewGroup)child, text); if (result != null) return result; }
        }
        return null;
    }
    @Test public void discoveredHlsCanBeHandedToDownloadAndNavigationClearsIt() {
        try (ActivityController<BrowserActivity> controller = Robolectric.buildActivity(BrowserActivity.class).create()) {
            BrowserActivity activity = controller.get();
            WebView web = activity.findViewById(R.id.browser_web);
            android.webkit.WebViewClient client = web.getWebViewClient();
            client.onPageStarted(web, "https://example.com/watch", null);
            android.webkit.WebResourceRequest request = new android.webkit.WebResourceRequest() {
                public Uri getUrl() { return Uri.parse("https://example.com/movie.m3u8?token=a%2Fb"); }
                public boolean isForMainFrame() { return false; }
                public boolean isRedirect() { return false; }
                public boolean hasGesture() { return false; }
                public String getMethod() { return "GET"; }
                public java.util.Map<String, String> getRequestHeaders() { return java.util.Collections.emptyMap(); }
            };
            assertNull(client.shouldInterceptRequest(web, request));
            activity.findViewById(R.id.browser_resources).performClick();
            android.app.AlertDialog inventory = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            android.widget.ListView list = findList((android.view.ViewGroup)inventory.getWindow().getDecorView());
            assertNotNull(list);
            assertEquals(1, list.getCount());
            list.performItemClick(null, 0, 0);
            android.app.AlertDialog actions = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            findText((android.view.ViewGroup)actions.getWindow().getDecorView(), "下载 M3U8").performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            Intent next = shadowOf(activity).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
            assertEquals("https://example.com/movie.m3u8?token=a%2Fb", next.getStringExtra("sniffed_url"));
            client.onPageStarted(web, "https://example.com/next", null);
            assertEquals(0, list.getCount());
        }
    }
    private static android.widget.ListView findList(android.view.ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof android.widget.ListView) return (android.widget.ListView) child;
            if (child instanceof android.view.ViewGroup) {
                android.widget.ListView result = findList((android.view.ViewGroup) child);
                if (result != null) return result;
            }
        }
        return null;
    }
}
