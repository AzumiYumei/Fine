package com.fine.player;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Scheduler;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import java.util.List;

@androidx.media3.common.util.UnstableApi
public final class VideoDownloadService extends DownloadService {
    public VideoDownloadService() {
        super(1, 1000, "fine_downloads", R.string.download_channel, 0);
    }
    @Override protected DownloadManager getDownloadManager() {
        return ((FineApp) getApplication()).downloads();
    }
    @Override protected Scheduler getScheduler() { return null; }
    @Override protected Notification getForegroundNotification(List<Download> downloads, int requirements) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new DownloadNotificationHelper(this, "fine_downloads")
                .buildProgressNotification(this, R.drawable.ic_download, open, "Fine · 离线视频", downloads, requirements);
    }
}
