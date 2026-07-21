package exposed.thunder.thunderLBs.leaderboard;

import exposed.thunder.thunderLBs.animation.EasingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageSessionTimingTest {
    @Test
    void progressCycleIncludesEntranceBeforeVisiblePageDuration() {
        assertEquals(45L, PageSession.progressCycleTicks(35L, 10));
    }

    @Test
    void interpolationMatchesSpacingBetweenProgressFrames() {
        assertEquals(5, PageSession.progressInterpolationTicks(45L, 10));
    }

    @Test
    void progressInterpolationNeverDropsBelowOneTick() {
        assertEquals(1, PageSession.progressInterpolationTicks(1L, 20));
    }

    @Test
    void barFadeEndsAtPageSwitch() {
        assertEquals(5L, PageSession.barFadeDelayTicks(60L, 45L, 10));
        assertEquals(5L, PageSession.barFadeDelayTicks(100L, 85L, 10));
        assertEquals(5L, PageSession.barFadeDelayTicks(160L, 145L, 10));
    }

    @Test
    void barFadeDelayNeverDropsBelowOneTick() {
        assertEquals(1L, PageSession.barFadeDelayTicks(10L, 10L, 10));
    }

    @Test
    void linearFrameDurationPreservesEndpointTiming() {
        assertEquals(19, PageSession.linearInterpolationTicks(20));
        assertEquals(20L, PageSession.linearCompletionDelayTicks(20));
    }

    @Test
    void linearDurationsNeverDropBelowOneTick() {
        assertEquals(1, PageSession.linearInterpolationTicks(1));
        assertEquals(1L, PageSession.linearCompletionDelayTicks(1));
        assertEquals(1, PageSession.linearCycleInterpolationTicks(1L));
    }

    @Test
    void linearBarFinishesAtTheExistingCycleBoundary() {
        assertEquals(44, PageSession.linearCycleInterpolationTicks(45L));
    }

    @Test
    void onlyEnabledMultiFrameLinearPhasesUseClientInterpolation() {
        assertTrue(PageSession.usesClientInterpolation(true, EasingType.LINEAR, 20));
        assertFalse(PageSession.usesClientInterpolation(false, EasingType.LINEAR, 20));
        assertFalse(PageSession.usesClientInterpolation(true, EasingType.LINEAR, 1));
        assertFalse(PageSession.usesClientInterpolation(true, EasingType.EASE_IN_CUBIC, 20));
    }

    @Test
    void boardScaleAppliesToRowOffsetAndSpacing() {
        assertEquals(-2.25D, PageSession.scaledRowOffset(-0.8D, 0.325D, 1, 2.0D));
    }

    @Test
    void typingBroadcastKeepsConfiguredVolumeWithinNaturalRange() {
        assertEquals(0.5F, PageSession.typingBroadcastVolume(0.5F, 16.0D));
        assertEquals(2.0F, PageSession.typingBroadcastVolume(2.0F, 16.0D));
    }

    @Test
    void typingBroadcastRaisesVolumeOnlyToReachLargerRadius() {
        assertEquals(2.0F, PageSession.typingBroadcastVolume(0.5F, 32.0D));
    }
}
