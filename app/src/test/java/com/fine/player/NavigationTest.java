package com.fine.player;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.view.View;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.StreamKey;
import androidx.media3.exoplayer.offline.DownloadRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class NavigationTest {
    @Test public void fullscreenAndBackKeepPlayerPageUntilSecondBack() {
        Intent intent = PlayerActivity.online(RuntimeEnvironment.getApplication(), "https://example.com/v.m3u8", "测试");
        try (ActivityController<PlayerActivity> controller = Robolectric.buildActivity(PlayerActivity.class, intent).create()) {
            PlayerActivity activity = controller.get();
            activity.findViewById(R.id.fullscreen_toggle).performClick();
            assertEquals(View.GONE, activity.findViewById(R.id.player_header).getVisibility());
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.getRequestedOrientation());
            activity.onBackPressed();
            assertFalse(activity.isFinishing());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.player_header).getVisibility());
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
            activity.onBackPressed();
            assertTrue(activity.isFinishing());
        }
    }
    @Test public void offlineNavigationPreservesTrackSelectionAndTaskIdentity() {
        DownloadRequest original = new DownloadRequest.Builder("task-with-gaps", Uri.parse("https://example.com/v.m3u8"))
                .setMimeType(MimeTypes.APPLICATION_M3U8).setStreamKeys(Collections.singletonList(new StreamKey(0, 1)))
                .setData("视频".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build();
        Intent intent = PlayerActivity.offline(RuntimeEnvironment.getApplication(), original);
        DownloadRequest restored = intent.getParcelableExtra("download");
        assertEquals(original, restored);
        assertEquals("task-with-gaps", restored.id);
    }
    @Test public void invalidPlayerRequestClosesWithoutCreatingPlayer() {
        try (ActivityController<PlayerActivity> controller = Robolectric.buildActivity(PlayerActivity.class).create()) {
            assertTrue(controller.get().isFinishing());
            assertNull(controller.get().findViewById(R.id.player_view));
        }
    }
}
