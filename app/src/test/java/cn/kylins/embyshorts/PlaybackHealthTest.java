package cn.kylins.embyshorts;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackHealthTest {
    private final PlaybackHealth health = new PlaybackHealth();
    private final PlaybackStatus state = new PlaybackStatus();

    @Test public void readyWithoutPictureStillTimesOutEvenWhenAudioAdvances() {
        state.ready = true;
        state.buffering = false;
        health.reset(0);
        health.waitingMs(state, true, 0, 0);
        assertEquals(8_000, health.waitingMs(state, true, 8_000, 8_000));
        state.videoOutput = true;
        assertEquals(0, health.waitingMs(state, true, 8_100, 8_100));
    }

    @Test public void bufferingHasContinuousDeadlineDespiteBufferUpdates() {
        health.reset(0);
        state.videoOutput = true;
        health.waitingMs(state, true, 500, 0);
        state.bufferedMs = 300;
        assertEquals(5_000, health.waitingMs(state, true, 500, 5_000));
        state.bufferedMs = 600;
        assertEquals(8_000, health.waitingMs(state, true, 500, 8_000));
        state.buffering = false;
        assertEquals(0, health.waitingMs(state, true, 600, 8_100));
    }

    @Test public void userPauseNeverLooksLikeAStallAfterFirstPicture() {
        state.videoOutput = true;
        state.ready = true;
        health.reset(0);
        assertEquals(0, health.waitingMs(state, false, 500, 0));
        assertEquals(0, health.waitingMs(state, false, 500, 60_000));
    }

    @Test public void pausedInitialLoadStillShowsMissingPicture() {
        health.reset(0);
        health.waitingMs(state, false, 0, 0);
        assertEquals(8_000, health.waitingMs(state, false, 0, 8_000));
    }

    @Test public void slowMotionProgressDoesNotTriggerWatchdog() {
        state.videoOutput = state.ready = true;
        state.buffering = false;
        health.reset(0);
        for (long now = 0; now <= 30_000; now += 250) {
            assertEquals(0, health.waitingMs(state, true, now / 8, now));
        }
    }

    @Test public void decoderReportingReadyButFrozenCanBeRecovered() {
        state.videoOutput = state.ready = true;
        state.buffering = false;
        health.reset(0);
        health.waitingMs(state, true, 5_000, 0);
        health.waitingMs(state, true, 5_000, 2_000);
        assertEquals(8_000, health.waitingMs(state, true, 5_000, 10_000));
    }

    @Test public void switchingClearsPreviousTimeoutAndPreviewErrorIsRetained() {
        health.reset(0);
        health.waitingMs(state, true, 0, 0);
        assertEquals(9_000, health.waitingMs(state, true, 0, 9_000));
        health.reset(9_000);
        assertEquals(0, health.waitingMs(state, true, 0, 9_000));
        state.failure = new PlaybackFailure(PlaybackFailure.Kind.DECODE, "ERROR_CODE_DECODING_FAILED");
        assertNotNull(state.failure);
        state.reset();
        assertNull(state.failure);
        assertFalse(state.videoOutput);
    }

    @Test public void endedClipDoesNotShowLoading() {
        state.ended = true;
        health.reset(0);
        assertEquals(0, health.waitingMs(state, true, 0, 50_000));
    }
}
