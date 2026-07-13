package com.rfizzle.instinct.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §6 guard stance: the pure return-to-post geometry. A posted pet paths back to its anchor only
 * once it has drifted past the hold radius, so it stays loosely on its ground without twitching back
 * on every step. No Minecraft types — the Tier-1 seam under {@code GuardGoal}.
 */
class GuardTest {

    @Test
    void aDriftedPetReturnsToPost() {
        assertTrue(Guard.shouldReturnToPost(9.0, 2.0), "3 blocks out (9 blocks²) exceeds the 2-block hold radius");
    }

    @Test
    void aPetWithinTheHoldRadiusStaysPut() {
        assertFalse(Guard.shouldReturnToPost(1.0, 2.0), "1 block out is well within the hold radius");
        assertFalse(Guard.shouldReturnToPost(4.0, 2.0), "exactly at the hold radius does not trigger a return");
    }
}
