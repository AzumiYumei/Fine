package com.fine.player;

import android.app.Application;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import java.io.File;

@androidx.media3.common.util.UnstableApi
public final class FineApp extends Application {
    private DownloadManager downloads;
    private SimpleCache cache;
    private DefaultHttpDataSource.Factory network;
    private DownloadProblems problems;
    private Thumbnails thumbnails;
    Thumbnails thumbnails() { if(thumbnails==null) thumbnails=new Thumbnails(this); return thumbnails; }
    public final java.util.concurrent.ExecutorService queries = java.util.concurrent.Executors.newSingleThreadExecutor();
    public DownloadProblems problems() { downloads(); return problems; }
    public int parallelism() { return getSharedPreferences("settings", 0).getInt("threads", 6); }
    public void parallelism(int value) { getSharedPreferences("settings", 0).edit().putInt("threads", value).apply(); }

    public synchronized DownloadManager downloads() {
        if (downloads == null) {
            StandaloneDatabaseProvider database = new StandaloneDatabaseProvider(this);
            cache = new SimpleCache(new File(getFilesDir(), "media"), new NoOpCacheEvictor(), database);
            network = new DefaultHttpDataSource.Factory().setUserAgent("Fine/1.0")
                    .setConnectTimeoutMs(15000).setReadTimeoutMs(30000);
            problems = new DownloadProblems(this);
            downloads = new DownloadManager(this, new DefaultDownloadIndex(database), request ->
                    new ParallelHlsDownloader(request, new CacheDataSource.Factory().setCache(cache)
                            .setUpstreamDataSourceFactory(problems.archiving(request.id) ? null : network), problems, parallelism()));
            downloads.setMaxParallelDownloads(1);
            // Per-resource retries are handled by our downloader. Avoid restarting the whole queue.
            downloads.setMinRetryCount(0);
            downloads.resumeDownloads();
        }
        return downloads;
    }

    public CacheDataSource.Factory source(boolean offline) {
        downloads();
        // Playback never writes cache. Completed downloads must never silently use the network.
        return new CacheDataSource.Factory().setCache(cache).setCacheWriteDataSinkFactory(null)
                .setUpstreamDataSourceFactory(offline ? null : network);
    }
}
