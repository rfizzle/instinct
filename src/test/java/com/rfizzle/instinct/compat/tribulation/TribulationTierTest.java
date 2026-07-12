package com.rfizzle.instinct.compat.tribulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Tribulation tier mapping is pure, so it unit-tests without the sibling jar: inclusive
 * thresholds, the doubling boundary at tier 3, and the empty-threshold degenerate case.
 */
class TribulationTierTest {

    private static final int[] THRESHOLDS = {10, 20, 30, 40, 50};

    @Test
    void belowFirstThresholdIsTierZero() {
        assertEquals(0, TribulationTier.tierFor(5, THRESHOLDS));
    }

    @Test
    void thresholdIsInclusive() {
        assertEquals(1, TribulationTier.tierFor(10, THRESHOLDS), "exactly the tier-1 threshold is tier 1");
        assertEquals(3, TribulationTier.tierFor(30, THRESHOLDS), "exactly the tier-3 threshold is tier 3");
    }

    @Test
    void countsEveryThresholdMet() {
        assertEquals(2, TribulationTier.tierFor(25, THRESHOLDS));
        assertEquals(5, TribulationTier.tierFor(1000, THRESHOLDS));
    }

    @Test
    void emptyThresholdsIsAlwaysTierZero() {
        assertEquals(0, TribulationTier.tierFor(1000, new int[0]));
    }

    @Test
    void rateDoublesAtTierThreeAndAbove() {
        assertEquals(TribulationTier.DOUBLED_RATE, TribulationTier.rateFor(30, THRESHOLDS), "tier 3 doubles");
        assertEquals(TribulationTier.DOUBLED_RATE, TribulationTier.rateFor(55, THRESHOLDS), "tier 5 doubles");
    }

    @Test
    void rateIsBaseBelowTierThree() {
        assertEquals(TribulationTier.BASE_RATE, TribulationTier.rateFor(29, THRESHOLDS), "tier 2 stays base");
        assertEquals(TribulationTier.BASE_RATE, TribulationTier.rateFor(0, THRESHOLDS), "tier 0 stays base");
    }
}
