package cn.kylins.embyshorts;

public final class SeekMath {
    private SeekMath() {}

    /** Exponential seek: precision near the origin, rapidly increasing reach for long swipes. */
    public static long deltaMs(float deltaPx, float widthPx, long durationMs) {
        if (widthPx <= 0 || deltaPx == 0) return 0;
        double normalized = Math.min(1.0, Math.abs(deltaPx) / widthPx);
        double maxMs = durationMs > 0 ? Math.min(300_000d, Math.max(10_000d, durationMs * 0.5d)) : 120_000d;
        double fineMs = 400d;
        double exponential = fineMs * (Math.exp(Math.log1p(maxMs / fineMs) * normalized) - 1d);
        double minimumReach = normalized <= (1d / 3d)
                ? 45_000d * normalized * normalized
                : 5_000d + 7_500d * (normalized - (1d / 3d));
        double magnitude = Math.max(exponential, minimumReach);
        return Math.round(Math.copySign(magnitude, deltaPx));
    }

    public static long clampPosition(long positionMs, long durationMs) {
        if (durationMs <= 0) return Math.max(0, positionMs);
        return Math.max(0, Math.min(durationMs, positionMs));
    }

    /** Returns the real movement after applying the start/end boundaries. */
    public static long clampedDeltaMs(long basePositionMs, long requestedDeltaMs, long durationMs) {
        long target = clampPosition(basePositionMs + requestedDeltaMs, durationMs);
        return target - Math.max(0, basePositionMs);
    }
}
