package com.rfizzle.instinct.downed;

/**
 * Pure carry-eligibility and cost logic ({@code design/SPEC.md} §7). Game-instance-free so it
 * unit-tests without a world — the {@link CarryHandler} shell resolves the game state (downed flag,
 * membership, baby, tag) and passes plain booleans/doubles here.
 */
public final class Carry {

    private Carry() {
    }

    /**
     * Whether a downed animal is small enough to be scooped up and carried. Only a <b>downed</b>,
     * <b>pets-set</b> animal qualifies, and only when it is a baby (a pup) or its type is tagged
     * carryable (cat, parrot, plus any a mod adds). A mount is never carryable — it stays where it
     * falls — which the caller enforces by passing {@code pet=false} for a mounts-set animal.
     */
    public static boolean carryable(boolean downed, boolean pet, boolean baby, boolean taggedCarryable) {
        return downed && pet && (baby || taggedCarryable);
    }

    /**
     * The {@code MOVEMENT_SPEED} modifier amount for the carry slowdown — a negative
     * {@code ADD_MULTIPLIED_TOTAL} value, so a fraction of {@code 0.30} scales the carrier's speed to
     * {@code ×0.70}. The fraction is assumed already clamped to {@code [0, 0.9]} by config validation.
     */
    public static double slowdownAmount(double fraction) {
        return -fraction;
    }
}
