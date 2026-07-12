package com.rfizzle.instinct.genetics;

import com.rfizzle.instinct.api.Perk;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure genetics math ({@code design/SPEC.md} §3): grade inheritance across all nine parent-grade
 * pairs under each feeding/crowding state, the perk-inheritance branches with scripted rolls, the
 * fertile cooldown arithmetic, and the per-grade yield counts. No game, seeded rolls only.
 */
class GeneticsTest {

    /** A DoubleSupplier that hands back a fixed script of rolls, then holds the last value. */
    private static DoubleSupplier rolls(double... values) {
        Deque<Double> queue = new ArrayDeque<>();
        for (double v : values) {
            queue.add(v);
        }
        return () -> queue.size() > 1 ? queue.poll() : queue.peek();
    }

    // ---- resolveGrade: base average across all 9 pairs ----

    @Test
    void baseGradeIsFlooredAverageWhenNeitherModifierApplies() {
        int[][] expectedBase = {
                {0, 0, 1}, // ordinary × {ordinary, sturdy, prime}
                {0, 1, 1}, // sturdy   × {ordinary, sturdy, prime}
                {1, 1, 2}, // prime    × {ordinary, sturdy, prime}
        };
        for (int a = 0; a <= 2; a++) {
            for (int b = 0; b <= 2; b++) {
                // Neither well-fed nor crowded: the base is returned untouched, roll never consulted.
                int neither = Genetics.resolveGrade(a, b, false, false, 1.0, 1.0, rolls(0.0));
                assertEquals(expectedBase[a][b], neither, "base " + a + "x" + b);
                // Both well-fed and crowded: also untouched.
                int both = Genetics.resolveGrade(a, b, true, true, 1.0, 1.0, rolls(0.0));
                assertEquals(expectedBase[a][b], both, "both " + a + "x" + b);
            }
        }
    }

    @Test
    void wellFedNotCrowdedUpgradesOnASuccessfulRollAndCapsAtPrime() {
        // chance 1.0, roll below → +1; sturdy base (1x1) → prime.
        assertEquals(2, Genetics.resolveGrade(1, 1, true, false, 1.0, 0.0, rolls(0.0)));
        // prime base already at cap: +1 clamps to prime.
        assertEquals(2, Genetics.resolveGrade(2, 2, true, false, 1.0, 0.0, rolls(0.0)));
        // roll at/above the chance → no upgrade.
        assertEquals(1, Genetics.resolveGrade(1, 1, true, false, 1.0, 0.0, rolls(1.0)));
    }

    @Test
    void crowdedNotWellFedDowngradesOnASuccessfulRollAndFloorsAtOrdinary() {
        assertEquals(0, Genetics.resolveGrade(1, 1, false, true, 0.0, 1.0, rolls(0.0)));
        // ordinary base already at floor: −1 clamps to ordinary.
        assertEquals(0, Genetics.resolveGrade(0, 0, false, true, 0.0, 1.0, rolls(0.0)));
        assertEquals(1, Genetics.resolveGrade(1, 1, false, true, 0.0, 1.0, rolls(1.0)));
    }

    // ---- resolvePerk branches ----

    @Test
    void notWellFedAlwaysRollsUniformIgnoringParents() {
        // Two hardy parents, not well-fed: the 0.0 roll lands on the first pool entry uniformly.
        assertEquals(Perk.HARDY, Genetics.resolvePerk(Perk.HARDY, Perk.HARDY, false, rolls(0.0)));
        // A roll landing in the fleet slot proves the parents' shared perk did not bias it.
        assertEquals(Perk.FLEET, Genetics.resolvePerk(Perk.HARDY, Perk.HARDY, false, rolls(0.25)));
    }

    @Test
    void sharedParentPerkIsEightyPercentWellFed() {
        // roll < 0.80 → the shared perk.
        assertEquals(Perk.FLEET, Genetics.resolvePerk(Perk.FLEET, Perk.FLEET, true, rolls(0.79)));
        // roll >= 0.80 → uniform among the other three (0.80 maps to the first non-fleet perk).
        Perk other = Genetics.resolvePerk(Perk.FLEET, Perk.FLEET, true, rolls(0.80, 0.0));
        assertTrue(other != Perk.FLEET && other != Perk.NONE, "the 20% branch excludes the shared perk");
    }

    @Test
    void differentParentPerksAreFortyFortyTwentyWellFed() {
        assertEquals(Perk.HARDY, Genetics.resolvePerk(Perk.HARDY, Perk.PLACID, true, rolls(0.39)));
        assertEquals(Perk.PLACID, Genetics.resolvePerk(Perk.HARDY, Perk.PLACID, true, rolls(0.79)));
        // the 20% tail rolls uniform; a second roll of 0.0 selects the first pool entry.
        assertEquals(Perk.HARDY, Genetics.resolvePerk(Perk.HARDY, Perk.PLACID, true, rolls(0.80, 0.0)));
    }

    @Test
    void singleParentPerkIsFiftyFiftyWellFed() {
        assertEquals(Perk.FERTILE, Genetics.resolvePerk(Perk.FERTILE, Perk.NONE, true, rolls(0.49)));
        // roll >= 0.50 → uniform; second roll 0.0 → first pool entry.
        assertEquals(Perk.HARDY, Genetics.resolvePerk(Perk.NONE, Perk.FERTILE, true, rolls(0.50, 0.0)));
    }

