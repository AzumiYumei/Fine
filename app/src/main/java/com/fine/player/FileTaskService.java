package com.fine.player;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import androidx.media3.exoplayer.offline.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Serial, foreground file jobs. Source deletion is always after durable, verified output. */
@androidx.media3.common.util.UnstableApi
public final class FileTaskService extends Service {
    private static final ConcurrentMap<String, String> running = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int outstanding, latestStart;
    private FineApp app;
    static String status(String id) { String value = running.get(id); return value == null ? "" : value; }
    static void start(Context context, DownloadRequest request, String action, boolean deleteSource) {
        Intent intent = new Intent(context, FileTaskService.class).putExtra("request", request)
                .putExtra("action", action).putExtra("deleteSource", deleteSource)
                .putExtra("destination", ((FineApp) context.getApplicationContext()).library().destination());
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
        } catch (RuntimeException e) {
            try { ((FineApp) context.getApplicationContext()).library().put(request.id, "error", "未能启动文件处理，请在已下载中重试"); }
            catch (IOException ignored) { }
        }
    }
    @Override public void onCreate() {
        super.onCreate(); app = (FineApp) getApplication();
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("file_tasks", "文件保存与转换", NotificationManager.IMPORTANCE_LOW));
        startForeground(2, notification("正在准备", 0));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStart = startId;
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        DownloadRequest request = intent.getParcelableExtra("request");
        String action = intent.getStringExtra("action");
        if (request == null || action == null) { stopSelf(startId); return START_NOT_STICKY; }
        if (running.putIfAbsent(request.id, "等待处理") != null) return START_NOT_STICKY;
        outstanding++;
        worker.execute(() -> {
            try {
                app.library().put(request.id, "error", "");
                cleanupIncomplete(request.id);
                if ("save".equals(action)) save(request);
                else if ("convert".equals(action)) convert(request, intent.getBooleanExtra("deleteSource", false), intent.getStringExtra("destination"));
                else if ("source".equals(action)) { verifyMp4(request.id); deleteSource(request); }
                else if ("delete".equals(action)) deleteAll(request);
            } catch (Exception e) {
                try { app.library().put(request.id, "error", e.getMessage() == null ? "文件处理失败，请检查目录权限和空间" : e.getMessage()); }
                catch (IOException ignored) { }
            } finally {
                Thread.interrupted();
                main.post(() -> {
                    running.remove(request.id);
                    if (--outstanding == 0) { stopForeground(true); stopSelfResult(latestStart); }
                });
            }
        });
        return START_NOT_STICKY;
    }
    private void progress(String id, String label, int percent) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("操作已中断，源视频已保留");
        running.put(id, label + " " + percent + "%");
        getSystemService(NotificationManager.class).notify(2, notification(label, percent));
    }
    private void save(DownloadRequest request) throws IOException {
        LibraryFiles files = app.library();
        if (!files.get(request.id, "hls").isEmpty() || files.flag(request.id, "sourceDeleted")) return;
        String destination = files.get(request.id, "destination");
        if (destination.isEmpty()) return;
        progress(request.id, "保存到目录", 0);
        Uri directory = LibraryFiles.create(this, LibraryFiles.outputParent(this, destination),
                DocumentsContract.Document.MIME_TYPE_DIR, "Fine-" + request.id.substring(0, Math.min(12, request.id.length())) + "-" + System.currentTimeMillis());
        files.put(request.id, "incomplete", directory.toString());
        Uri playlist = HlsExporter.save(this, app.offlineHls(request), directory, p -> progress(request.id, "保存到目录", p));
        // Read the written manifests using the same local-only source used for playback.
        new OfflineHls(app.localSource(), playlist, Collections.emptyList(), Collections.emptySet());
        files.output(request.id, playlist.toString(), directory.toString());
        files.put(request.id, "incomplete", "");
        app.removeCachedSource(request);
    }
    private void convert(DownloadRequest request, boolean removeSource, String destination) throws IOException {
        LibraryFiles files = app.library();
        if (!files.get(request.id, "mp4").isEmpty()) throw new IOException("此视频已经是 MP4");
        progress(request.id, "转换 MP4", 0);
        File work = new File(getCacheDir(), "convert-" + request.id);
        if (work.exists()) LibraryFiles.delete(this, Uri.fromFile(work));
        if (!work.mkdirs()) throw new IOException("无法创建转换临时目录");
        File output = new File(work, "video.mp4");
        try {
            Mp4Converter.convert(app.offlineHls(request), work, output, p -> progress(request.id, "转换 MP4", p));
            Mp4Converter.validate(this, Uri.fromFile(output));
            Uri target = LibraryFiles.create(this, LibraryFiles.outputParent(this, destination == null ? "" : destination), "video/mp4",
                    "Fine-" + request.id.substring(0, Math.min(12, request.id.length())) + "-" + System.currentTimeMillis() + ".mp4");
            files.put(request.id, "incomplete", target.toString());
            progress(request.id, "保存 MP4", 96);
            HlsExporter.writeChecked(this, target, out -> { try (InputStream in = new FileInputStream(output)) { OfflineHls.copy(in, out); } });
            Mp4Converter.validate(this, target);
            files.put(request.id, "mp4", target.toString());
            files.put(request.id, "incomplete", "");
            if (removeSource) deleteSource(request);
            progress(request.id, "转换完成", 100);
        } finally { LibraryFiles.delete(this, Uri.fromFile(work)); }
    }
    private void verifyMp4(String id) throws IOException {
        String uri = app.library().get(id, "mp4");
        if (uri.isEmpty()) throw new IOException("没有可用的 MP4，不能删除源文件");
        Mp4Converter.validate(this, Uri.parse(uri));
    }
    private void deleteSource(DownloadRequest request) throws IOException {
        progress(request.id, "删除源文件", 100);
        LibraryFiles files = app.library();
        String folder = files.get(request.id, "folder");
        if (!folder.isEmpty()) LibraryFiles.delete(this, Uri.parse(folder));
        app.removeCachedSource(request);
        files.put(request.id, "sourceDeleted", true);
        files.output(request.id, "", "");
    }
    private void deleteAll(DownloadRequest request) throws IOException {
        progress(request.id, "删除视频", 0);
        LibraryFiles files = app.library();
        for (String key : new String[]{"mp4", "folder"}) {
            String value = files.get(request.id, key);
            if (!value.isEmpty()) { LibraryFiles.delete(this, Uri.parse(value)); files.put(request.id, key, ""); }
        }
        files.clear(request.id);
        main.post(() -> {
            app.thumbnails().remove(request.id);
            DownloadService.sendRemoveDownload(this, VideoDownloadService.class, request.id, false);
        });
    }
    private void cleanupIncomplete(String id) throws IOException {
        LibraryFiles files = app.library();
        String incomplete = files.get(id, "incomplete");
        if (!incomplete.isEmpty()) {
            // A crash may happen after committing the output but before clearing this marker.
            if (!incomplete.equals(files.get(id, "folder")) && !incomplete.equals(files.get(id, "mp4")))
                LibraryFiles.delete(this, Uri.parse(incomplete));
            files.put(id, "incomplete", "");
        }
    }
    private Notification notification(String text, int progress) {
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class).putExtra("page", 1)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "file_tasks") : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_download).setContentTitle("Fine · " + text).setContentIntent(open)
                .setProgress(100, progress, progress == 0).setOngoing(true).build();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
