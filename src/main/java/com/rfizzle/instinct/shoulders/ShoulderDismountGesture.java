package com.rfizzle.instinct.shoulders;

/**
 * The deliberate gesture that sets a perched pet down ({@code design/SPEC.md} §1). Config
 * vocabulary only — the motion a player makes, independent of the tick plumbing that spots it.
 *
 * <p>Declaration order is the order the Cloth selector cycles through, so the default leads.
 */
public enum ShoulderDismountGesture {

    /**
     * Two quick sneak taps in a row. Crouching to place a block, work a ledge, or peek over an edge
     * leaves the bird where it is.
     */
    DOUBLE_TAP_SNEAK,

    /** Any sneak at all, tapped or held. */
    SNEAK
}
