package com.fine.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class LinksTest {
    @Test public void preservesSignedAndExtensionlessUrls() {
        assertEquals("https://example.com/play?token=a%2Fb&x=1", Links.normalize("  https://example.com/play?token=a%2Fb&x=1 \n"));
    }
    @Test public void acceptsHttpForLegacyHls() {
        assertEquals("http://192.168.1.2/video.m3u8", Links.normalize("http://192.168.1.2/video.m3u8"));
    }
    @Test public void rejectsInvalidOrLocalInputs() {
        for (String bad : new String[]{"", "video.m3u8", "file:///tmp/a", "https:///path", "https://a b.com/v", "https://user:pass@example.com/v"}) {
            try { Links.normalize(bad); fail(bad); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void identityIncludesQueryString() {
        assertEquals(64, Links.id("https://a.com/v").length());
        assertEquals(Links.id("https://a.com/v"), Links.id("https://a.com/v"));
        assertNotEquals(Links.id("https://a.com/v?t=1"), Links.id("https://a.com/v?t=2"));
    }
}
