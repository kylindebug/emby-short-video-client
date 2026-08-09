package cn.kylins.embyshorts;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PreloadCache {
    private static final long MAX_ITEM_BYTES = 512L * 1024 * 1024;
    private static final long MAX_CACHE_BYTES = 1024L * 1024 * 1024;
    private final File directory;
    private final EmbyApi api;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentHashMap<String, Future<?>> tasks = new ConcurrentHashMap<>();

    public PreloadCache(Context context, EmbyApi api) {
        directory = new File(context.getCacheDir(), "video_preload");
        if (!directory.exists()) directory.mkdirs();
        this.api = api;
    }

    public Uri playableUri(VideoItem item) {
        File cached = fileFor(item);
        if (cached.isFile() && cached.length() > 0) {
            cached.setLastModified(System.currentTimeMillis());
            return Uri.fromFile(cached);
        }
        return Uri.parse(api.streamUrl(item));
    }

    public void preload(List<VideoItem> upcoming) {
        Set<String> wanted = new HashSet<>();
        for (VideoItem item : upcoming) {
            wanted.add(item.id);
            if (fileFor(item).isFile() || tasks.containsKey(item.id)) continue;
            Future<?> future = executor.submit(() -> download(item));
            tasks.put(item.id, future);
        }
        for (String id : new HashSet<>(tasks.keySet())) {
            if (!wanted.contains(id)) {
                Future<?> task = tasks.remove(id);
                if (task != null) task.cancel(true);
            }
        }
        trimCache();
    }

    private void download(VideoItem item) {
        File target = fileFor(item);
        File temporary = new File(directory, target.getName() + ".part");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(api.streamUrl(item)).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept-Encoding", "identity");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return;
            long declared = connection.getContentLengthLong();
            if (declared > MAX_ITEM_BYTES) return;
            long total = 0;
            byte[] buffer = new byte[128 * 1024];
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) return;
                    total += count;
                    if (total > MAX_ITEM_BYTES) return;
                    output.write(buffer, 0, count);
                }
            }
            if (!temporary.renameTo(target)) return;
            target.setLastModified(System.currentTimeMillis());
        } catch (Exception ignored) {
            // Network playback remains available; preload failures are intentionally non-fatal.
        } finally {
            if (connection != null) connection.disconnect();
            if (temporary.exists()) temporary.delete();
            tasks.remove(item.id);
        }
    }

    private void trimCache() {
        File[] files = directory.listFiles(file -> !file.getName().endsWith(".part"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_CACHE_BYTES) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= MAX_CACHE_BYTES) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private File fileFor(VideoItem item) {
        return new File(directory, item.id.replaceAll("[^A-Za-z0-9_-]", "_") + "." + item.container);
    }

    public void release() {
        for (Future<?> task : tasks.values()) task.cancel(true);
        tasks.clear();
        executor.shutdownNow();
    }
}
