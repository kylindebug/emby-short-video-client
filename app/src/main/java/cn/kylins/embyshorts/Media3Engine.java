package cn.kylins.embyshorts;

import android.content.Context;
import android.net.Uri;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

@OptIn(markerClass = UnstableApi.class)
public final class Media3Engine implements PlaybackEngine {
    private final ExoPlayer player;
    private final PlayerView playerView;
    private Listener listener;

    public Media3Engine(Context context) {
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        player = new ExoPlayer.Builder(context, renderers).build();
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        playerView = new PlayerView(context);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && listener != null) listener.onEnded();
            }

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (listener != null) listener.onPlayingChanged(isPlaying);
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (listener != null) listener.onError(error.getErrorCodeName() + ": " + error.getMessage());
            }
        });
    }

    @Override public View view() { return playerView; }
    @Override public void setListener(Listener listener) { this.listener = listener; }
    @Override public void load(Uri uri, boolean autoPlay) {
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.setPlayWhenReady(autoPlay);
    }
    @Override public void play() { player.play(); }
    @Override public void pause() { player.pause(); }
    @Override public boolean isPlaying() { return player.isPlaying(); }
    @Override public void seekTo(long positionMs) { player.seekTo(positionMs); }
    @Override public long positionMs() { return player.getCurrentPosition(); }
    @Override public long durationMs() { return player.getDuration() == C.TIME_UNSET ? -1 : player.getDuration(); }
    @Override public void setSpeed(float speed) { player.setPlaybackSpeed(speed); }
    @Override public float speed() { return player.getPlaybackParameters().speed; }
    @Override public void release() { playerView.setPlayer(null); player.release(); }
}
