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

/** Extracts cached seek-preview frames without blocking or starving the UI thread. */
public final class ThumbnailLoader {
    public interface Callback { void onLoaded(Bitmap bitmap); }

    private static final long BUCKET_MS = 1_000;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object requestLock = new Object();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private Request pendingRequest;
    private boolean workerRunning;
    private boolean released;
    private int generation;
    private long nextSerial;
    private long deliveredSerial;

    private static final class Request {
        final Uri uri;
        final long bucketMs;
        final int width;
        final int height;
        final String key;
        final Callback callback;
        final int generation;
        final long serial;

        Request(Uri uri, long bucketMs, int width, int height, String key,
                Callback callback, int generation, long serial) {
            this.uri = uri;
            this.bucketMs = bucketMs;
            this.width = width;
            this.height = height;
            this.key = key;
            this.callback = callback;
            this.generation = generation;
            this.serial = serial;
        }
    }

    public ThumbnailLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public void request(Uri uri, long positionMs, int width, int height, Callback callback) {
        long bucketMs = Math.max(0, Math.round(positionMs / (double) BUCKET_MS) * BUCKET_MS);
        String key = uri + "#" + bucketMs + "#" + width + "x" + height;
        Bitmap cached;
        synchronized (cache) { cached = cache.get(key); }
        Request request;
        synchronized (requestLock) {
            if (released) return;
            request = new Request(uri, bucketMs, width, height, key, callback,
                    generation, ++nextSerial);
            pendingRequest = cached == null ? request : null;
            if (cached == null && !workerRunning) {
                workerRunning = true;
                executor.execute(this::drainRequests);
            }
        }
        if (cached != null) {
            deliver(request, cached);
        }
    }

    public void cancel() {
        synchronized (requestLock) {
            generation++;
            pendingRequest = null;
            deliveredSerial = 0;
        }
    }

    public void release() {
        synchronized (requestLock) {
            released = true;
            generation++;
            pendingRequest = null;
        }
        executor.shutdownNow();
        synchronized (cache) { cache.evictAll(); }
    }

    /**
     * Processes one frame immediately, then always jumps to the newest requested second.
     * An in-flight extraction is never invalidated by finger movement, so the preview can
     * keep painting useful frames instead of remaining black until the finger is released.
     */
    private void drainRequests() {
        MediaMetadataRetriever retriever = null;
        String retrieverSource = null;
        try {
            while (true) {
                Request request;
                synchronized (requestLock) {
                    request = pendingRequest;
                    pendingRequest = null;
                    if (request == null || released) {
                        workerRunning = false;
                        return;
                    }
                }

                String source = request.uri.toString();
                if (!source.equals(retrieverSource)) {
                    releaseRetriever(retriever);
                    retriever = openRetriever(request.uri);
                    retrieverSource = retriever == null ? null : source;
                }
                if (retriever == null) continue;

                Bitmap bitmap;
                try {
                    bitmap = extract(retriever, request.bucketMs, request.width, request.height);
                } catch (RuntimeException ignored) {
                    releaseRetriever(retriever);
                    retriever = null;
                    retrieverSource = null;
                    continue;
                }
                if (bitmap == null) continue;
                synchronized (cache) { cache.put(request.key, bitmap); }
                deliver(request, bitmap);
            }
        } finally {
            releaseRetriever(retriever);
        }
    }

    private MediaMetadataRetriever openRetriever(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                retriever.setDataSource(uri.toString(), Collections.emptyMap());
            } else {
                retriever.setDataSource(context, uri);
            }
            return retriever;
        } catch (RuntimeException ignored) {
            releaseRetriever(retriever);
            return null;
        }
    }

    private static Bitmap extract(MediaMetadataRetriever retriever, long positionMs, int width, int height) {
        long timeUs = positionMs * 1_000L;
        if (Build.VERSION.SDK_INT >= 27) {
            return retriever.getScaledFrameAtTime(timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST, width, height);
        }
        Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
        if (frame == null) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(frame, width, height, true);
        if (scaled != frame) frame.recycle();
        return scaled;
    }

    private void deliver(Request request, Bitmap bitmap) {
        mainHandler.post(() -> {
            synchronized (requestLock) {
                if (released || request.generation != generation || request.serial < deliveredSerial) return;
                deliveredSerial = request.serial;
            }
            request.callback.onLoaded(bitmap);
        });
    }

    private static void releaseRetriever(MediaMetadataRetriever retriever) {
        if (retriever == null) return;
        try { retriever.release(); } catch (Exception ignored) { }
    }
}
