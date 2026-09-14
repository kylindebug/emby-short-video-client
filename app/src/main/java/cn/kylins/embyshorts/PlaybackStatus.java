package cn.kylins.embyshorts;

/** Snapshot also retained while a swipe preview has no Activity listener. */
public final class PlaybackStatus {
    public boolean buffering = true;
    public boolean ready;
    public boolean videoOutput;
    public boolean ended;
    public int bufferingPercent = -1;
    public long bufferedMs;
    public PlaybackFailure failure;

    public void reset() {
        buffering = true;
        ready = false;
        videoOutput = false;
        ended = false;
        bufferingPercent = -1;
        bufferedMs = 0;
        failure = null;
    }
}
