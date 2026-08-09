package cn.kylins.embyshorts;

import org.junit.Test;
import static org.junit.Assert.*;

public class SeekMathTest {
    @Test public void seekIsSymmetricAndExponential() {
        long small = SeekMath.deltaMs(100, 1000, 600_000);
        long medium = SeekMath.deltaMs(500, 1000, 600_000);
        long large = SeekMath.deltaMs(1000, 1000, 600_000);
        assertTrue(small > 0);
        assertTrue(medium > small * 4);
        assertTrue(large > medium * 4);
        assertEquals(-medium, SeekMath.deltaMs(-500, 1000, 600_000));
    }

    @Test public void seekIsCappedAndPositionClamped() {
        assertEquals(300_000, SeekMath.deltaMs(1000, 1000, 3_600_000));
        assertEquals(0, SeekMath.clampPosition(-1, 10_000));
        assertEquals(10_000, SeekMath.clampPosition(20_000, 10_000));
    }

    @Test public void displayedDeltaStopsAtVideoBoundaries() {
        assertEquals(0, SeekMath.clampedDeltaMs(0, -30_000, 120_000));
        assertEquals(0, SeekMath.clampedDeltaMs(120_000, 30_000, 120_000));
        assertEquals(-2_000, SeekMath.clampedDeltaMs(2_000, -30_000, 120_000));
        assertEquals(2_000, SeekMath.clampedDeltaMs(118_000, 30_000, 120_000));
    }
}
