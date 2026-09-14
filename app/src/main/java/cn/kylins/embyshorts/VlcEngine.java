package cn.kylins.embyshorts;

import android.content.Context;
import android.net.Uri;
import android.view.View;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VlcEngine implements PlaybackEngine {
    private final LibVLC libVlc;
    private final MediaPlayer player;
    private final VLCVideoLayout layout;
    private Listener listener;
    private float rate = 1f;
    private final PlaybackStatus status = new PlaybackStatus();
    private static final ExecutorService RELEASE_QUEUE = Executors.newSingleThreadExecutor();
    private boolean wantsPlay;
    private boolean released;
    private boolean pauseAfterPrepare;
    private long pendingSeek = -1;

    public VlcEngine(Context context) {
        libVlc = new LibVLC(context, Arrays.asList(
                "--avcodec-hw=none",
                "--network-caching=6000",
                "--file-caching=1000",
                "--drop-late-frames",
                "--skip-frames"));
        player = new MediaPlayer(libVlc);
        layout = new VLCVideoLayout(context);
        player.attachViews(layout, null, false, true);
        player.setEventListener(event -> {
            if (released) return;
            if (event.type == MediaPlayer.Event.EndReached) {
                status.ended = true;
                if (listener != null) listener.onEnded();
            } else if (event.type == MediaPlayer.Event.EncounteredError) {
                status.failure = new PlaybackFailure(PlaybackFailure.Kind.UNKNOWN, "VLC_PLAYBACK_ERROR");
                if (listener != null) listener.onError(status.failure);
            } else if (event.type == MediaPlayer.Event.Buffering) {
                status.bufferingPercent = Math.max(0, Math.min(100, Math.round(event.getBuffering())));
                status.buffering = status.bufferingPercent < 100;
            } else if (event.type == MediaPlayer.Event.Vout) {
                // Vout means a video output exists, not a decoded-frame timestamp.
                status.videoOutput = event.getVoutCount() > 0;
                pausePreviewWhenReady();
            } else if (event.type == MediaPlayer.Event.Playing) {
                status.ready = true;
                status.buffering = false;
                player.setRate(rate);
                applyPendingSeek();
                pausePreviewWhenReady();
                if (listener != null) listener.onPlayingChanged(wantsPlay);
            } else if (event.type == MediaPlayer.Event.SeekableChanged) {
                applyPendingSeek();
            } else if (event.type == MediaPlayer.Event.Paused || event.type == MediaPlayer.Event.Stopped) {
                if (listener != null) listener.onPlayingChanged(false);
            }
        });
    }

    @Override public View view() { return layout; }
    @Override public void setListener(Listener listener) { this.listener = listener; }
    @Override public void load(Uri uri, boolean autoPlay) {
        status.reset();
        pendingSeek = -1;
        wantsPlay = autoPlay;
        Media media = new Media(libVlc, uri);
        media.setHWDecoderEnabled(false, false);
        media.addOption(":http-reconnect");
        media.addOption(":network-caching=6000");
        player.setMedia(media);
        media.release();
        pauseAfterPrepare = !autoPlay;
        player.setVolume(autoPlay ? 100 : 0);
        player.play();
    }
    @Override public void play() {
        wantsPlay = true;
        pauseAfterPrepare = false;
        player.setVolume(100);
        player.play();
        if (listener != null) listener.onPlayingChanged(true);
    }
    @Override public void pause() {
        wantsPlay = false;
        pauseAfterPrepare = !status.ready || !status.videoOutput;
        player.setVolume(0);
        if (!pauseAfterPrepare) player.pause();
        if (listener != null) listener.onPlayingChanged(false);
    }
    @Override public boolean isPlaying() { return player.isPlaying(); }
    @Override public boolean playWhenReady() { return wantsPlay; }
    @Override public PlaybackStatus status() { return status; }
    @Override public void seekTo(long positionMs) {
        pendingSeek = Math.max(0, positionMs);
        applyPendingSeek();
    }
    private void applyPendingSeek() {
        if (pendingSeek >= 0 && status.ready && player.isSeekable()) {
            player.setTime(pendingSeek);
            pendingSeek = -1;
        }
    }
    private void pausePreviewWhenReady() {
        if (pauseAfterPrepare && status.ready && status.videoOutput) {
            pauseAfterPrepare = false;
            player.pause();
        }
    }
    @Override public long positionMs() { return player.getTime(); }
    @Override public long durationMs() { return player.getLength(); }
    @Override public void setSpeed(float speed) {
        if (Math.abs(rate - speed) <= 0.0001f) return;
        rate = speed;
        if (status.ready) player.setRate(speed);
    }
    @Override public float speed() { return rate; }
    @Override public void release() {
        if (released) return;
        released = true;
        listener = null;
        player.setEventListener(null);
        pauseAfterPrepare = false;
        player.setVolume(0);
        player.detachViews();
        // Native stop may wait for network/decoder threads. Keep gestures and retry responsive.
        RELEASE_QUEUE.execute(() -> {
            try { player.stop(); }
            finally {
                try { player.release(); }
                finally { libVlc.release(); }
            }
        });
    }
}
