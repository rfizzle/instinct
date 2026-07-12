package com.rfizzle.instinct.compat.tribulation;

/**
 * Pure tier-mapping math for the Tribulation veterancy integration (SPEC §Compat). Kept free of any
 * Tribulation import so it unit-tests without the sibling jar on the classpath: the compat glue
 * feeds it a local difficulty level and Tribulation's tier thresholds, and it decides the accrual
 * multiplier. Mirrors Tribulation's own inclusive-threshold tier semantics (a level exactly at the
 * tier-1 threshold is tier 1).
 */
public final class TribulationTier {

    /** Live accrual doubles at or above this local difficulty tier (SPEC §Compat). */
    public static final int DOUBLING_TIER = 3;

    /** The doubled rate applied at {@link #DOUBLING_TIER}+, and the un-integrated base below it. */
    public static final double DOUBLED_RATE = 2.0;
    public static final double BASE_RATE = 1.0;

    private TribulationTier() {
    }

    /** The tier for {@code level} — the count of ascending thresholds it meets (inclusive ≥). */
    public static int tierFor(int level, int[] thresholds) {
        int tier = 0;
        for (int threshold : thresholds) {
            if (level >= threshold) {
                tier++;
            }
        }
        return tier;
    }

    /** {@link #DOUBLED_RATE} when the level lands at tier {@value #DOUBLING_TIER} or above, else the base. */
    public static double rateFor(int level, int[] thresholds) {
        return tierFor(level, thresholds) >= DOUBLING_TIER ? DOUBLED_RATE : BASE_RATE;
    }
}
