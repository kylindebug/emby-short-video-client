package cn.kylins.embyshorts;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;
import java.util.HashSet;

/**
 * Wear-aware read-through cache. Only bytes requested by playback are cached; there is no
 * speculative full-video downloader competing with the active NAS stream.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlaybackCache {
    private static final long CACHE_FRAGMENT_BYTES = 8L * 1024 * 1024;
    private static final int WRITE_BUFFER_BYTES = 256 * 1024;

    private final File legacyPreloadDirectory;
    private final SimpleCache cache;
    private final DataSource.Factory dataSourceFactory;

    public PlaybackCache(Context context) {
        Context appContext = context.getApplicationContext();
        legacyPreloadDirectory = new File(appContext.getCacheDir(), "video_preload");

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("EmbyShorts/" + BuildConfig.VERSION_NAME)
                .setConnectTimeoutMs(6_000)
                .setReadTimeoutMs(10_000)
                .setAllowCrossProtocolRedirects(true);
        DataSource.Factory upstream = new DefaultDataSource.Factory(appContext, httpFactory);

        SimpleCache initialized = null;
        try {
            File directory = new File(appContext.getCacheDir(), "media_playback");
            initialized = new SimpleCache(directory,
                    new LeastRecentlyUsedCacheEvictor(CachePolicy.MAX_DISK_BYTES),
                    new StandaloneDatabaseProvider(appContext));
            initialized.checkInitialization();
        } catch (Exception ignored) {
            if (initialized != null) initialized.release();
            initialized = null;
        }
        cache = initialized;

        if (cache == null) {
            dataSourceFactory = upstream;
        } else {
            CacheDataSink.Factory sink = new CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(CACHE_FRAGMENT_BYTES)
                    .setBufferSize(WRITE_BUFFER_BYTES);
            dataSourceFactory = new CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(upstream)
                    .setCacheWriteDataSinkFactory(sink)
                    .setCacheKeyFactory(dataSpec -> CachePolicy.stableCacheKey(dataSpec.uri.toString()))
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        }
    }

    public DataSource.Factory dataSourceFactory() {
        return dataSourceFactory;
    }

    /** Run before playback starts so cleanup never competes with active media reads. */
    public void runMaintenance() {
        deleteTree(legacyPreloadDirectory);
        if (cache == null) return;
        long now = System.currentTimeMillis();
        for (String key : new HashSet<>(cache.getKeys())) {
            for (CacheSpan span : new HashSet<>(cache.getCachedSpans(key))) {
                if (CachePolicy.isExpired(span.lastTouchTimestamp, now)) {
                    cache.removeSpan(span);
                }
            }
        }
    }

    public void release() {
        if (cache != null) cache.release();
    }

    private static void deleteTree(File target) {
        if (target == null || !target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        target.delete();
    }
}
