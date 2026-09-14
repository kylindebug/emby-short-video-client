package cn.kylins.embyshorts;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpeedTransitionTest {
    @Test public void slowAndFastLimitsReachTargetWithoutOvershootOrZeroSpeed() {
        for (float target : new float[]{0.125f, 0.25f, 0.5f, 1f, 2f, 8f}) {
            SpeedTransition ramp = new SpeedTransition();
            ramp.start(1f, target, 0);
            for (long now = 0; now <= 150; now += 5) {
                float value = ramp.value(now);
                assertTrue(value >= Math.min(1f, target));
                assertTrue(value <= Math.max(1f, target));
            }
            assertEquals(target, ramp.value(100), 0f);
            assertTrue(ramp.finished(100));
        }
    }

    @Test public void releaseDuringEntryRampRestoresFromCurrentSpeedWithoutJump() {
        SpeedTransition ramp = new SpeedTransition();
        ramp.start(1f, 0.125f, 0);
        float interrupted = ramp.value(25);
        ramp.start(interrupted, 1f, 25);
        assertEquals(interrupted, ramp.value(25), 0f);
        assertTrue(ramp.value(50) > interrupted);
        assertEquals(1f, ramp.value(125), 0f);
    }

    @Test public void newHoldCancelsRestoreInsteadOfApplyingOldTargetLater() {
        SpeedTransition ramp = new SpeedTransition();
        ramp.start(0.125f, 1f, 0);
        float interrupted = ramp.value(50);
        ramp.start(interrupted, 2f, 50);
        assertEquals(interrupted, ramp.value(50), 0f);
        assertEquals(2f, ramp.value(150), 0f);
        assertEquals(2f, ramp.value(5_000), 0f);
    }
}
