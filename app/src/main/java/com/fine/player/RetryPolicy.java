package com.fine.player;

final class RetryPolicy {
    static final int ATTEMPTS = 4;
    static boolean retryable(int code) {
        return code == -1 || code == 408 || code == 429 || code >= 500;
    }
    static long delay(int attempt) { return Math.min(4000, 500L << attempt); }
    static String segmentId(String uri, long offset, long length) {
        return Links.id(uri + "\n" + offset + "\n" + length);
    }
}
