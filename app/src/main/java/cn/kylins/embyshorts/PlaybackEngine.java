package cn.kylins.embyshorts;

import android.net.Uri;
import android.view.View;

public interface PlaybackEngine {
    interface Listener {
        void onEnded();
        void onError(PlaybackFailure failure);
        void onPlayingChanged(boolean playing);
    }

    View view();
    void setListener(Listener listener);
    void load(Uri uri, boolean autoPlay);
    void play();
    void pause();
    boolean isPlaying();
    boolean playWhenReady();
    PlaybackStatus status();
    void seekTo(long positionMs);
    long positionMs();
    long durationMs();
    void setSpeed(float speed);
    float speed();
    void release();
}
