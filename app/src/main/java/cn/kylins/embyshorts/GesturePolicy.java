package cn.kylins.embyshorts;

/** Pure gesture-boundary rules kept separate so they can be unit tested. */
public final class GesturePolicy {
    /** Fast enough to feel immediate, while preserving double-tap and swipe disambiguation. */
    public static final long LONG_PRESS_ACTIVATION_MS = 140;

    private GesturePolicy() {}

    public static float topEdgeSafePx(float heightPx, float density) {
        return Math.max(72f * density, heightPx * 0.08f);
    }

    public static boolean allowVerticalSwitch(float startY, float deltaY, float heightPx, float density) {
        return deltaY <= 0 || startY >= topEdgeSafePx(heightPx, density);
    }

    public static boolean shouldCancelLongPress(float deltaX, float deltaY, float touchSlopPx) {
        return Math.hypot(deltaX, deltaY) >= touchSlopPx;
    }
}