    @Test
    void perklessParentsRollUniform() {
        assertEquals(Perk.HARDY, Genetics.resolvePerk(Perk.NONE, Perk.NONE, true, rolls(0.0)));
        assertEquals(Perk.PLACID, Genetics.resolvePerk(Perk.NONE, Perk.NONE, true, rolls(0.99)));
    }

    @Test
    void resolvePerkNeverReturnsNone() {
        Random random = new Random(1234);
        DoubleSupplier roll = random::nextDouble;
        for (int i = 0; i < 500; i++) {
            assertTrue(Genetics.resolvePerk(Perk.HARDY, Perk.FLEET, i % 2 == 0, roll) != Perk.NONE,
                    "a grade-1+ newborn always gets a real perk");
        }
    }

    @Test
    void sharedPerkDistributionIsRoughlyEightyPercent() {
        Random random = new Random(42);
        DoubleSupplier roll = random::nextDouble;
        Map<Perk, Integer> counts = new EnumMap<>(Perk.class);
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            counts.merge(Genetics.resolvePerk(Perk.FLEET, Perk.FLEET, true, roll), 1, Integer::sum);
        }
        double fleetShare = counts.getOrDefault(Perk.FLEET, 0) / (double) n;
        assertTrue(Math.abs(fleetShare - 0.80) < 0.03, "shared-perk share ~80%, got " + fleetShare);
    }

    // ---- fertile cooldown ----

    @Test
    void fertileScalesLoveCooldownByGradeAndLeavesOthersAlone() {
        assertEquals(4200, Genetics.scaledLoveCooldown(6000, Perk.FERTILE, 2), "prime fertile = −30%");
        assertEquals(5100, Genetics.scaledLoveCooldown(6000, Perk.FERTILE, 1), "sturdy fertile = −15%");
        assertEquals(6000, Genetics.scaledLoveCooldown(6000, Perk.FERTILE, 0), "grade 0 = no change");
        assertEquals(6000, Genetics.scaledLoveCooldown(6000, Perk.HARDY, 2), "non-fertile = no change");
    }

    // ---- yield counts ----

    @Test
    void primaryBonusIsGradeCount() {
        assertEquals(0, Genetics.primaryBonus(0));
        assertEquals(1, Genetics.primaryBonus(1));
        assertEquals(2, Genetics.primaryBonus(2));
    }

    @Test
    void secondaryBonusIsPrimeFlatAndSturdyFiftyPercent() {
        assertEquals(1, Genetics.secondaryBonus(2, rolls(0.99)), "prime always +1");
        assertEquals(1, Genetics.secondaryBonus(1, rolls(0.49)), "sturdy +1 on a sub-0.5 roll");
        assertEquals(0, Genetics.secondaryBonus(1, rolls(0.50)), "sturdy nothing on a 0.5+ roll");
        assertEquals(0, Genetics.secondaryBonus(0, rolls(0.0)), "ordinary never");
    }

    // ---- renewable cadence ----

    @Test
    void renewableFactorIsGradeOnlyForNonFertile() {
        // A non-fertile animal ignores the fertile reduction entirely: grade drives it alone, exactly
        // as the shipped egg scaling did (sturdy 0.90, prime 0.80).
        assertEquals(1.00, Genetics.renewableIntervalFactor(0, Perk.HARDY, 0.15), 1e-9, "grade 0 = no change");
        assertEquals(0.90, Genetics.renewableIntervalFactor(1, Perk.HARDY, 0.15), 1e-9, "sturdy = −10%");
        assertEquals(0.80, Genetics.renewableIntervalFactor(2, Perk.NONE, 0.15), 1e-9, "prime = −20%");
    }

    @Test
    void renewableFactorFoldsFertileMultiplicatively() {
        // Fertile stacks on grade multiplicatively at the default −15%×grade register.
        assertEquals(1.00, Genetics.renewableIntervalFactor(0, Perk.FERTILE, 0.15), 1e-9, "grade 0 fertile = no change");
        assertEquals(0.765, Genetics.renewableIntervalFactor(1, Perk.FERTILE, 0.15), 1e-9, "sturdy fertile = 0.90×0.85");
        assertEquals(0.56, Genetics.renewableIntervalFactor(2, Perk.FERTILE, 0.15), 1e-9, "prime fertile = 0.80×0.70");
    }

    @Test
    void renewableFactorZeroReductionLeavesFertileAsGradeOnly() {
        // The knob at 0 confines fertile back to breeding: a fertile animal matches a non-fertile one.
        assertEquals(0.80, Genetics.renewableIntervalFactor(2, Perk.FERTILE, 0.0), 1e-9, "prime fertile == prime");
        assertEquals(0.90, Genetics.renewableIntervalFactor(1, Perk.FERTILE, 0.0), 1e-9, "sturdy fertile == sturdy");
    }

    @Test
    void renewableFactorFloorsSoAMaxedKnobNeverZerosACadence() {
        // prime fertile at the 0.5 ceiling: 0.80 × (1 − 1.0) = 0, clamped up to the 0.01 floor.
        assertEquals(0.01, Genetics.renewableIntervalFactor(2, Perk.FERTILE, 0.5), 1e-9, "clamped to the floor, never 0");
    }
}
