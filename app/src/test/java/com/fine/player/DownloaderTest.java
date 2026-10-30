package com.fine.player;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MimeTypes;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.hls.playlist.*;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import okhttp3.mockwebserver.*;
import okio.Buffer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
public class DownloaderTest {
    MockWebServer server;
    SimpleCache cache;
    CacheDataSource.Factory factory;
    DownloadProblems problems;
    String base;
    AtomicInteger calls = new AtomicInteger(), active = new AtomicInteger(), peak = new AtomicInteger();
    volatile boolean fail = true;
    final byte[] bytes = new byte[2048];
    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        problems = new DownloadProblems(context);
        problems.clear("test");
        cache = new SimpleCache(new File(context.getCacheDir(), UUID.randomUUID().toString()), new NoOpCacheEvictor(), new StandaloneDatabaseProvider(context));
        factory = new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory().setReadTimeoutMs(3000));
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.equals("/index.m3u8")) return new MockResponse().setBody(playlist());
                int n = active.incrementAndGet(); peak.accumulateAndGet(n, Math::max);
                try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                active.decrementAndGet();
                int code = 200;
                if (path.equals("/1.ts")) { calls.incrementAndGet(); if (fail) code = 503; }
                return new MockResponse().setResponseCode(code).setBody(new Buffer().write(bytes));
            }
        });
        server.start();
        base = server.url("/").toString().replaceAll("/$", "");
    }
    String playlist() { return "#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXT-X-MEDIA-SEQUENCE:10\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:5,\n0.ts\n#EXTINF:5,\n1.ts\n#EXTINF:5,\n2.ts\n#EXT-X-ENDLIST\n"; }
    DownloadRequest request() { return new DownloadRequest.Builder("test", Uri.parse(base + "/index.m3u8")).setMimeType(MimeTypes.APPLICATION_M3U8).build(); }
    ParallelHlsDownloader downloader() { return new ParallelHlsDownloader(request(), factory, problems, 3); }
    @After public void teardown() throws Exception {
        server.shutdown(); cache.release();
    }
    @Test public void retriesFailedSegmentRetainsHealthyCacheAndResumes() throws Exception {
        try { downloader().download(null); fail("Expected partial failure"); } catch (IOException expected) { }
        assertEquals(4, calls.get());
        assertTrue("real requests must overlap", peak.get() > 1);
        assertTrue(cache.isCached(base + "/0.ts",0,bytes.length));
        assertTrue(cache.isCached(base + "/2.ts",0,bytes.length));
        assertEquals(1, problems.failures("test").size());
        fail = false;
        downloader().download(null);
        assertEquals(5, calls.get());
        assertTrue(problems.failures("test").isEmpty());
        assertTrue(cache.isCached(base + "/1.ts",0,bytes.length));
    }
    @Test public void discardPersistsAndOfflinePlaylistKeepsIvAndTimeline() throws Exception {
        try { downloader().download(null); fail("Expected partial failure"); } catch (IOException expected) { }
        problems.discard("test", Collections.singleton(problems.failures("test").get(0).id));
        downloader().download(null);
        assertEquals(4, calls.get()); // discarded resource is never fetched again
        server.shutdown();
        CacheDataSource.Factory offline = new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null).setCacheWriteDataSinkFactory(null);
        HlsMediaPlaylist p = (HlsMediaPlaylist) ParsingLoadable.load(offline.createDataSource(),
                new GapPlaylistParser(new HlsPlaylistParser(), new DownloadProblems(RuntimeEnvironment.getApplication()).skipped("test")),
                request().uri, 4);
        assertEquals(3, p.segments.size());
        assertTrue(p.segments.get(1).hasGapTag);
        assertFalse(p.segments.get(2).hasGapTag);
        assertEquals("c", p.segments.get(2).encryptionIV);
        assertEquals(15_000_000, p.durationUs);
        downloader().remove();
        assertTrue(problems.skipped("test").isEmpty());
        assertEquals(0, cache.getCacheSpace());
    }
    @Test public void keyFailuresCannotBeDiscarded() {
        problems.record("test", new DownloadProblems.Failure("key", "密钥", false));
        problems.discard("test", Collections.singleton("key"));
        assertTrue(problems.skipped("test").isEmpty());
        assertEquals(1, problems.failures("test").size());
    }
    @Test public void byteRangeDiscardOnlyMarksSelectedRange() throws Exception {
        String text = "#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\n#EXT-X-BYTERANGE:10@0\nall.ts\n#EXTINF:5,\n#EXT-X-BYTERANGE:10\nall.ts\n#EXT-X-ENDLIST\n";
        Set<String> skipped = Collections.singleton(RetryPolicy.segmentId(base + "/all.ts", 10, 10));
        HlsMediaPlaylist p = (HlsMediaPlaylist) new GapPlaylistParser(new HlsPlaylistParser(), skipped)
                .parse(Uri.parse(base + "/index.m3u8"), new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertFalse(p.segments.get(0).hasGapTag);
        assertTrue(p.segments.get(1).hasGapTag);
        assertEquals(10, p.segments.get(1).byteRangeOffset);
    }
    @Test public void cancellationDoesNotCreateFailedSegments() throws Exception {
        fail = false;
        ParallelHlsDownloader download = downloader();
        ExecutorService runner = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = runner.submit(() -> {
                try { download.download(null); }
                catch (InterruptedException expected) { return; }
                catch (IOException e) { throw new RuntimeException(e); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (active.get() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertTrue(active.get() > 0);
            download.cancel();
            future.get(5, TimeUnit.SECONDS);
            assertTrue(problems.failures("test").isEmpty());
        } finally { runner.shutdownNow(); }
    }
}
