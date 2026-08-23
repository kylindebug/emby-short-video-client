package cn.kylins.embyshorts;

/** Pure cache limits and key rules kept separate for unit testing. */
public final class CachePolicy {
    public static final long MAX_DISK_BYTES = 256L * 1024 * 1024;
    public static final long MAX_AGE_MS = 72L * 60 * 60 * 1_000;
    public static final int TARGET_MEMORY_BUFFER_BYTES = 48 * 1024 * 1024;
    public static final int MIN_BUFFER_MS = 8_000;
    public static final int MAX_BUFFER_MS = 30_000;

    private CachePolicy() {}

    /** Removes access tokens and other transient query data from the on-device cache index. */
    public static String stableCacheKey(String uri) {
        if (uri == null) return "";
        int query = uri.indexOf('?');
        int fragment = uri.indexOf('#');
        int end = uri.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return uri.substring(0, end);
    }

    public static boolean isExpired(long lastTouchTimestamp, long nowTimestamp) {
        return lastTouchTimestamp <= 0 || nowTimestamp - lastTouchTimestamp > MAX_AGE_MS;
    }
}
