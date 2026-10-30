package com.fine.player;

import android.content.Context;
import android.net.Uri;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.hls.playlist.*;
import androidx.media3.exoplayer.offline.DownloadRequest;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class LibraryFilesTest {
    Context context = RuntimeEnvironment.getApplication();
    @Test public void storedLocationsAndMp4NavigationSurviveRecreation() throws Exception {
        LibraryFiles files = new LibraryFiles(context);
        String id = UUID.randomUUID().toString();
        files.output(id, "content://media/source.m3u8", "content://media/folder");
        files.put(id, "mp4", "content://media/result.mp4");
        files.put(id, "sourceDeleted", true);
        LibraryFiles restored = new LibraryFiles(context);
        assertEquals("content://media/result.mp4", restored.get(id, "mp4"));
        assertTrue(restored.flag(id, "sourceDeleted"));
        DownloadRequest request = new DownloadRequest.Builder(id, Uri.parse("https://example.com/a.m3u8")).build();
        android.content.Intent playback = PlayerActivity.saved(context, request, restored);
        assertEquals("content://media/result.mp4", playback.getStringExtra("local"));
        assertTrue(playback.getBooleanExtra("mp4", false));
        assertFalse(playback.hasExtra("download"));
        files.clear(id);
    }
}
