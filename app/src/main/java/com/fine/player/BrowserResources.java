package com.fine.player;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Bounded, memory-only inventory; signed query strings are preserved verbatim. */
final class BrowserResources {
    static final int LIMIT = 500;
    static final class Entry {
        final String url, type;
        Entry(String url, String type) { this.url = url; this.type = type; }
        boolean media() { return !type.equals("其他") && !type.equals("图片"); }
        boolean standalone() { return media() && !type.equals("媒体分片"); }
    }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    synchronized void add(String value, String mime) {
        final String url;
        try { url = Links.normalize(value); } catch (Exception e) { return; }
        String type = classify(url, mime);
        Entry old = entries.get(url);
        if (old != null && (!old.type.equals("其他") || type.equals("其他"))) return;
        if (old == null && entries.size() >= LIMIT) {
            if (!new Entry(url, type).media()) return;
            String victim = null;
            for (Entry e : entries.values()) if (!e.media()) { victim = e.url; break; }
            if (victim == null) return;
            entries.remove(victim);
        }
        entries.put(url, new Entry(url, type));
    }
    synchronized List<Entry> snapshot(boolean mediaOnly) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : entries.values()) if (!mediaOnly || e.standalone()) result.add(e);
        return result;
    }
    synchronized void clear() { entries.clear(); }
    static String classify(String url, String mime) {
        String path;
        try { path = new URI(url).getPath().toLowerCase(Locale.ROOT); }
        catch (Exception e) { return "其他"; }
        String type = mime == null ? "" : mime.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (path.endsWith(".m3u8") || type.equals("application/vnd.apple.mpegurl")
                || type.equals("application/x-mpegurl") || type.equals("audio/mpegurl")) return "HLS";
        if (path.endsWith(".mpd") || type.equals("application/dash+xml")) return "DASH";
        if (path.matches(".*\\.(ts|m4s)$")) return "媒体分片";
        if (path.matches(".*\\.(mp4|webm|mov|mkv|flv)$") || type.startsWith("video/")) return "视频";
        if (path.matches(".*\\.(mp3|m4a|aac|wav|ogg|flac)$") || type.startsWith("audio/")) return "音频";
        if (path.matches(".*\\.(png|jpe?g|gif|webp|svg|avif|ico)$")) return "图片";
        return "其他";
    }
    static String address(String input) {
        String value = input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("请输入网址或搜索词");
        if (value.matches("(?i)^https?://.*")) return Links.normalize(value);
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") && !value.matches("^[^/: ]+:\\d+.*"))
            throw new IllegalArgumentException("仅支持 HTTP / HTTPS 网页");
        if (!value.contains(" ") && (value.contains(".") || value.startsWith("localhost")))
            return Links.normalize("https://" + value);
        try { return "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
    static String sharedUrl(String text) {
        if (text == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("https?://[^\\s<>\\\"“”]+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        while (matcher.find()) {
            try { return Links.normalize(matcher.group()); } catch (IllegalArgumentException ignored) { }
        }
        return "";
    }
}
