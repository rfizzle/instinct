package com.rfizzle.instinct.herding;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of §4's pure drive-assist core: the behind-the-straggler press point, the
 * straggler threshold, the flock speed scaling, and the claim map's expiry and per-owner accounting.
 * All primitive in / primitive out — no Minecraft bootstrap.
 */
class HerdingTest {

    @AfterEach
    void clearClaims() {
        Herding.clearClaims();
    }

    @Test
    void pressPointIsTwoBlocksBehindOnTheAxis() {
        double[] point = Herding.pressPoint(10.0, 0.0, 0.0, 0.0, 2.0);
        assertEquals(12.0, point[0], 1.0e-9, "behind point is on the far side of the straggler");
        assertEquals(0.0, point[1], 1.0e-9);
    }

    @Test
    void pressPointFollowsADiagonalAxis() {
        // straggler at (3,4) from the player at the origin: axis length 5, so 5 blocks behind lands at (6,8).
        double[] point = Herding.pressPoint(3.0, 4.0, 0.0, 0.0, 5.0);
        assertEquals(6.0, point[0], 1.0e-9);
        assertEquals(8.0, point[1], 1.0e-9);
    }

    @Test
    void pressPointDegeneratesToTheStragglerWhenCoincident() {
        double[] point = Herding.pressPoint(5.0, 5.0, 5.0, 5.0, 2.0);
        assertEquals(5.0, point[0], 1.0e-9);
        assertEquals(5.0, point[1], 1.0e-9);
    }

    @Test
    void stragglersAreStrictlyBeyondTheThreshold() {
        List<double[]> flock = List.of(
                new double[]{7.9, 0.0},   // inside — not a straggler
                new double[]{8.0, 0.0},   // exactly at the threshold — not a straggler
                new double[]{8.1, 0.0},   // beyond — a straggler
                new double[]{0.0, 12.0}); // beyond on the other axis — a straggler
        List<double[]> stragglers = Herding.stragglersOf(flock, p -> p[0], p -> p[1], 0.0, 0.0, 8.0);
        assertEquals(2, stragglers.size());
        assertTrue(stragglers.contains(flock.get(2)));
        assertTrue(stragglers.contains(flock.get(3)));
    }

    @Test
    void flockSpeedScalesByMultiplierAndPress() {
        assertEquals(1.25 * 1.15, Herding.flockSpeed(1.25, 1.15, false), 1.0e-9);
        assertEquals(1.25 * 1.15 * FlockingTemptGoal.PRESS_HUSTLE, Herding.flockSpeed(1.25, 1.15, true), 1.0e-9);
    }

    @Test
    void claimHoldsUntilExpiryThenReleases() {
        UUID straggler = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        assertTrue(Herding.tryClaim(straggler, pet, owner, 1000L), "an unclaimed straggler is claimable");
        assertFalse(Herding.tryClaim(straggler, UUID.randomUUID(), owner, 1000L),
                "a second pet cannot claim the same straggler");
        assertTrue(Herding.isClaimed(straggler, 1000L + Herding.CLAIM_EXPIRY_TICKS - 1));
        assertFalse(Herding.isClaimed(straggler, 1000L + Herding.CLAIM_EXPIRY_TICKS),
                "the claim expires exactly at its expiry tick");
    }

    @Test
    void pressFlagRidesTheClaimAndClearsOnRelease() {
        UUID straggler = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        Herding.tryClaim(straggler, pet, owner, 0L);
        assertFalse(Herding.isPressed(straggler, 0L), "a fresh claim is not yet pressing");
        Herding.refreshClaim(straggler, pet, owner, 10L, true);
        assertTrue(Herding.isPressed(straggler, 10L), "the pet in position marks the straggler pressed");
        Herding.release(straggler);
        assertFalse(Herding.isClaimed(straggler, 10L), "release drops the claim");
        assertFalse(Herding.isPressed(straggler, 10L));
    }

    @Test
    void ownerCountIsScopedPerDriverAndPrunesExpired() {
        UUID driverA = UUID.randomUUID();
        UUID driverB = UUID.randomUUID();
        Herding.tryClaim(UUID.randomUUID(), UUID.randomUUID(), driverA, 0L);
        Herding.tryClaim(UUID.randomUUID(), UUID.randomUUID(), driverA, 0L);
        Herding.tryClaim(UUID.randomUUID(), UUID.randomUUID(), driverB, 0L);

        assertEquals(2, Herding.activeClaimsForOwner(driverA, 10L), "each driver only counts its own workers");
        assertEquals(1, Herding.activeClaimsForOwner(driverB, 10L));
        assertEquals(0, Herding.activeClaimsForOwner(driverA, Herding.CLAIM_EXPIRY_TICKS),
                "expired claims are pruned and stop counting");
    }
}
