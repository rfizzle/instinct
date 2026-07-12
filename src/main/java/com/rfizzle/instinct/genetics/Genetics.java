package com.rfizzle.instinct.genetics;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.Perk;

import java.util.function.DoubleSupplier;

/**
 * Pure genetics math ({@code design/SPEC.md} §3): grade inheritance, perk inheritance, the fertile
 * love-cooldown scale, and the per-grade yield counts. Every method takes plain values and a
 * {@link DoubleSupplier} of {@code [0,1)} rolls, so it unit-tests with a seeded random and no game.
 */
public final class Genetics {

    /** The four perks a grade-1+ newborn can be born with, in resolution order (never {@code NONE}). */
    static final Perk[] PERK_POOL = {Perk.HARDY, Perk.FLEET, Perk.FERTILE, Perk.PLACID};

    /** Fertile shortens the post-breed love cooldown by this fraction per grade (prime = 30%). */
    static final double FERTILE_COOLDOWN_REDUCTION_PER_GRADE = 0.15;

    /** Grade shortens a living renewable's cadence (egg interval, wool regrowth) by this fraction per grade. */
    static final double GRADE_RENEWABLE_REDUCTION_PER_GRADE = 0.10;

    private Genetics() {
    }

    /**
     * The child's grade ({@code design/SPEC.md} §3 inheritance). Base is the parents' floored
     * average; a well-fed-not-crowded breeding rolls {@code upgradeChance} for +1, a
     * crowded-not-well-fed breeding rolls {@code downgradeChance} for −1, and both-or-neither
     * leaves the base. Always clamped to {@code ORDINARY}–{@code PRIME}.
     */
    public static int resolveGrade(int gradeA, int gradeB, boolean wellFed, boolean crowded,
                                   double upgradeChance, double downgradeChance, DoubleSupplier roll) {
        int base = Math.clamp((gradeA + gradeB) / 2,
                Grade.ORDINARY.level(), Grade.PRIME.level());
        if (wellFed && !crowded) {
            if (roll.getAsDouble() < upgradeChance) {
                base++;
            }
        } else if (crowded && !wellFed) {
            if (roll.getAsDouble() < downgradeChance) {
                base--;
            }
        }
        return Math.clamp(base, Grade.ORDINARY.level(), Grade.PRIME.level());
    }

    /**
     * The child's birth perk ({@code design/SPEC.md} §3 perk inheritance). The parents' perks bias
     * the roll only when the breeding is well-fed; otherwise it is uniform across the four-perk
     * pool. An uncovered or grade-0 parent counts as perkless ({@link Perk#NONE}).
     *
     * <ul>
     *   <li>both parents share perk P → P at 80%, else uniform among the other three;
     *   <li>parents carry different perks P and Q → 40% / 40%, uniform 20%;
     *   <li>exactly one parent has a perk P → P at 50%, uniform 50%;
     *   <li>neither parent has a perk → uniform.
     * </ul>
     *
     * <p>Caller resolves this only for a grade-1+ child; a grade-0 child is always {@link Perk#NONE}.
     */
    public static Perk resolvePerk(Perk perkA, Perk perkB, boolean wellFed, DoubleSupplier roll) {
        Perk a = perkA == null ? Perk.NONE : perkA;
        Perk b = perkB == null ? Perk.NONE : perkB;
        boolean hasA = a != Perk.NONE;
        boolean hasB = b != Perk.NONE;

        if (!wellFed || (!hasA && !hasB)) {
            return uniform(roll);
        }
        double r = roll.getAsDouble();
        if (hasA && hasB && a == b) {
            return r < 0.80 ? a : uniformExcluding(a, roll);
        }
        if (hasA && hasB) {
            if (r < 0.40) return a;
            if (r < 0.80) return b;
            return uniform(roll);
        }
        Perk only = hasA ? a : b;
        return r < 0.50 ? only : uniform(roll);
    }

    /** A uniform draw across the whole four-perk pool. */
    private static Perk uniform(DoubleSupplier roll) {
        return PERK_POOL[poolIndex(roll)];
    }

    /** A uniform draw across the three perks other than {@code excluded}. */
    private static Perk uniformExcluding(Perk excluded, DoubleSupplier roll) {
        int index = (int) (roll.getAsDouble() * (PERK_POOL.length - 1));
        index = Math.clamp(index, 0, PERK_POOL.length - 2);
        int seen = 0;
        for (Perk perk : PERK_POOL) {
            if (perk == excluded) {
                continue;
            }
            if (seen == index) {
                return perk;
            }
            seen++;
        }
        return PERK_POOL[0]; // unreachable: excluded is always in the pool
    }

    private static int poolIndex(DoubleSupplier roll) {
        return Math.clamp((int) (roll.getAsDouble() * PERK_POOL.length), 0, PERK_POOL.length - 1);
    }

    /**
     * The post-breed love cooldown for a parent, scaled by its own fertile perk × grade — fertile
     * prime returns 30% off a 6000-tick base (4200). Any non-fertile parent returns the base
     * unchanged.
     */
    public static int scaledLoveCooldown(int baseCooldown, Perk perk, int grade) {
        if (perk != Perk.FERTILE || grade <= 0) {
            return baseCooldown;
        }
        double factor = 1.0 - FERTILE_COOLDOWN_REDUCTION_PER_GRADE * grade;
        return (int) Math.round(baseCooldown * Math.max(0.0, factor));
    }

    /**
     * The multiplier a covered animal applies to a living renewable's cadence — the chicken egg
     * interval and the sheep wool-regrowth graze roll ({@code design/SPEC.md} §3 renewables). Grade
     * shortens it {@value #GRADE_RENEWABLE_REDUCTION_PER_GRADE} per grade; the fertile perk shortens
     * it a further {@code fertileReductionPerGrade} per grade, composed multiplicatively so a prime
     * fertile animal is the ceiling. A grade-0 or non-fertile-at-grade-0 animal returns {@code 1.0}
     * (no change). Clamped to {@code [0.01, 1.0]} so a maxed config can never zero a cadence.
     */
    public static double renewableIntervalFactor(int grade, Perk perk, double fertileReductionPerGrade) {
        if (grade <= 0) {
            return 1.0;
        }
        double gradeFactor = 1.0 - GRADE_RENEWABLE_REDUCTION_PER_GRADE * grade;
        double fertileFactor = perk == Perk.FERTILE ? 1.0 - fertileReductionPerGrade * grade : 1.0;
        return Math.clamp(gradeFactor * fertileFactor, 0.01, 1.0);
    }

    /** Bonus primary-product count by grade: sturdy +1, prime +2 ({@code design/SPEC.md} §3 yield). */
    public static int primaryBonus(int grade) {
        return Math.clamp(grade, 0, Grade.PRIME.level());
    }

    /**
     * Bonus secondary-product count by grade: sturdy is a 50% chance of +1, prime is a flat +1
     * ({@code design/SPEC.md} §3 yield). Deterministic given the roll.
     */
    public static int secondaryBonus(int grade, DoubleSupplier roll) {
        if (grade >= Grade.PRIME.level()) {
            return 1;
        }
        if (grade == Grade.STURDY.level()) {
            return roll.getAsDouble() < 0.5 ? 1 : 0;
        }
        return 0;
    }
}
