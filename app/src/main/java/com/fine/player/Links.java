package com.fine.player;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class Links {
    private Links() {}
    static String normalize(String value) {
        String link = value.trim();
        try {
            URI uri = new URI(link);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
            return link;
        } catch (Exception e) {
            throw new IllegalArgumentException("请输入完整的 http:// 或 https:// 视频链接");
        }
    }
    static String id(String link) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(link.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
