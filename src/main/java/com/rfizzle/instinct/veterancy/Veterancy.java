package com.rfizzle.instinct.veterancy;

import java.util.List;

/**
 * Pure veterancy math ({@code design/SPEC.md} §2). Rank is always derived from accrued days
 * against the current threshold list, never stored — threshold config edits promote or demote on
 * the next derivation.
 */
public final class Veterancy {

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
}
