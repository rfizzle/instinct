package com.rfizzle.instinct.kennel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §9 kennel post: the pure geometry and pacing. A downed pet recovers within a post's radius, a
 * recalled pet settles once it reaches its post, and recovery accrues to a per-second threshold. No
 * Minecraft types — the Tier-1 seam under {@code HomeGoal} and the downed recovery sweep.
 */
class KennelTest {

    @Test
    void aPetInsideTheRecoveryRadiusRecovers() {
        assertTrue(Kennel.withinRecoveryRadius(0.0, 4), "on the post recovers");
        assertTrue(Kennel.withinRecoveryRadius(16.0, 4), "exactly 4 blocks out (16 blocks²) is within radius 4");
    }

    @Test
    void aPetBeyondTheRecoveryRadiusDoesNot() {
        assertFalse(Kennel.withinRecoveryRadius(17.0, 4), "just past 4 blocks is out of radius 4");
        assertFalse(Kennel.withinRecoveryRadius(100.0, 4), "far out is out");
    }

    @Test
    void aRecalledPetSettlesOnceItReachesThePost() {
        assertTrue(Kennel.arrivedHome(0.0, Kennel.ARRIVE_RADIUS), "at the post has arrived");
        assertTrue(Kennel.arrivedHome(4.0, Kennel.ARRIVE_RADIUS), "exactly at the 2-block arrive radius counts as home");
        assertFalse(Kennel.arrivedHome(9.0, Kennel.ARRIVE_RADIUS), "3 blocks out has not arrived");
    }

    @Test
    void recoveryCompletesOnlyAtTheThreshold() {
        int threshold = Kennel.recoveryThresholdTicks(5); // 100 ticks
        assertEquals(100, threshold);
        assertFalse(Kennel.recoveryComplete(80, threshold), "still short of the threshold");
        assertTrue(Kennel.recoveryComplete(100, threshold), "reaching the threshold completes");
        assertTrue(Kennel.recoveryComplete(120, threshold), "past the threshold completes");
    }
}
