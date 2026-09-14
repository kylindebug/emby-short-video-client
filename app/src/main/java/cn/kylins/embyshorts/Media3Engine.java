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
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.ui.PlayerView;

@OptIn(markerClass = UnstableApi.class)
public final class Media3Engine implements PlaybackEngine {
    private final ExoPlayer player;
    private final PlayerView playerView;
    private Listener listener;
    private final PlaybackStatus status = new PlaybackStatus();

    public Media3Engine(Context context, PlaybackCache playbackCache) {
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .setEnableAudioOutputPlaybackParameters(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(CachePolicy.MIN_BUFFER_MS, CachePolicy.MAX_BUFFER_MS,
                        500, 1_000)
                .setTargetBufferBytes(CachePolicy.TARGET_MEMORY_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setBackBuffer(3_000, false)
                .build();
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(
                playbackCache.dataSourceFactory())
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6));
        player = new ExoPlayer.Builder(context, renderers)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        playerView = new PlayerView(context);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                status.buffering = state == Player.STATE_BUFFERING;
                status.ready = state == Player.STATE_READY;
                status.ended = state == Player.STATE_ENDED;
                if (state == Player.STATE_ENDED && listener != null) listener.onEnded();
            }

            @Override public void onRenderedFirstFrame() { status.videoOutput = true; }

            @Override public void onSurfaceSizeChanged(int width, int height) {
                if (width == 0 || height == 0) status.videoOutput = false;
            }

            @Override public void onPlayWhenReadyChanged(boolean ready, int reason) {
                if (listener != null) listener.onPlayingChanged(player.isPlaying());
            }

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (listener != null) listener.onPlayingChanged(isPlaying);
            }

            @Override public void onPlayerError(PlaybackException error) {
                PlaybackFailure.Kind kind = error.errorCode >= 2000 && error.errorCode < 3000
                        ? PlaybackFailure.Kind.NETWORK
                        : error.errorCode >= 3000 && error.errorCode < 4000
                        ? PlaybackFailure.Kind.SOURCE
                        : error.errorCode >= 4000 && error.errorCode < 6000
                        ? PlaybackFailure.Kind.DECODE : PlaybackFailure.Kind.UNKNOWN;
                status.failure = new PlaybackFailure(kind, error.getErrorCodeName());
                if (listener != null) listener.onError(status.failure);
            }
        });
    }

    @Override public View view() { return playerView; }
    @Override public void setListener(Listener listener) { this.listener = listener; }
    @Override public void load(Uri uri, boolean autoPlay) {
        status.reset();
        player.setPlayWhenReady(autoPlay);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
    }
    @Override public void play() { player.play(); }
    @Override public void pause() { player.pause(); }
    @Override public boolean isPlaying() { return player.isPlaying(); }
    @Override public boolean playWhenReady() { return player.getPlayWhenReady(); }
    @Override public PlaybackStatus status() {
        status.bufferedMs = player.getTotalBufferedDuration();
        return status;
    }
    @Override public void seekTo(long positionMs) { player.seekTo(positionMs); }
    @Override public long positionMs() { return player.getCurrentPosition(); }
    @Override public long durationMs() { return player.getDuration() == C.TIME_UNSET ? -1 : player.getDuration(); }
    @Override public void setSpeed(float speed) {
        if (Math.abs(speed - speed()) > 0.0001f) player.setPlaybackSpeed(speed);
    }
    @Override public float speed() { return player.getPlaybackParameters().speed; }
    @Override public void release() { listener = null; playerView.setPlayer(null); player.release(); }
}
