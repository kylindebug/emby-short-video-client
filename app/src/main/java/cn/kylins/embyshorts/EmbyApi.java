package cn.kylins.embyshorts;

import android.net.Uri;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EmbyApi {
    public static final class Folder {
        public final String id;
        public final String name;
        Folder(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    private final Context context;
    private final AppSettings settings;

    public EmbyApi(Context context, AppSettings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    public void authenticate() throws Exception {
        JSONObject body = new JSONObject()
                .put("Username", settings.username())
                .put("Pw", settings.password());
        JSONObject response = requestJson("POST", "/Users/AuthenticateByName", body, false);
        String token = response.getString("AccessToken");
        String userId = response.getJSONObject("User").getString("Id");
        settings.saveAuth(token, userId);
    }

    /** Returns one navigation level. Root views retain the order configured in Emby. */
    public List<Folder> getFolders(String parentId) throws Exception {
        ensureAuthenticated();
        String query;
        if (parentId == null || parentId.isEmpty()) {
            query = "/Users/" + enc(settings.userId()) + "/Views";
        } else {
            query = "/Users/" + enc(settings.userId()) + "/Items?ParentId=" + enc(parentId) +
                    "&IncludeItemTypes=Folder&Recursive=false&SortBy=SortName&Fields=Path";
        }
        JSONArray items = requestJson("GET", query, null, true).getJSONArray("Items");
        List<Folder> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            result.add(new Folder(item.getString("Id"), item.optString("Name", "未命名文件夹")));
        }
        return result;
    }

    public List<VideoItem> getVideos() throws Exception {
        ensureAuthenticated();
        String query = "/Users/" + enc(settings.userId()) + "/Items?ParentId=" + enc(settings.folderId()) +
                "&IncludeItemTypes=Video&Recursive=true&SortBy=Random&Limit=10000&Fields=MediaSources,Path";
        JSONArray items = requestJson("GET", query, null, true).getJSONArray("Items");
        List<VideoItem> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            result.add(new VideoItem(item.getString("Id"), item.optString("Name", "未命名视频"), item.optString("Container", "mp4")));
        }
        return result;
    }

    public String streamUrl(VideoItem item) {
        return streamUrl(settings.serverBaseUrl(), item.id, item.container, settings.accessToken());
    }

    static String streamUrl(String base, String id, String container, String token) {
        return base + "/Videos/" + enc(id) + "/stream." + enc(container) +
                "?Static=true&api_key=" + enc(token);
    }

    private void ensureAuthenticated() throws Exception {
        if (settings.accessToken().isEmpty() || settings.userId().isEmpty()) authenticate();
    }

    private JSONObject requestJson(String method, String path, JSONObject body, boolean withToken) throws Exception {
        URL url = new URL(settings.serverBaseUrl() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Emby-Authorization", authorizationHeader());
        if (withToken && !settings.accessToken().isEmpty()) {
            connection.setRequestProperty("X-Emby-Token", settings.accessToken());
        }
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readAll(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("Emby HTTP " + code + ": " + text);
        return new JSONObject(text);
    }

    private String authorizationHeader() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("emby_shorts_device", Context.MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", "");
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", deviceId).apply();
        }
        return "MediaBrowser Client=\"Emby Short Video Client\", Device=\"Android\", DeviceId=\"" +
                deviceId + "\", Version=\"" + BuildConfig.VERSION_NAME + "\"";
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static String enc(String value) { return Uri.encode(value == null ? "" : value); }
}
