package com.fine.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserResourcesTest {
    @Test public void mediaFilterHidesFragmentsWithoutDiscardingTheirUrls() {
        BrowserResources resources = new BrowserResources();
        resources.add("https://example.com/a.ts", "");
        resources.add("https://example.com/a.m3u8", "");
        assertEquals(1, resources.snapshot(true).size());
        assertEquals(2, resources.snapshot(false).size());
    }
    @Test public void signedUrlsArePreservedAndDeduplicated() {
        BrowserResources resources = new BrowserResources();
        String signed = "https://example.com/VIDEO.M3U8?sig=a%2Fb+Z&expires=123";
        resources.add(signed, ""); resources.add(signed, "");
        assertEquals(1, resources.snapshot(true).size());
        assertEquals(signed, resources.snapshot(true).get(0).url);
        assertEquals("HLS", resources.snapshot(true).get(0).type);
        resources.add(signed + "4", "");
        assertEquals(2, resources.snapshot(true).size());
    }
    @Test public void declaredMimeUpgradesExtensionlessResource() {
        BrowserResources resources = new BrowserResources();
        resources.add("https://example.com/stream?id=1", "");
        assertTrue(resources.snapshot(true).isEmpty());
        resources.add("https://example.com/stream?id=1", "application/vnd.apple.mpegurl; charset=UTF-8");
        assertEquals("HLS", resources.snapshot(true).get(0).type);
        resources.add("https://example.com/stream?id=1", "");
        assertEquals("HLS", resources.snapshot(true).get(0).type);
    }
    @Test public void inventoryIsBoundedButStillAcceptsMedia() {
        BrowserResources resources = new BrowserResources();
        for (int i = 0; i < 700; i++) resources.add("https://example.com/" + i + ".js", "");
        assertEquals(BrowserResources.LIMIT, resources.snapshot(false).size());
        resources.add("https://example.com/movie.m3u8", "");
        assertEquals(BrowserResources.LIMIT, resources.snapshot(false).size());
        assertEquals(1, resources.snapshot(true).size());
        resources.clear(); assertTrue(resources.snapshot(false).isEmpty());
    }
    @Test public void unsafeAndNonNetworkResourcesAreExcluded() {
        BrowserResources resources = new BrowserResources();
        for (String value : new String[]{"javascript:alert(1)", "file:///sdcard/a.m3u8", "blob:https://example.com/a", "https://user:secret@example.com/a.m3u8"}) resources.add(value, "video/mp4");
        assertTrue(resources.snapshot(false).isEmpty());
        assertEquals("其他", BrowserResources.classify("https://example.com/page?next=movie.m3u8", ""));
    }
    @Test public void addressAcceptsUrlsAndSearchWithoutEnablingOtherSchemes() {
        assertEquals("https://example.com", BrowserResources.address("example.com"));
        assertEquals("http://example.com/a?sig=a%2Fb", BrowserResources.address("http://example.com/a?sig=a%2Fb"));
        assertTrue(BrowserResources.address("轻量 浏览器").startsWith("https://www.bing.com/search?q="));
        for (String value : new String[]{"", "javascript:alert(1)", "file:///sdcard/a", "intent://test"}) {
            try { BrowserResources.address(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
        assertEquals("https://example.com/a?token=123", BrowserResources.sharedUrl("分享页面 https://example.com/a?token=123 标题"));
    }
}
