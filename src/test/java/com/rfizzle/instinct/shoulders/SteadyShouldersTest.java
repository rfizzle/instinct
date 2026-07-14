package com.rfizzle.instinct.shoulders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure predicates behind steady shoulders (SPEC §1): the tick removal is suppressed only when
 * the fall branch is the sole reason, and a hit dislodges at or above the damage threshold.
 */
class SteadyShouldersTest {

    private static final double THRESHOLD = 4.0;

    @Test
    void fallOnlyReasonSuppressesFallDismount() {
        assertTrue(SteadyShoulders.suppressesFallDismount(false, false, false, false));
    }

    @Test
    void inWaterDoesNotSuppress() {
        assertFalse(SteadyShoulders.suppressesFallDismount(true, false, false, false));
    }

    @Test
    void flyingDoesNotSuppress() {
        assertFalse(SteadyShoulders.suppressesFallDismount(false, true, false, false));
    }

    @Test
    void sleepingDoesNotSuppress() {
        assertFalse(SteadyShoulders.suppressesFallDismount(false, false, true, false));
    }

    @Test
    void powderSnowDoesNotSuppress() {
        assertFalse(SteadyShoulders.suppressesFallDismount(false, false, false, true));
    }

    @Test
    void minorHitBelowThresholdKeepsSeated() {
        assertFalse(SteadyShoulders.dismountsOnHit(1.0f, THRESHOLD));
    }

    @Test
    void hitExactlyAtThresholdDislodges() {
        assertTrue(SteadyShoulders.dismountsOnHit(4.0f, THRESHOLD));
    }

    @Test
    void seriousHitAboveThresholdDislodges() {
        assertTrue(SteadyShoulders.dismountsOnHit(7.0f, THRESHOLD));
    }
}
