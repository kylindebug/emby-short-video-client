package cn.kylins.embyshorts;

/** Pure gesture-boundary rules kept separate so they can be unit tested. */
public final class GesturePolicy {
    /** Fast enough to feel immediate, while preserving double-tap and swipe disambiguation. */
    public static final long LONG_PRESS_ACTIVATION_MS = 140;
    public static final float BACK_EDGE_FRACTION = 0.05f;
    public static final int BACK_EDGE_NONE = 0;
    public static final int BACK_EDGE_LEFT = -1;
    public static final int BACK_EDGE_RIGHT = 1;

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

    public static boolean shouldCommitVertical(float deltaY, float heightPx, float thresholdPx) {
        return Math.abs(deltaY) >= Math.max(thresholdPx * 2f, heightPx * 0.12f);
    }

    /** Returns the reserved Android-back edge for the initial touch coordinate. */
    public static int backEdge(float startX, float widthPx) {
        if (widthPx <= 0 || startX < 0 || startX > widthPx) return BACK_EDGE_NONE;
        if (startX <= widthPx * BACK_EDGE_FRACTION) return BACK_EDGE_LEFT;
        if (startX >= widthPx * (1f - BACK_EDGE_FRACTION)) return BACK_EDGE_RIGHT;
        return BACK_EDGE_NONE;
    }

    /** Android-style inward swipe: right from the left edge or left from the right edge. */
    public static boolean shouldCommitBack(int edge, float deltaX, float thresholdPx) {
        if (thresholdPx <= 0) return false;
        if (edge == BACK_EDGE_LEFT) return deltaX >= thresholdPx;
        if (edge == BACK_EDGE_RIGHT) return deltaX <= -thresholdPx;
        return false;
    }
}
