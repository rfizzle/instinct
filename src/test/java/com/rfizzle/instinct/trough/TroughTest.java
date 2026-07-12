package com.rfizzle.instinct.trough;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trough's pure decision logic ({@code design/SPEC.md} §5): the accepted-item predicate, the
 * hay→wheat conversion guards, the comparator fill math, the population-cap gate, and the transient
 * claim / baby-cooldown registers — all exercised without a running game.
 */
class TroughTest {

    @BeforeEach
    void reset() {
        Trough.clearForTest();
    }

    @Test
    void acceptsOnlyFoodOfTheStoredTypeWithRoom() {
        assertTrue(Trough.canInsert(true, true, false, 64), "food into an empty trough");
        assertTrue(Trough.canInsert(true, false, true, 40), "more of the same food");
        assertFalse(Trough.canInsert(false, true, false, 64), "a non-food is refused");
        assertFalse(Trough.canInsert(true, false, false, 40), "a mismatched food type is refused");
        assertFalse(Trough.canInsert(true, false, true, 0), "no room is refused");
    }

    @Test
    void hayConvertsToNineWheatOnlyWhenWheatOrEmptyWithRoom() {
        assertEquals(9, Trough.hayWheatYield(true, 64), "empty trough takes the full 9 wheat");
        assertEquals(9, Trough.hayWheatYield(true, 9), "exactly 9 room is enough");
        assertEquals(0, Trough.hayWheatYield(true, 8), "fewer than 9 room refuses the bale");
        assertEquals(0, Trough.hayWheatYield(false, 64), "a trough holding another food refuses hay");
    }

    @Test
    void comparatorLevelIsProportionalToFill() {
        assertEquals(0, Trough.comparatorLevel(0, 64), "empty reads 0");
        assertEquals(1, Trough.comparatorLevel(1, 64), "one item lights the first level");
        assertEquals(15, Trough.comparatorLevel(64, 64), "a full stack reads 15");
        assertEquals(8, Trough.comparatorLevel(32, 64), "half full reads mid-range");
    }

    @Test
    void populationCapGatesBreedingButZeroIsUncapped() {
        assertTrue(Trough.capAllows(16, 16), "at the cap still admits");
        assertFalse(Trough.capAllows(17, 16), "over the cap is refused");
        assertTrue(Trough.capAllows(1000, 0), "0 means uncapped");
    }

    @Test
    void withinRadiusIsSpherical() {
        assertTrue(Trough.withinRadiusSq(100.0, 10.0), "exactly at the radius counts");
        assertFalse(Trough.withinRadiusSq(101.0, 10.0), "just outside does not");
    }

    @Test
    void aClaimBlocksOtherTroughsUntilReleasedOrExpired() {
        UUID animal = UUID.randomUUID();
        BlockPos troughA = new BlockPos(0, 64, 0);
        long now = 1000L;

        assertTrue(Trough.tryClaim(animal, troughA, now), "first claim succeeds");
        assertTrue(Trough.isClaimed(animal, now), "the animal is now claimed");
        assertEquals(troughA, Trough.claimedTrough(animal, now), "the claim names its trough");
        assertFalse(Trough.tryClaim(animal, new BlockPos(9, 64, 9), now), "a second trough cannot claim it");

        Trough.release(animal);
        assertFalse(Trough.isClaimed(animal, now), "release frees the animal");
        assertNull(Trough.claimedTrough(animal, now), "a released animal has no trough");
    }

    @Test
    void aClaimExpiresAfterTheTimeout() {
        UUID animal = UUID.randomUUID();
        long now = 0L;
        Trough.tryClaim(animal, new BlockPos(0, 64, 0), now);
        long afterTimeout = now + Trough.CLAIM_EXPIRY_TICKS;
        assertFalse(Trough.isClaimed(animal, afterTimeout), "the claim lapses at the 20-second timeout");
        assertNull(Trough.claimedTrough(animal, afterTimeout), "an expired claim names no trough");
    }

    @Test
    void babyCooldownGatesRepeatFeeding() {
        UUID baby = UUID.randomUUID();
        assertTrue(Trough.canBabyEat(baby, 0L), "a baby that never ate may eat");
        Trough.markBabyFed(baby, 0L);
        assertFalse(Trough.canBabyEat(baby, 599L), "not again within the cooldown");
        assertTrue(Trough.canBabyEat(baby, Trough.BABY_FEED_COOLDOWN_TICKS), "and again once it elapses");
    }
}
