package com.fine.player;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class MediaPreviewsTest {
    @Test public void unknownDurationIsNeverPresentedAsZeroAndLiveIsDistinct() {
        MediaPreviews.Info info = new MediaPreviews.Info();
        assertEquals("时长待读取", MediaPreviews.duration(info));
        info.duration = 3661000;
        assertEquals("1:01:01", MediaPreviews.duration(info));
        info.live = true;
        assertEquals("直播", MediaPreviews.duration(info));
    }
    @Test public void metadataIsAssociatedWithExactSignedResource() {
        MediaPreviews.clear();
        BrowserResources.Entry first = new BrowserResources.Entry("https://example.com/stream?token=1", "HLS");
        BrowserResources.Entry second = new BrowserResources.Entry("https://example.com/stream?token=2", "HLS");
        MediaPreviews.info(first.url).title = "真正的视频标题";
        assertEquals("真正的视频标题", MediaPreviews.name(first));
        assertEquals("stream", MediaPreviews.name(second));
        assertFalse(MediaPreviews.canPreview(new BrowserResources.Entry("https://example.com/a.ts", "媒体分片")));
    }
}
