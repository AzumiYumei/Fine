package com.fine.player;

import android.graphics.Bitmap;
import android.util.LruCache;
import java.net.URI;
import java.util.Locale;

/** No files or browsing metadata are persisted by the preview feature. */
final class MediaPreviews {
    static final class Info {
        String title = "";
        long duration = -1;
        boolean live;
        int width, height;
    }
    private static final LruCache<String, Info> details = new LruCache<>(200);
    private static final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    static Info info(String url) {
        Info info = details.get(url);
        if (info == null) { info = new Info(); details.put(url, info); }
        return info;
    }
    static Bitmap image(String url) { return images.get(url); }
    static void image(String url, Bitmap bitmap) { if (bitmap != null) images.put(url, bitmap); }
    static void clear() { details.evictAll(); images.evictAll(); }
    static String name(BrowserResources.Entry entry) {
        String title = info(entry.url).title;
        if (!title.isEmpty()) return title;
        try {
            String path = new URI(entry.url).getPath();
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (!filename.isEmpty()) return filename.length() > 100 ? filename.substring(0, 100) : filename;
        } catch (Exception ignored) { }
        return entry.type + "资源";
    }
    static String host(String url) { try { return new URI(url).getHost(); } catch (Exception e) { return "未知来源"; } }
    static String duration(Info info) {
        if (info.live) return "直播";
        if (info.duration < 0) return "时长待读取";
        long seconds = info.duration / 1000;
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds/3600, seconds/60%60, seconds%60)
                : String.format(Locale.ROOT, "%02d:%02d", seconds/60, seconds%60);
    }
    static boolean canPreview(BrowserResources.Entry entry) {
        return entry.type.equals("HLS") || entry.type.equals("视频") || entry.type.equals("音频");
    }
}
