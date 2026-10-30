package com.fine.player;

import android.net.Uri;
import androidx.media3.common.util.UriUtil;
import androidx.media3.exoplayer.hls.playlist.*;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import java.io.*;
import java.util.*;

@androidx.media3.common.util.UnstableApi
final class GapPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {
    private final ParsingLoadable.Parser<HlsPlaylist> delegate;
    private final Set<String> skipped;
    GapPlaylistParser(ParsingLoadable.Parser<HlsPlaylist> delegate, Set<String> skipped) {
        this.delegate = delegate; this.skipped = skipped;
    }
    @Override public HlsPlaylist parse(Uri uri, InputStream input) throws IOException {
        HlsPlaylist result = delegate.parse(uri, input);
        if (!(result instanceof HlsMediaPlaylist) || skipped.isEmpty()) return result;
        HlsMediaPlaylist p = (HlsMediaPlaylist) result;
        List<HlsMediaPlaylist.Segment> segments = new ArrayList<>();
        for (HlsMediaPlaylist.Segment s : p.segments) {
            String id = RetryPolicy.segmentId(UriUtil.resolve(p.baseUri, s.url), s.byteRangeOffset, s.byteRangeLength);
            // Keep sequence numbers, durations, IVs and discontinuities exactly intact.
            // A GAP tells ExoPlayer to omit the unavailable media without requesting it.
            segments.add(!skipped.contains(id) ? s : new HlsMediaPlaylist.Segment(s.url,
                    s.initializationSegment, s.title, s.durationUs, s.relativeDiscontinuitySequence,
                    s.relativeStartTimeUs, s.drmInitData, s.fullSegmentEncryptionKeyUri, s.encryptionIV,
                    s.byteRangeOffset, s.byteRangeLength, true, Collections.emptyList()));
        }
        return new HlsMediaPlaylist(p.playlistType, p.baseUri, p.tags, p.startOffsetUs, p.preciseStart,
                p.startTimeUs, p.hasDiscontinuitySequence, p.discontinuitySequence, p.mediaSequence,
                p.version, p.targetDurationUs, p.partTargetDurationUs, p.hasIndependentSegments,
                p.hasEndTag, p.hasProgramDateTime, p.protectionSchemes, segments, p.trailingParts,
                p.serverControl, p.renditionReports);
    }
    static HlsPlaylistParserFactory factory(Set<String> skipped) {
        return new HlsPlaylistParserFactory() {
            @Override public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
                return new GapPlaylistParser(new HlsPlaylistParser(), skipped);
            }
            @Override public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(HlsMultivariantPlaylist master, HlsMediaPlaylist previous) {
                return new GapPlaylistParser(new HlsPlaylistParser(master, previous), skipped);
            }
        };
    }
}
