package com.fine.player;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UriUtil;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.hls.playlist.*;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Reads only the caller's local data source, including AES-128 and byte-range segments. */
@androidx.media3.common.util.UnstableApi
final class OfflineHls {
    final DataSource.Factory source;
    final HlsPlaylist root;
    final List<HlsMediaPlaylist> playlists = new ArrayList<>();
    final List<Uri> urls = new ArrayList<>();
    OfflineHls(DataSource.Factory source, Uri uri, List<StreamKey> keys, Set<String> gaps) throws IOException {
        this.source = source;
        HlsPlaylist parsed = parse(uri, gaps);
        root = keys.isEmpty() ? parsed : parsed.copy(keys);
        if (root instanceof HlsMultivariantPlaylist) {
            for (Uri child : ((HlsMultivariantPlaylist) root).mediaPlaylistUrls) {
                HlsPlaylist p = parse(child, gaps);
                if (!(p instanceof HlsMediaPlaylist)) throw new IOException("无效的本地播放列表");
                urls.add(child); playlists.add((HlsMediaPlaylist) p);
            }
        } else { urls.add(uri); playlists.add((HlsMediaPlaylist) root); }
        for (HlsMediaPlaylist p : playlists) {
            if (!p.hasEndTag) throw new IOException("仅支持已完成的点播视频");
            if (p.protectionSchemes != null) throw new IOException("不支持 DRM 视频转换");
            for (HlsMediaPlaylist.Segment s : p.segments)
                if (s.drmInitData != null) throw new IOException("不支持 DRM 视频转换");
        }
    }
    private HlsPlaylist parse(Uri uri, Set<String> gaps) throws IOException {
        return ParsingLoadable.load(source.createDataSource(), new GapPlaylistParser(new HlsPlaylistParser(), gaps), uri, C.DATA_TYPE_MANIFEST);
    }
    void writeSegment(HlsMediaPlaylist p, HlsMediaPlaylist.Segment s, OutputStream out) throws IOException {
        DataSpec spec = new DataSpec(UriUtil.resolveToUri(p.baseUri, s.url), s.byteRangeOffset, s.byteRangeLength);
        try (InputStream raw = new DataSourceInputStream(source.createDataSource(), spec)) {
            InputStream input = raw;
            if (s.fullSegmentEncryptionKeyUri != null) {
                try {
                    byte[] key = new byte[16];
                    try (DataInputStream keys = new DataInputStream(new DataSourceInputStream(source.createDataSource(),
                            new DataSpec(UriUtil.resolveToUri(p.baseUri, s.fullSegmentEncryptionKeyUri))))) {
                        keys.readFully(key); if (keys.read() != -1) throw new IOException("AES 密钥长度错误");
                    }
                    byte[] value = new BigInteger(s.encryptionIV.replaceFirst("^0[xX]", ""), 16).toByteArray(), iv = new byte[16];
                    System.arraycopy(value, Math.max(0, value.length - 16), iv, Math.max(0, 16 - value.length), Math.min(16, value.length));
                    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
                    input = new CipherInputStream(raw, cipher);
                } catch (java.security.GeneralSecurityException | RuntimeException e) { throw new IOException("分片解密失败", e); }
            }
            copy(input, out);
        }
    }
    File materialize(File directory, HlsMediaPlaylist p, HlsMediaPlaylist.Segment s) throws IOException {
        File file = File.createTempFile("segment-", ".media", directory);
        try (OutputStream out = new FileOutputStream(file)) {
            if (s.initializationSegment != null) writeSegment(p, s.initializationSegment, out);
            writeSegment(p, s, out);
        } catch (IOException e) { file.delete(); throw e; }
        return file;
    }
    static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] bytes = new byte[64 * 1024]; long total = 0; int n;
        while ((n = in.read(bytes)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("操作已取消");
            out.write(bytes, 0, n); total += n;
        }
        return total;
    }
}
