package cn.kylins.embyshorts;

/** A short, interruptible ramp in log speed, so slowing and restoring feel symmetric. */
public final class SpeedTransition {
    public static final long DURATION_MS = 100;
    private float from = 1f;
    private float target = 1f;
    private long startedAt;

    public void start(float current, float desired, long now) {
        from = current;
        target = desired;
        startedAt = now;
    }

    public float value(long now) {
        float fraction = Math.max(0f, Math.min(1f, (now - startedAt) / (float) DURATION_MS));
        if (fraction >= 1f) return target;
        return (float) (from * Math.pow(target / from, fraction));
    }

    public boolean finished(long now) { return now - startedAt >= DURATION_MS; }
}
