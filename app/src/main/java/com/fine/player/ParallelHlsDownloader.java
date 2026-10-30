package com.fine.player;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.util.UriUtil;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.exoplayer.hls.offline.HlsDownloader;
import androidx.media3.exoplayer.hls.playlist.*;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded parallel cache writers. One bad segment does not cancel the healthy ones. */
@androidx.media3.common.util.UnstableApi
final class ParallelHlsDownloader implements Downloader {
    private final DownloadRequest request;
    private final CacheDataSource.Factory factory;
    private final DownloadProblems problems;
    private final int threads;
    private volatile boolean cancelled;
    private final Set<CacheWriter> writers = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Long> cached = new HashMap<>();
    private int done, total;
    private static final class Part {
        final DataSpec spec;
        final String id, label;
        final boolean skippable;
        Part(DataSpec spec, String label, boolean skippable) {
            this.spec = spec; this.label = label; this.skippable = skippable;
            id = RetryPolicy.segmentId(spec.uri.toString(), spec.position, spec.length);
        }
    }
    ParallelHlsDownloader(DownloadRequest request, CacheDataSource.Factory factory, DownloadProblems problems, int threads) {
        this.request = request; this.factory = problems.archiving(request.id) ? new CacheDataSource.Factory().setCache(factory.getCache()).setUpstreamDataSourceFactory(null) : factory; this.problems = problems; this.threads = threads;
    }
    @Override public void download(ProgressListener listener) throws IOException, InterruptedException {
        checkCancelled();
        if (problems.sealed(request.id)) return;
        cached.clear(); done = 0;
        LinkedHashMap<String, Part> parts = new LinkedHashMap<>();
        HlsPlaylist root = manifest(request.uri);
        if (!request.streamKeys.isEmpty()) root = root.copy(request.streamKeys);
        List<HlsMediaPlaylist> playlists = new ArrayList<>();
        if (root instanceof HlsMultivariantPlaylist) {
            for (Uri uri : ((HlsMultivariantPlaylist) root).mediaPlaylistUrls) {
                HlsPlaylist child = manifest(uri);
                if (!(child instanceof HlsMediaPlaylist)) throw new IOException("无效的子播放列表");
                playlists.add((HlsMediaPlaylist) child);
            }
        } else playlists.add((HlsMediaPlaylist) root);
        Set<String> skipped = problems.skipped(request.id);
        for (HlsMediaPlaylist p : playlists) {
            if (!p.hasEndTag) throw new IOException("直播不能下载为完整点播视频");
            int index = 0;
            for (HlsMediaPlaylist.Segment s : p.segments) {
                Part media = part(p, s, "分片 " + (++index), true);
                if (s.hasGapTag || skipped.contains(media.id)) continue;
                if (s.initializationSegment != null) {
                    Part init = part(p, s.initializationSegment, "初始化片段", false);
                    parts.put(init.id, init);
                    addKey(p, s.initializationSegment, parts);
                }
                addKey(p, s, parts);
                parts.put(media.id, media);
            }
        }
        if (parts.isEmpty()) throw new IOException("没有可下载的片段，不能丢弃全部视频");
        total = parts.size();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CompletionService<Boolean> completions = new ExecutorCompletionService<>(pool);
        int failures = 0;
        try {
            for (Part part : parts.values()) completions.submit(() -> fetch(part, listener));
            for (int i = 0; i < total; i++) {
                checkCancelled();
                try { if (!completions.take().get()) failures++; }
                catch (ExecutionException e) {
                    if (e.getCause() instanceof InterruptedException) throw new InterruptedException();
                    throw new IOException("下载工作线程失败", e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
            synchronized (writers) { for (CacheWriter writer : writers) writer.cancel(); }
            boolean interrupted = Thread.interrupted();
            while (!pool.isTerminated()) {
                try { pool.awaitTermination(1, TimeUnit.SECONDS); }
                catch (InterruptedException e) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
        checkCancelled();
        if (failures > 0) throw new IOException(failures + " 个资源失败，请重试或处理失败分片");
        problems.seal(request.id);
    }
    private HlsPlaylist manifest(Uri uri) throws IOException, InterruptedException {
        String id = RetryPolicy.segmentId(uri.toString(), 0, -1);
        for (int attempt = 0; ; attempt++) {
            checkCancelled();
            try {
                HlsPlaylist p = ParsingLoadable.load(factory.createDataSourceForDownloading(), new HlsPlaylistParser(), uri, C.DATA_TYPE_MANIFEST);
                problems.resolved(request.id, id);
                return p;
            } catch (IOException e) {
                checkCancelled();
                if (attempt + 1 < RetryPolicy.ATTEMPTS && RetryPolicy.retryable(httpCode(e))) {
                    Thread.sleep(RetryPolicy.delay(attempt)); continue;
                }
                problems.record(request.id, new DownloadProblems.Failure(id, "播放列表：" + errorText(e), false));
                throw e;
            }
        }
    }
    private Part part(HlsMediaPlaylist p, HlsMediaPlaylist.Segment s, String label, boolean skip) {
        return new Part(new DataSpec(UriUtil.resolveToUri(p.baseUri, s.url), s.byteRangeOffset, s.byteRangeLength), label, skip);
    }
    private void addKey(HlsMediaPlaylist p, HlsMediaPlaylist.Segment s, Map<String, Part> parts) {
        if (s.fullSegmentEncryptionKeyUri == null) return;
        Part key = new Part(new DataSpec(UriUtil.resolveToUri(p.baseUri, s.fullSegmentEncryptionKeyUri)), "解密密钥", false);
        parts.put(key.id, key);
    }
    private boolean fetch(Part part, ProgressListener listener) throws InterruptedException {
        for (int attempt = 0; attempt < RetryPolicy.ATTEMPTS; attempt++) {
            checkCancelled();
            CacheWriter writer = new CacheWriter(factory.createDataSourceForDownloading(), part.spec,
                    new byte[64 * 1024], (length, bytes, delta) -> progress(part.id, bytes, false, listener));
            writers.add(writer);
            try {
                checkCancelled();
                writer.cache();
                problems.resolved(request.id, part.id);
                progress(part.id, -1, true, listener);
                return true;
            } catch (IOException e) {
                checkCancelled();
                if (attempt + 1 < RetryPolicy.ATTEMPTS && RetryPolicy.retryable(httpCode(e))) {
                    Thread.sleep(RetryPolicy.delay(attempt)); continue;
                }
                problems.record(request.id, new DownloadProblems.Failure(part.id, part.label + "：" + errorText(e), part.skippable && networkFailure(e)));
                return false;
            } finally { writers.remove(writer); }
        }
        return false;
    }
    private synchronized void progress(String id, long bytes, boolean complete, ProgressListener listener) {
        if (bytes >= 0) cached.put(id, bytes);
        if (complete) done++;
        long sum = 0;
        for (long n : cached.values()) sum += n;
        if (listener != null) listener.onProgress(C.LENGTH_UNSET, sum, total == 0 ? 0 : done * 100f / total);
    }
    static int httpCode(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause())
            if (e instanceof HttpDataSource.InvalidResponseCodeException) return ((HttpDataSource.InvalidResponseCodeException) e).responseCode;
        return -1;
    }
    private static boolean networkFailure(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause())
            if (e instanceof HttpDataSource.HttpDataSourceException) return true;
        return false;
    }
    static String errorText(IOException e) {
        int code = httpCode(e);
        if (code == 400 || code == 401 || code == 403) return "HTTP " + code + "，链接可能过期或需要鉴权";
        if (code > 0) return "HTTP " + code;
        return "连接中断、超时或存储异常（" + e.getClass().getSimpleName() + "）";
    }
    private void checkCancelled() throws InterruptedException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
    @Override public void cancel() {
        cancelled = true;
        synchronized (writers) { for (CacheWriter writer : writers) writer.cancel(); }
    }
    @Override public void remove() {
        // The stock remover enumerates all segments, including discarded and partially cached ones.
        new HlsDownloader(request.toMediaItem(), factory).remove();
        problems.clear(request.id);
    }
}
