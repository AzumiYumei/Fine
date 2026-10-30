package com.fine.player;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Lossless remux using platform extractors/muxer, no bundled native codec library. */
@androidx.media3.common.util.UnstableApi
final class Mp4Converter {
    private static final class Track {
        final MediaFormat format; final int output;
        long samples;
        Track(MediaFormat format, int output) { this.format = format; this.output = output; }
    }
    static void convert(OfflineHls hls, File work, File output, HlsExporter.Progress progress) throws IOException {
        MediaMuxer muxer = null;
        boolean finished = false;
        try {
            muxer = new MediaMuxer(output.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            List<Map<String, Track>> mappings = new ArrayList<>();
            int total = 0, done = 0, videoTracks = 0;
            for (HlsMediaPlaylist p : hls.playlists) {
                Map<String, Track> tracks = new LinkedHashMap<>(); mappings.add(tracks);
                HlsMediaPlaylist.Segment first = null;
                for (HlsMediaPlaylist.Segment s : p.segments) if (!s.hasGapTag) { if (first == null) first = s; total++; }
                if (first == null) continue;
                File file = hls.materialize(work, p, first);
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(file.getPath());
                    for (int i = 0; i < extractor.getTrackCount(); i++) {
                        MediaFormat format = extractor.getTrackFormat(i);
                        String mime = format.getString(MediaFormat.KEY_MIME);
                        if (mime == null || (!mime.startsWith("video/") && !mime.startsWith("audio/"))) continue;
                        if (!supported(mime)) throw new IOException("此视频编码不支持直接转 MP4：" + mime);
                        if (tracks.containsKey(mime)) throw new IOException("此视频包含重复音视频轨道，暂不支持直接转换");
                        if (mime.startsWith("video/") && ++videoTracks > 1) throw new IOException("请只保留一个视频清晰度后转换");
                        if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION))
                            muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION));
                        tracks.put(mime, new Track(format, muxer.addTrack(format)));
                    }
                } finally { extractor.release(); file.delete(); }
            }
            if (videoTracks == 0) throw new IOException("未找到可转换的视频轨道");
            muxer.start();
            ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int pi = 0; pi < hls.playlists.size(); pi++) {
                HlsMediaPlaylist p = hls.playlists.get(pi);
                Map<String, Track> tracks = mappings.get(pi);
                if (tracks.isEmpty()) continue; // External subtitles aren't MP4 audio/video tracks.
                for (HlsMediaPlaylist.Segment s : p.segments) {
                    if (s.hasGapTag) continue;
                    File file = hls.materialize(work, p, s);
                    MediaExtractor extractor = new MediaExtractor();
                    try {
                        extractor.setDataSource(file.getPath());
                        Map<Integer, Track> selected = new HashMap<>();
                        // Keep all tracks selected. Some native TS extractors exhaust a track
                        // when it is deselected and cannot seek back to its first sample.
                        for (int i = 0; i < extractor.getTrackCount(); i++) {
                            MediaFormat format = extractor.getTrackFormat(i);
                            Track track = tracks.get(format.getString(MediaFormat.KEY_MIME));
                            if (track == null) continue;
                            ensureCompatible(track.format, format);
                            extractor.selectTrack(i);
                            selected.put(i, track);
                        }
                        long firstTime = extractor.getSampleTime();
                        if (selected.size() != tracks.size() || firstTime < 0) throw new IOException("分片轨道缺失，原视频已保留");
                        while (extractor.getSampleTrackIndex() >= 0) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("转换已取消");
                            int flags = extractor.getSampleFlags();
                            if ((flags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) throw new IOException("不支持加密轨道转换");
                            buffer.clear(); int size = extractor.readSampleData(buffer, 0);
                            if (size < 0) break;
                            if (size > buffer.capacity()) throw new IOException("视频单帧过大，暂不支持转换");
                            Track track = selected.get(extractor.getSampleTrackIndex());
                            long time = s.relativeStartTimeUs + Math.max(0, extractor.getSampleTime() - firstTime);
                            info.set(0, size, time, (flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                            muxer.writeSampleData(track.output, buffer, info); track.samples++;
                            extractor.advance();
                        }
                    } finally { extractor.release(); file.delete(); }
                    progress.update(++done * 95 / Math.max(1, total));
                }
            }
            for (Map<String, Track> tracks : mappings) for (Track track : tracks.values())
                if (track.samples == 0) throw new IOException("音视频轨道为空，原视频已保留");
            muxer.stop(); finished = true;
        } catch (RuntimeException e) { throw new IOException("设备无法封装此视频，原视频已保留", e); }
        finally { if (muxer != null) try { muxer.release(); } catch (RuntimeException ignored) { } if (!finished) output.delete(); }
    }
    private static boolean supported(String mime) {
        return mime.equals("video/avc") || mime.equals("video/hevc") || mime.equals("video/mp4v-es") || mime.equals("audio/mp4a-latm");
    }
    private static void ensureCompatible(MediaFormat a, MediaFormat b) throws IOException {
        for (String key : new String[]{MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT, MediaFormat.KEY_CHANNEL_COUNT, MediaFormat.KEY_SAMPLE_RATE})
            if (a.containsKey(key) != b.containsKey(key) || (a.containsKey(key) && a.getInteger(key) != b.getInteger(key)))
                throw new IOException("视频中途切换格式，暂不支持直接转 MP4");
        for (String key : new String[]{"csd-0", "csd-1", "csd-2"})
            if (!Objects.equals(a.getByteBuffer(key), b.getByteBuffer(key))) throw new IOException("视频编码参数发生变化，原视频已保留");
    }
    static void validate(Context context, Uri uri) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        boolean video = false;
        try {
            extractor.setDataSource(context, uri, null);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    extractor.selectTrack(i);
                    video = extractor.getSampleTime() >= 0 && format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > 0;
                }
            }
            if (!video) throw new IOException("MP4 校验失败，原视频已保留");
        } catch (RuntimeException e) { throw new IOException("MP4 无法读取，原视频已保留", e); }
        finally { extractor.release(); }
    }
}
