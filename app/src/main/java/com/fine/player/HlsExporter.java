package com.fine.player;

import android.content.Context;
import android.net.Uri;
import androidx.media3.exoplayer.hls.playlist.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@androidx.media3.common.util.UnstableApi
final class HlsExporter {
    interface Progress { void update(int percent) throws IOException; }
    interface Writer { void write(OutputStream out) throws IOException; }
    static Uri save(Context context, OfflineHls hls, Uri directory, Progress progress) throws IOException {
        Map<String, Uri> manifests = new HashMap<>();
        int total = 0, done = 0;
        for (HlsMediaPlaylist p : hls.playlists) total += p.segments.size();
        for (int index = 0; index < hls.playlists.size(); index++) {
            HlsMediaPlaylist p = hls.playlists.get(index);
            StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-TARGETDURATION:")
                    .append(Math.max(1, (p.targetDurationUs + 999999) / 1000000)).append("\n#EXT-X-MEDIA-SEQUENCE:").append(p.mediaSequence).append('\n');
            text.append("#EXT-X-DISCONTINUITY-SEQUENCE:").append(p.discontinuitySequence).append('\n');
            int discontinuity = 0, segmentIndex = 0;
            Map<String, Uri> initFiles = new HashMap<>();
            HlsMediaPlaylist.Segment previousInit = null;
            for (HlsMediaPlaylist.Segment s : p.segments) {
                while (discontinuity < s.relativeDiscontinuitySequence) { text.append("#EXT-X-DISCONTINUITY\n"); discontinuity++; }
                if (!s.hasGapTag && s.initializationSegment != null && s.initializationSegment != previousInit) {
                    HlsMediaPlaylist.Segment init = s.initializationSegment;
                    String key = init.url + ":" + init.byteRangeOffset + ":" + init.byteRangeLength + ":" + init.encryptionIV;
                    Uri uri = initFiles.get(key);
                    if (uri == null) {
                        uri = LibraryFiles.create(context, directory, "application/octet-stream", "init-" + index + "-" + initFiles.size() + ".mp4");
                        writeChecked(context, uri, out -> hls.writeSegment(p, init, out)); initFiles.put(key, uri);
                    }
                    text.append("#EXT-X-MAP:URI=\"").append(uri).append("\"\n"); previousInit = init;
                }
                String extension = s.initializationSegment != null ? ".m4s" : ".ts";
                Uri uri;
                if (s.hasGapTag) {
                    text.append("#EXT-X-GAP\n");
                    uri = Uri.parse("file:///fine-discarded/" + index + "-" + segmentIndex);
                } else {
                    uri = LibraryFiles.create(context, directory, "application/octet-stream", "part-" + index + "-" + segmentIndex + extension);
                    writeChecked(context, uri, out -> hls.writeSegment(p, s, out));
                }
                text.append("#EXTINF:").append(String.format(Locale.US, "%.6f", s.durationUs / 1000000.0)).append(",\n").append(uri).append('\n');
                segmentIndex++; progress.update(++done * 100 / Math.max(total, 1));
            }
            text.append("#EXT-X-ENDLIST\n");
            Uri manifest = LibraryFiles.create(context, directory, "application/vnd.apple.mpegurl", "track-" + index + ".m3u8");
            writeText(context, manifest, text.toString()); manifests.put(hls.urls.get(index).toString(), manifest);
        }
        if (!(hls.root instanceof HlsMultivariantPlaylist)) return manifests.get(hls.urls.get(0).toString());
        HlsMultivariantPlaylist master = (HlsMultivariantPlaylist) hls.root;
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n");
        renditions(text, master.audios, "AUDIO", manifests);
        renditions(text, master.subtitles, "SUBTITLES", manifests);
        for (HlsMultivariantPlaylist.Variant v : master.variants) {
            text.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(Math.max(1, v.format.bitrate));
            if (v.format.codecs != null) text.append(",CODECS=\"").append(v.format.codecs).append('"');
            if (hasGroup(master.audios, v.audioGroupId)) text.append(",AUDIO=\"").append(quoted(v.audioGroupId)).append('"');
            if (hasGroup(master.subtitles, v.subtitleGroupId)) text.append(",SUBTITLES=\"").append(quoted(v.subtitleGroupId)).append('"');
            text.append('\n').append(manifests.get(v.url.toString())).append('\n');
        }
        Uri result = LibraryFiles.create(context, directory, "application/vnd.apple.mpegurl", "video.m3u8");
        writeText(context, result, text.toString()); return result;
    }
    private static boolean hasGroup(List<HlsMultivariantPlaylist.Rendition> list, String group) {
        for (HlsMultivariantPlaylist.Rendition r : list) if (r.url != null && r.groupId.equals(group)) return true;
        return false;
    }
    private static void renditions(StringBuilder text, List<HlsMultivariantPlaylist.Rendition> list, String type, Map<String, Uri> urls) {
        for (HlsMultivariantPlaylist.Rendition r : list) if (r.url != null) {
            text.append("#EXT-X-MEDIA:TYPE=").append(type).append(",GROUP-ID=\"").append(quoted(r.groupId))
                    .append("\",NAME=\"").append(quoted(r.name)).append("\",AUTOSELECT=YES,URI=\"").append(urls.get(r.url.toString())).append("\"\n");
        }
    }
    private static String quoted(String s) { return s.replace("\"", "").replace("\n", "").replace("\r", ""); }
    private static void writeText(Context c, Uri uri, String text) throws IOException {
        writeChecked(c, uri, out -> out.write(text.getBytes(StandardCharsets.UTF_8)));
    }
    static void writeChecked(Context c, Uri uri, Writer writer) throws IOException {
        try {
            MessageDigest expected = MessageDigest.getInstance("SHA-256"), actual = MessageDigest.getInstance("SHA-256");
            try (OutputStream out = new DigestOutputStream(LibraryFiles.write(c, uri), expected)) { writer.write(out); }
            try (InputStream in = new DigestInputStream(LibraryFiles.read(c, uri), actual)) {
                byte[] bytes = new byte[65536]; while (in.read(bytes) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("操作已取消");
                }
            }
            if (!Arrays.equals(expected.digest(), actual.digest())) throw new IOException("文件写入校验失败，原视频已保留");
        } catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    }
}
