package cn.kylins.embyshorts;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Extracts debounced, cached seek-preview frames without blocking the UI thread. */
public final class ThumbnailLoader {
    public interface Callback { void onLoaded(Bitmap bitmap); }

    private static final long REQUEST_DEBOUNCE_MS = 70;
    private static final long BUCKET_MS = 1_000;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private Runnable pendingRequest;
    private int generation;

    public ThumbnailLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public void request(Uri uri, long positionMs, int width, int height, Callback callback) {
        cancelPendingRunnable();
        int requestGeneration = ++generation;
        long bucketMs = Math.max(0, Math.round(positionMs / (double) BUCKET_MS) * BUCKET_MS);
        String key = uri + "#" + bucketMs + "#" + width + "x" + height;
        Bitmap cached;
        synchronized (cache) { cached = cache.get(key); }
        if (cached != null) {
            callback.onLoaded(cached);
            return;
        }
        pendingRequest = () -> executor.execute(() -> {
            if (requestGeneration != generation) return;
            Bitmap bitmap = extract(uri, bucketMs, width, height);
            if (bitmap == null || requestGeneration != generation) return;
            synchronized (cache) { cache.put(key, bitmap); }
            mainHandler.post(() -> {
                if (requestGeneration == generation) callback.onLoaded(bitmap);
            });
        });
        mainHandler.postDelayed(pendingRequest, REQUEST_DEBOUNCE_MS);
    }

    public void cancel() {
        generation++;
        cancelPendingRunnable();
    }

    public void release() {
        cancel();
        executor.shutdownNow();
        synchronized (cache) { cache.evictAll(); }
    }

    private void cancelPendingRunnable() {
        if (pendingRequest != null) {
            mainHandler.removeCallbacks(pendingRequest);
            pendingRequest = null;
        }
    }

    private Bitmap extract(Uri uri, long positionMs, int width, int height) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                retriever.setDataSource(uri.toString(), Collections.emptyMap());
            } else {
                retriever.setDataSource(context, uri);
            }
            long timeUs = positionMs * 1_000L;
            if (Build.VERSION.SDK_INT >= 27) {
                return retriever.getScaledFrameAtTime(timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height);
            }
            Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) return null;
            return Bitmap.createScaledBitmap(frame, width, height, true);
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }
}
