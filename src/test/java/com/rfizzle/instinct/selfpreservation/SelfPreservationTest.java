package com.rfizzle.instinct.selfpreservation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure owner-unsafe predicate behind teleport refusal (SPEC §1 protection 3): unsafe while
 * falling strictly more than the threshold, in lava, or gliding.
 */
class SelfPreservationTest {

    private static final double THRESHOLD = 3.0;

    @Test
    void safeOwnerDoesNotSuppress() {
        assertFalse(SelfPreservation.ownerUnsafeToJoin(0.0, false, false, THRESHOLD));
    }

    @Test
    void fallDistanceExactlyAtThresholdIsStillSafe() {
        assertFalse(SelfPreservation.ownerUnsafeToJoin(3.0, false, false, THRESHOLD));
    }

    @Test
    void fallDistanceBeyondThresholdSuppresses() {
        assertTrue(SelfPreservation.ownerUnsafeToJoin(3.0001, false, false, THRESHOLD));
    }

    @Test
    void lavaAloneSuppresses() {
        assertTrue(SelfPreservation.ownerUnsafeToJoin(0.0, true, false, THRESHOLD));
    }

    @Test
    void glidingAloneSuppresses() {
        assertTrue(SelfPreservation.ownerUnsafeToJoin(0.0, false, true, THRESHOLD));
    }

    @Test
    void combinedConditionsSuppress() {
        assertTrue(SelfPreservation.ownerUnsafeToJoin(10.0, true, true, THRESHOLD));
    }

    @Test
    void higherThresholdPermitsLongerFalls() {
        assertFalse(SelfPreservation.ownerUnsafeToJoin(9.5, false, false, 10.0));
        assertTrue(SelfPreservation.ownerUnsafeToJoin(10.5, false, false, 10.0));
    }
}
