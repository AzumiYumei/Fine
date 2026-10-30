package com.fine.player;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.hls.playlist.*;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import static org.junit.Assert.*;

public class ConversionDeviceTest {
    Context context;
    File root, source, work;
    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = new File(context.getCacheDir(), "conversion-test-" + UUID.randomUUID());
        source = new File(root, "source"); work = new File(root, "work");
        assertTrue(source.mkdirs()); assertTrue(work.mkdirs());
        for (String name : new String[]{"test.m3u8", "test0.ts", "test1.ts"})
            try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("hls/" + name);
                 OutputStream out = new FileOutputStream(new File(source, name))) { OfflineHls.copy(in, out); }
    }
    @After public void cleanup() throws Exception { LibraryFiles.delete(context, Uri.fromFile(root)); }
    OfflineHls input() throws IOException {
        return new OfflineHls(new DefaultDataSource.Factory(context, PlaceholderDataSource.FACTORY),
                Uri.fromFile(new File(source, "test.m3u8")), Collections.emptyList(), Collections.emptySet());
    }
    @Test public void remuxTwoSegmentsPreservesVideoAudioAndDuration() throws Exception {
        File output = new File(work, "result.mp4");
        Mp4Converter.convert(input(), work, output, p -> { });
        Mp4Converter.validate(context, Uri.fromFile(output));
        assertTracksAndDuration(Uri.fromFile(output), 3_800_000, 4_300_000);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try { retriever.setDataSource(output.getPath()); assertNotNull(retriever.getFrameAtTime(2_500_000)); }
        finally { retriever.release(); }
    }
    @Test public void aesInputExportsOfflineAndConvertsAfterOriginalRemoved() throws Exception {
        byte[] key = new byte[16]; Arrays.fill(key, (byte) 7);
        try (OutputStream out = new FileOutputStream(new File(source, "key"))) { out.write(key); }
        for (int i = 0; i < 2; i++) {
            File file = new File(source, "test" + i + ".ts");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = new FileInputStream(file)) { OfflineHls.copy(in, bytes); }
            byte[] iv = new byte[16]; iv[15] = (byte) i;
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            try (OutputStream out = new FileOutputStream(file)) { out.write(cipher.doFinal(bytes.toByteArray())); }
        }
        File manifest = new File(source, "test.m3u8");
        String text;
        try (InputStream in = new FileInputStream(manifest)) { ByteArrayOutputStream b = new ByteArrayOutputStream(); OfflineHls.copy(in, b); text = b.toString("UTF-8"); }
        try (OutputStream out = new FileOutputStream(manifest)) {
            out.write(text.replace("#EXT-X-MEDIA-SEQUENCE:0", "#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"").getBytes(StandardCharsets.UTF_8));
        }
        File saved = new File(root, "saved"); assertTrue(saved.mkdir());
        Uri exported = HlsExporter.save(context, input(), Uri.fromFile(saved), p -> { });
        LibraryFiles.delete(context, Uri.fromFile(source));
        OfflineHls local = new OfflineHls(new DefaultDataSource.Factory(context, PlaceholderDataSource.FACTORY), exported, Collections.emptyList(), Collections.emptySet());
        assertNull(local.playlists.get(0).segments.get(0).fullSegmentEncryptionKeyUri);
        File output = new File(work, "result.mp4");
        Mp4Converter.convert(local, work, output, p -> { });
        assertTracksAndDuration(Uri.fromFile(output), 3_800_000, 4_300_000);
    }
    @Test public void brokenInputDoesNotProduceMp4OrDeleteSource() throws Exception {
        try (OutputStream out = new FileOutputStream(new File(source, "test1.ts"))) { out.write(new byte[10]); }
        File output = new File(work, "broken.mp4");
        try { Mp4Converter.convert(input(), work, output, p -> { }); fail("Expected corrupt segment failure"); }
        catch (IOException expected) { }
        assertFalse(output.exists()); assertTrue(new File(source, "test0.ts").exists());
    }
    @Test public void safDirectoryRoundTripAndMp4RemainReadable() throws Exception {
        Uri tree = android.provider.DocumentsContract.buildTreeDocumentUri("com.fine.player.test.documents", "root");
        Uri folder = LibraryFiles.create(context, LibraryFiles.treeRoot(tree.toString()),
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR, "test-" + UUID.randomUUID());
        try {
            Uri manifest = HlsExporter.save(context, input(), folder, p -> { });
            assertEquals("content", manifest.getScheme());
            LibraryFiles.delete(context, Uri.fromFile(source));
            OfflineHls stored = new OfflineHls(new DefaultDataSource.Factory(context, PlaceholderDataSource.FACTORY), manifest, Collections.emptyList(), Collections.emptySet());
            File output = new File(work, "output.mp4");
            Mp4Converter.convert(stored, work, output, p -> { });
            Uri mp4 = LibraryFiles.create(context, folder, "video/mp4", "result.mp4");
            HlsExporter.writeChecked(context, mp4, out -> { try (InputStream in = new FileInputStream(output)) { OfflineHls.copy(in, out); } });
            Mp4Converter.validate(context, mp4);
            assertTracksAndDuration(mp4, 3_800_000, 4_300_000);
        } finally { LibraryFiles.delete(context, folder); }
    }
    @Test public void discardedMiddleSegmentIsNotReadAndKeepsTimeline() throws Exception {
        String manifest = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\ntest0.ts\n#EXT-X-GAP\n#EXTINF:2,\nmissing.ts\n#EXTINF:2,\ntest1.ts\n#EXT-X-ENDLIST\n";
        try (OutputStream out = new FileOutputStream(new File(source, "test.m3u8"))) { out.write(manifest.getBytes(StandardCharsets.UTF_8)); }
        File output = new File(work, "gap.mp4");
        Mp4Converter.convert(input(), work, output, p -> { });
        assertTracksAndDuration(Uri.fromFile(output), 5_800_000, 6_300_000);
    }
    @Test public void serviceCommitsMp4BeforeDeletingSourceAndKeepsMetadata() throws Exception {
        runServiceConversion(true, false);
    }
    @Test public void serviceRetainsSourceWhenRequested() throws Exception {
        runServiceConversion(false, false);
    }
    @Test public void serviceFailureNeverDeletesSource() throws Exception {
        runServiceConversion(true, true);
    }
    private void runServiceConversion(boolean delete, boolean corrupt) throws Exception {
        FineApp app = (FineApp) context.getApplicationContext();
        String id = "test-" + UUID.randomUUID();
        DownloadRequest request = new DownloadRequest.Builder(id, Uri.fromFile(new File(source, "test.m3u8")))
                .setMimeType("application/x-mpegURL").build();
        if (corrupt) try (OutputStream out = new FileOutputStream(new File(source, "test1.ts"))) { out.write(0); }
        app.library().output(id, request.uri.toString(), Uri.fromFile(source).toString());
        android.content.Intent intent = new android.content.Intent(context, FileTaskService.class).putExtra("request", request)
                .putExtra("action", "convert").putExtra("deleteSource", delete).putExtra("destination", "");
        try {
            context.startForegroundService(intent);
            long deadline = android.os.SystemClock.elapsedRealtime() + 25000;
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if ((!app.library().get(id, "mp4").isEmpty() || !app.library().get(id, "error").isEmpty()) && FileTaskService.status(id).isEmpty()) break;
                Thread.sleep(50);
            }
            String mp4 = app.library().get(id, "mp4"), error = app.library().get(id, "error");
            if (corrupt) { assertFalse(error.isEmpty()); assertTrue(mp4.isEmpty()); assertTrue(source.exists()); }
            else {
                assertEquals("", error); assertFalse(mp4.isEmpty());
                Mp4Converter.validate(context, Uri.parse(mp4));
                assertEquals(!delete, source.exists());
                LibraryFiles restored = new LibraryFiles(context);
                assertEquals(mp4, restored.get(id, "mp4")); assertEquals(delete, restored.flag(id, "sourceDeleted"));
            }
        } finally {
            String mp4 = app.library().get(id, "mp4");
            if (!mp4.isEmpty()) LibraryFiles.delete(context, Uri.parse(mp4));
            app.library().clear(id);
        }
    }
    @Test public void exportedMasterKeepsAudioAndGapTimelineWithoutRemoteUris() throws Exception {
        File root = new File(context.getCacheDir(), UUID.randomUUID().toString()); assertTrue(root.mkdir());
        File output = new File(root, "out"); assertTrue(output.mkdir());
        try {
            write(root, "master.m3u8", "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"Chinese\",DEFAULT=YES,URI=\"audio.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1000,CODECS=\"avc1.42e01e,mp4a.40.2\",AUDIO=\"a\"\nvideo.m3u8\n");
            String body = "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:5\n#EXTINF:2,\npart.ts\n#EXT-X-DISCONTINUITY\n#EXT-X-GAP\n#EXTINF:2,\nmissing.ts\n#EXT-X-ENDLIST\n";
            write(root, "audio.m3u8", body); write(root, "video.m3u8", body); write(root, "part.ts", "local bytes");
            DataSource.Factory source = new DefaultDataSource.Factory(context, PlaceholderDataSource.FACTORY);
            OfflineHls input = new OfflineHls(source, Uri.fromFile(new File(root, "master.m3u8")), Collections.emptyList(), Collections.emptySet());
            Uri manifest = HlsExporter.save(context, input, Uri.fromFile(output), p -> { });
            OfflineHls exported = new OfflineHls(source, manifest, Collections.emptyList(), Collections.emptySet());
            assertEquals(2, exported.playlists.size());
            HlsMultivariantPlaylist master = (HlsMultivariantPlaylist) exported.root;
            assertEquals("a", master.variants.get(0).audioGroupId);
            assertEquals(1, master.audios.size());
            for (HlsMediaPlaylist p : exported.playlists) {
                assertEquals(5, p.mediaSequence); assertEquals(4_000_000, p.durationUs);
                assertTrue(p.segments.get(1).hasGapTag);
                assertEquals(1, p.segments.get(1).relativeDiscontinuitySequence);
                assertTrue(p.segments.get(0).url.startsWith("file:"));
            }
        } finally { LibraryFiles.delete(context, Uri.fromFile(root)); }
    }
    private void write(File root, String name, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(new File(root, name))) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }
    private void assertTracksAndDuration(Uri uri, long min, long max) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            boolean audio = false, video = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                audio |= mime.startsWith("audio/"); video |= mime.startsWith("video/");
                long duration = f.getLong(MediaFormat.KEY_DURATION);
                assertTrue("duration=" + duration, duration >= min && duration <= max);
            }
            assertTrue(audio); assertTrue(video);
        } finally { extractor.release(); }
    }
}
