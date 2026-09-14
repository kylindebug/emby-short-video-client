package cn.kylins.embyshorts;

/** Monotonic-time watchdog, independent of the decoder's unreliable isPlaying flag. */
public final class PlaybackHealth {
    public static final long SHOW_DELAY_MS = 350;
    public static final long ACTION_DELAY_MS = 8_000;
    private long waitingSince = -1;
    private long lastAdvanceAt;
    private long lastPosition = -1;

    public void reset(long now) {
        waitingSince = -1;
        lastAdvanceAt = now;
        lastPosition = -1;
    }

    public long waitingMs(PlaybackStatus state, boolean wantsPlay, long position, long now) {
        if (position != lastPosition || !wantsPlay) lastAdvanceAt = now;
        lastPosition = position;
        boolean waiting = !state.ended && (state.failure != null || !state.videoOutput
                || (wantsPlay && (state.buffering || now - lastAdvanceAt >= 2_000)));
        if (!waiting) {
            waitingSince = -1;
            return 0;
        }
        if (waitingSince < 0) waitingSince = now;
        return now - waitingSince;
    }
}
