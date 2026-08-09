package cn.kylins.embyshorts;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    public enum Decoder { HARDWARE, SOFTWARE }
    public enum StartMode { AUTO_PLAY, PAUSED }
    public enum EndMode { NEXT, PAUSE, LOOP }

    private static final String PREFS = "emby_shorts_settings";
    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String protocol() { return prefs.getString("protocol", "http"); }
    public String host() { return prefs.getString("host", ""); }
    public int port() { return prefs.getInt("port", protocol().equals("https") ? 443 : 8096); }
    public String username() { return prefs.getString("username", ""); }
    public String password() { return prefs.getString("password", ""); }
    public String accessToken() { return prefs.getString("token", ""); }
    public String userId() { return prefs.getString("user_id", ""); }
    public String folderId() { return prefs.getString("folder_id", ""); }
    public String folderName() { return prefs.getString("folder_name", ""); }
    public Decoder decoder() { return Decoder.valueOf(prefs.getString("decoder", Decoder.HARDWARE.name())); }
    public float upperSpeed() { return prefs.getFloat("upper_speed", 2f); }
    public float lowerSpeed() { return prefs.getFloat("lower_speed", 0.25f); }
    public StartMode startMode() { return StartMode.valueOf(prefs.getString("start_mode", StartMode.AUTO_PLAY.name())); }
    public EndMode endMode() { return EndMode.valueOf(prefs.getString("end_mode", EndMode.LOOP.name())); }

    public String serverBaseUrl() {
        return protocol() + "://" + host().trim() + ":" + port();
    }

    public boolean canPlay() {
        return !host().trim().isEmpty() && !accessToken().isEmpty() && !userId().isEmpty() && !folderId().isEmpty();
    }

    public void saveServer(String protocol, String host, int port, String username, String password) {
        boolean serverChanged = !serverBaseUrl().equals(protocol + "://" + host.trim() + ":" + port)
                || !username().equals(username);
        SharedPreferences.Editor editor = prefs.edit()
                .putString("protocol", protocol)
                .putString("host", host.trim())
                .putInt("port", port)
                .putString("username", username.trim())
                .putString("password", password);
        if (serverChanged) {
            editor.remove("token").remove("user_id").remove("folder_id").remove("folder_name");
        }
        editor.apply();
    }

    public void saveAuth(String token, String userId) {
        prefs.edit().putString("token", token).putString("user_id", userId).apply();
    }

    public void saveFolder(String id, String name) {
        prefs.edit().putString("folder_id", id).putString("folder_name", name).apply();
    }

    public void savePlayback(Decoder decoder, float upper, float lower, StartMode startMode, EndMode endMode) {
        prefs.edit()
                .putString("decoder", decoder.name())
                .putFloat("upper_speed", upper)
                .putFloat("lower_speed", lower)
                .putString("start_mode", startMode.name())
                .putString("end_mode", endMode.name())
                .apply();
    }
}
