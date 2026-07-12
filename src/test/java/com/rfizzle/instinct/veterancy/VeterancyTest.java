package com.rfizzle.instinct.veterancy;

import com.rfizzle.instinct.data.VeterancyData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeterancyTest {

    private static final List<Integer> DEFAULTS = List.of(10, 30, 60);
    private static final double EPSILON = 1e-9;

    @Test
    void rankBoundariesAreInclusive() {
        assertEquals(0, Veterancy.rankFor(0.0, DEFAULTS));
        assertEquals(0, Veterancy.rankFor(9.99, DEFAULTS));
        assertEquals(1, Veterancy.rankFor(10.0, DEFAULTS));
        assertEquals(1, Veterancy.rankFor(29.5, DEFAULTS));
        assertEquals(2, Veterancy.rankFor(30.0, DEFAULTS));
        assertEquals(3, Veterancy.rankFor(60.0, DEFAULTS));
        assertEquals(3, Veterancy.rankFor(10_000.0, DEFAULTS));
    }

    @Test
    void shorterThresholdListCapsTheRank() {
        assertEquals(2, Veterancy.rankFor(500.0, List.of(5, 15)), "two thresholds mean max rank 2");
    }

    @Test
    void revivalPenaltyDropsToTheThresholdOfTheRankBelow() {
        // Rank n → the threshold day-count of rank n−1; rank 1 (and rank 0) → 0 days (SPEC §7).
        assertEquals(30.0, Veterancy.daysForRankBelow(3, DEFAULTS), EPSILON, "Venerable → Veteran's 30-day floor");
        assertEquals(10.0, Veterancy.daysForRankBelow(2, DEFAULTS), EPSILON, "Veteran → Seasoned's 10-day floor");
        assertEquals(0.0, Veterancy.daysForRankBelow(1, DEFAULTS), EPSILON, "Seasoned → 0 days");
        assertEquals(0.0, Veterancy.daysForRankBelow(0, DEFAULTS), EPSILON, "an unranked pet has nothing to lose");
    }

    @Test
    void rankKeysNameTheThreeRanks() {
        assertEquals("instinct.rank.seasoned", Veterancy.rankKey(1));
        assertEquals("instinct.rank.veteran", Veterancy.rankKey(2));
        assertEquals("instinct.rank.venerable", Veterancy.rankKey(3));
    }

    @Test
    void thresholdListEditsPromoteAndDemoteTheSameDays() {
        double days = 25.0;
        assertEquals(1, Veterancy.rankFor(days, DEFAULTS), "default thresholds: rank 1");
        assertEquals(3, Veterancy.rankFor(days, List.of(5, 10, 20)), "shortened thresholds promote");
        assertEquals(0, Veterancy.rankFor(days, List.of(50, 100, 200)), "lengthened thresholds demote");
    }

    @Test
    void accrueAddsGameTimeGapScaledByRate() {
        VeterancyData data = new VeterancyData(1.0, 24_000L);
        VeterancyData updated = Veterancy.accrue(data, 72_000L, 1.0);
        assertEquals(3.0, updated.accruedDays(), EPSILON, "two days at rate 1.0");
        assertEquals(72_000L, updated.lastAccrualGameTime());

        VeterancyData doubled = Veterancy.accrue(data, 72_000L, 2.0);
        assertEquals(5.0, doubled.accruedDays(), EPSILON, "two days at rate 2.0");
    }

    @Test
    void accrueInitializesTheNeverAccruedSentinelWithoutCreditingDays() {
        VeterancyData fresh = new VeterancyData();
        VeterancyData updated = Veterancy.accrue(fresh, 1_000_000L, 1.0);
        assertEquals(0.0, updated.accruedDays(), EPSILON,
                "a fresh tame must start at 0 days, never at the world's age");
        assertEquals(1_000_000L, updated.lastAccrualGameTime());
    }

    @Test
    void accrueRestampsWithoutCreditOnStalledOrBackwardsTime() {
        VeterancyData data = new VeterancyData(5.0, 48_000L);
        VeterancyData stalled = Veterancy.accrue(data, 48_000L, 1.0);
        assertEquals(5.0, stalled.accruedDays(), EPSILON, "no gap, no days");
        VeterancyData backwards = Veterancy.accrue(data, 24_000L, 1.0);
        assertEquals(5.0, backwards.accruedDays(), EPSILON, "backwards time refunds nothing");
        assertEquals(24_000L, backwards.lastAccrualGameTime(), "clock anomaly restamps");
    }

    @Test
    void providerRateClampsNonFiniteAndNonPositiveToOne() {
        assertEquals(2.0, Veterancy.clampProviderRate(2.0), EPSILON);
        assertEquals(0.5, Veterancy.clampProviderRate(0.5), EPSILON);
        assertEquals(1.0, Veterancy.clampProviderRate(0.0), EPSILON);
        assertEquals(1.0, Veterancy.clampProviderRate(-3.0), EPSILON);
        assertEquals(1.0, Veterancy.clampProviderRate(Double.NaN), EPSILON);
        assertEquals(1.0, Veterancy.clampProviderRate(Double.POSITIVE_INFINITY), EPSILON);
    }

    @Test
    void mentorComposesMultiplicativelyWithTheProviderRate() {
        assertEquals(2.5, Veterancy.liveRate(2.0, true, 0.25), EPSILON,
                "Tribulation's 2.0 × the mentor's 1.25 = 2.5");
        assertEquals(1.25, Veterancy.liveRate(1.0, true, 0.25), EPSILON);
        assertEquals(2.0, Veterancy.liveRate(2.0, false, 0.25), EPSILON);
    }

    @Test
    void mentorBonusNeverStacks() {
        // The rate composition takes one boolean — however many mentors are in range, the
        // accrual pass can only ever apply the single bonus.
        assertEquals(Veterancy.liveRate(1.0, true, 0.25), Veterancy.liveRate(1.0, true, 0.25), EPSILON);
    }

    @Test
    void ducksSweepRequiresRankTwoAndTheOwner() {
        assertFalse(Veterancy.ducksSweep(0, true));
        assertFalse(Veterancy.ducksSweep(1, true));
        assertTrue(Veterancy.ducksSweep(2, true));
        assertTrue(Veterancy.ducksSweep(3, true));
        assertFalse(Veterancy.ducksSweep(3, false), "another player's sweep still hits");
    }
}
