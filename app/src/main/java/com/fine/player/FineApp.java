package com.fine.player;

import android.app.Application;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DownloadManager;
import java.io.File;

@androidx.media3.common.util.UnstableApi
public final class FineApp extends Application {
    private DownloadManager downloads;
    private SimpleCache cache;
    private DefaultHttpDataSource.Factory network;

    public synchronized DownloadManager downloads() {
        if (downloads == null) {
            StandaloneDatabaseProvider database = new StandaloneDatabaseProvider(this);
            cache = new SimpleCache(new File(getFilesDir(), "media"), new NoOpCacheEvictor(), database);
            network = new DefaultHttpDataSource.Factory().setUserAgent("Fine/1.0")
                    .setConnectTimeoutMs(15000).setReadTimeoutMs(30000);
            downloads = new DownloadManager(this, database, cache, network, Runnable::run);
            downloads.setMaxParallelDownloads(1);
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
