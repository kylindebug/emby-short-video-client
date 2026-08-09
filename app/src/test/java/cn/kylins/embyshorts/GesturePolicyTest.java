package cn.kylins.embyshorts;

import org.junit.Test;

import static org.junit.Assert.*;

public class GesturePolicyTest {
    @Test public void longPressActivationIsFastButNotImmediate() {
        assertTrue(GesturePolicy.LONG_PRESS_ACTIVATION_MS >= 100);
        assertTrue(GesturePolicy.LONG_PRESS_ACTIVATION_MS <= 160);
    }

    @Test public void movementAtTouchSlopCancelsLongPressCandidate() {
        assertFalse(GesturePolicy.shouldCancelLongPress(3, 4, 6));
        assertTrue(GesturePolicy.shouldCancelLongPress(3, 4, 5));
    }

    @Test public void downwardSwipeFromTopSafeZoneIsIgnored() {
        float safe = GesturePolicy.topEdgeSafePx(2400, 3f);
        assertEquals(216f, safe, 0.01f);
        assertFalse(GesturePolicy.allowVerticalSwitch(safe - 1, 600, 2400, 3f));
        assertTrue(GesturePolicy.allowVerticalSwitch(safe, 600, 2400, 3f));
    }

    @Test public void UpwardSwipeIsNotBlockedByTopSafeZone() {
        assertTrue(GesturePolicy.allowVerticalSwitch(1, -600, 2400, 3f));
    }
}
