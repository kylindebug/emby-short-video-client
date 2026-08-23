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

    @Test public void verticalCommitUsesTheLargerDistanceThreshold() {
        assertFalse(GesturePolicy.shouldCommitVertical(250, 2400, 72));
        assertTrue(GesturePolicy.shouldCommitVertical(288, 2400, 72));
        assertFalse(GesturePolicy.shouldCommitVertical(143, 600, 72));
        assertTrue(GesturePolicy.shouldCommitVertical(144, 600, 72));
    }

    @Test public void outerFivePercentIsReservedForAndroidBack() {
        assertEquals(GesturePolicy.BACK_EDGE_LEFT, GesturePolicy.backEdge(0, 1000));
        assertEquals(GesturePolicy.BACK_EDGE_LEFT, GesturePolicy.backEdge(50, 1000));
        assertEquals(GesturePolicy.BACK_EDGE_NONE, GesturePolicy.backEdge(50.1f, 1000));
        assertEquals(GesturePolicy.BACK_EDGE_NONE, GesturePolicy.backEdge(949.9f, 1000));
        assertEquals(GesturePolicy.BACK_EDGE_RIGHT, GesturePolicy.backEdge(950, 1000));
        assertEquals(GesturePolicy.BACK_EDGE_RIGHT, GesturePolicy.backEdge(1000, 1000));
    }

    @Test public void backGestureRequiresAnInwardSwipePastTheThreshold() {
        assertFalse(GesturePolicy.shouldCommitBack(GesturePolicy.BACK_EDGE_LEFT, 23, 24));
        assertTrue(GesturePolicy.shouldCommitBack(GesturePolicy.BACK_EDGE_LEFT, 24, 24));
        assertFalse(GesturePolicy.shouldCommitBack(GesturePolicy.BACK_EDGE_LEFT, -100, 24));
        assertFalse(GesturePolicy.shouldCommitBack(GesturePolicy.BACK_EDGE_RIGHT, -23, 24));
        assertTrue(GesturePolicy.shouldCommitBack(GesturePolicy.BACK_EDGE_RIGHT, -24, 24));
        assertFalse(GesturePolicy.shouldCommitBack(GesturePolicy.BACK_EDGE_RIGHT, 100, 24));
    }
}
