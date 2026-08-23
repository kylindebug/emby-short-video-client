package cn.kylins.embyshorts;

import org.junit.Test;

import static org.junit.Assert.*;

public class CachePolicyTest {
    @Test public void stableKeyRemovesAccessTokenAndFragment() {
        assertEquals("https://nas:8920/Videos/item/stream.mp4",
                CachePolicy.stableCacheKey("https://nas:8920/Videos/item/stream.mp4?Static=true&api_key=secret#x"));
        assertEquals("file.mp4", CachePolicy.stableCacheKey("file.mp4#fragment"));
    }

    @Test public void expiryUsesASeventyTwoHourSlidingWindow() {
        long now = 1_000_000_000L;
        assertFalse(CachePolicy.isExpired(now - CachePolicy.MAX_AGE_MS, now));
        assertTrue(CachePolicy.isExpired(now - CachePolicy.MAX_AGE_MS - 1, now));
        assertTrue(CachePolicy.isExpired(0, now));
    }

    @Test public void resourceBudgetsRemainBounded() {
        assertEquals(256L * 1024 * 1024, CachePolicy.MAX_DISK_BYTES);
        assertEquals(48 * 1024 * 1024, CachePolicy.TARGET_MEMORY_BUFFER_BYTES);
        assertTrue(CachePolicy.MAX_BUFFER_MS >= CachePolicy.MIN_BUFFER_MS);
    }
}
