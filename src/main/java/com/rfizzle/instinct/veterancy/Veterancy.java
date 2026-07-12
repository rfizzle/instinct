package com.rfizzle.instinct.veterancy;

import com.rfizzle.instinct.data.VeterancyData;

import java.util.List;

/**
 * Pure veterancy math ({@code design/SPEC.md} §2). Rank is always derived from accrued days
 * against the current threshold list, never stored — threshold config edits promote or demote on
 * the next derivation. Everything here is Minecraft-free so it unit-tests without a game.
 */
public final class Veterancy {

    /** One in-game day in ticks — the accrual divisor. */
    public static final double TICKS_PER_DAY = 24_000.0;

    private static final String[] RANK_KEYS = {
            "instinct.rank.seasoned",
            "instinct.rank.veteran",
            "instinct.rank.venerable",
    };

    private Veterancy() {
    }

    /** The rank (0–{@code thresholds.size()}) for {@code days} against ascending thresholds. */
    public static int rankFor(double days, List<Integer> thresholds) {
        int rank = 0;
        for (int threshold : thresholds) {
            if (days >= threshold) {
                rank++;
            }
        }
        return rank;
    }

    /** The {@code instinct.rank.*} key for a rank's display name; ranks 1–3 only. */
    public static String rankKey(int rank) {
        return RANK_KEYS[Math.clamp(rank, 1, RANK_KEYS.length) - 1];
    }

    /**
     * The accrued-day count that lands a pet exactly one rank below {@code currentRank} — the
     * revival penalty ({@code design/SPEC.md} §7): rank <i>n</i> drops to the threshold of rank
     * <i>n−1</i> (rank 1, and any rank ≤ 0, drop to 0 days). Sitting on a threshold means a
     * further live day re-crosses it, which is the intended "just demoted" state.
     */
    public static double daysForRankBelow(int currentRank, List<Integer> thresholds) {
        int newRank = currentRank - 1;
        if (newRank <= 0 || thresholds.isEmpty()) {
            return 0.0;
        }
        return thresholds.get(Math.min(newRank, thresholds.size()) - 1);
    }

    /**
     * Advances an accrual window: adds {@code (now − lastAccrualGameTime) / 24000 × rate} days and
     * stamps {@code now}. A zero-or-negative {@code lastAccrualGameTime} is the "never accrued"
     * sentinel — the window opens at {@code now} with no days credited (a fresh tame starts at 0,
     * never at the world's age). Time that failed to advance (or went backwards) restamps without
     * crediting, so a clock anomaly can never mint or refund days.
     */
    public static VeterancyData accrue(VeterancyData data, long now, double rate) {
        if (data.lastAccrualGameTime() <= 0L || now <= data.lastAccrualGameTime()) {
            return new VeterancyData(data.accruedDays(), now);
        }
        double gained = (now - data.lastAccrualGameTime()) / TICKS_PER_DAY * rate;
        return new VeterancyData(data.accruedDays() + gained, now);
    }

    /**
     * A veterancy-rate provider's return, sanitized per the API contract: non-finite or
     * non-positive values fall back to 1.0.
     */
    public static double clampProviderRate(double rate) {
        return Double.isFinite(rate) && rate > 0.0 ? rate : 1.0;
    }

    /**
     * The live accrual rate: the (already clamped) provider rate composed multiplicatively with
     * the mentor bonus — e.g. Tribulation's 2.0 × a mentor's 1.25 = 2.5. Mentoring never stacks:
     * any number of mentors in range yields the one bonus.
     */
    public static double liveRate(double providerRate, boolean mentorInRange, double mentorRateBonus) {
        return providerRate * (mentorInRange ? 1.0 + mentorRateBonus : 1.0);
    }

    /**
     * Pure core of the sweep-attack guard ({@code design/SPEC.md} §2 "Knows your swing"): only a
     * pet at rank 2+ ducks, and only its own owner's sweep.
     */
    public static boolean ducksSweep(int rank, boolean ownedByAttacker) {
        return rank >= 2 && ownedByAttacker;
    }
}
