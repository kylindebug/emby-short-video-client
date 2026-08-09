package cn.kylins.embyshorts;

import android.content.Context;
import android.net.Uri;
import android.view.View;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.Arrays;

public final class VlcEngine implements PlaybackEngine {
    private final LibVLC libVlc;
    private final MediaPlayer player;
    private final VLCVideoLayout layout;
    private Listener listener;
    private float rate = 1f;

    public VlcEngine(Context context) {
        libVlc = new LibVLC(context, Arrays.asList(
                "--avcodec-hw=none",
                "--network-caching=1500",
                "--file-caching=500",
                "--drop-late-frames",
                "--skip-frames"));
        player = new MediaPlayer(libVlc);
        layout = new VLCVideoLayout(context);
        player.attachViews(layout, null, false, false);
        player.setEventListener(event -> {
            if (listener == null) return;
            if (event.type == MediaPlayer.Event.EndReached) listener.onEnded();
            else if (event.type == MediaPlayer.Event.EncounteredError) listener.onError("LibVLC 解码失败");
            else if (event.type == MediaPlayer.Event.Playing) listener.onPlayingChanged(true);
            else if (event.type == MediaPlayer.Event.Paused || event.type == MediaPlayer.Event.Stopped) listener.onPlayingChanged(false);
        });
    }

    @Override public View view() { return layout; }
    @Override public void setListener(Listener listener) { this.listener = listener; }
    @Override public void load(Uri uri, boolean autoPlay) {
        Media media = new Media(libVlc, uri);
        media.setHWDecoderEnabled(false, false);
        player.setMedia(media);
        media.release();
        if (autoPlay) player.play();
        else {
            player.play();
            layout.postDelayed(player::pause, 120);
        }
    }
    @Override public void play() { player.play(); }
    @Override public void pause() { player.pause(); }
    @Override public boolean isPlaying() { return player.isPlaying(); }
    @Override public void seekTo(long positionMs) { player.setTime(positionMs); }
    @Override public long positionMs() { return player.getTime(); }
    @Override public long durationMs() { return player.getLength(); }
    @Override public void setSpeed(float speed) { rate = speed; player.setRate(speed); }
    @Override public float speed() { return rate; }
    @Override public void release() {
        player.stop();
        player.detachViews();
        player.release();
        libVlc.release();
    }
}
