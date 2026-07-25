package com.rfizzle.instinct.shoulders;

/**
 * Pure detector for the double-tap sneak gesture ({@code design/SPEC.md} §1). One tracker rides each
 * player; {@link #advance} takes a single sneak sample per tick and answers whether the gesture
 * completed on that tick.
 *
 * <p>A tap banks on <em>release</em>, and only when the press was held no longer than the window, so
 * a held crouch never contributes one — which is the whole point of the gesture. A banked tap
 * expires once the window passes, and completing the gesture clears the state, so a third tap begins
 * a fresh gesture rather than firing again off the second.
 *
 * <p>Detection keys on observed edges rather than the raw sneak state, so a crouch already underway
 * when the tracker starts watching can never bank a tap: the tracker is seeded with the current
 * state (the constructor, and {@link #standDown} whenever the shoulder empties), so there is no
 * synthetic press for the tick it begins on.
 *
 * <p>Holds no game state and allocates nothing per tick — the shell supplies the clock — so this is
 * unit-testable with primitives alone.
 */
public final class SneakTapTracker {

    /** Sentinel for "no press or tap on record"; a real game-time tick can be 0 but never this. */
    private static final long NONE = Long.MIN_VALUE;

    private boolean sneaking;
    private long pressTick = NONE;
    private long bankedTapTick = NONE;

    /** @param sneaking the player's sneak state right now, so the first sample is not read as a press */
    public SneakTapTracker(boolean sneaking) {
        this.sneaking = sneaking;
    }

    /**
     * Feeds one tick's sneak state in.
     *
     * @param sneakingNow whether the player holds sneak on this tick
     * @param tick        a monotonic tick clock (the level's game time)
     * @param windowTicks how long a tap may be held, and how long a banked tap stays live
     * @return true on exactly the tick the second tap's press lands — the gesture completes on the
     *         press, not the release, since by then the owner has committed to it
     */
    public boolean advance(boolean sneakingNow, long tick, int windowTicks) {
        boolean wasSneaking = sneaking;
        sneaking = sneakingNow;

        if (sneakingNow && !wasSneaking) {
            pressTick = tick;
            if (bankedTapTick != NONE && tick - bankedTapTick <= windowTicks) {
                clear();
                return true;
            }
            return false;
        }
        if (!sneakingNow && wasSneaking) {
            // A press held past the window is a crouch, not a tap, so it banks nothing and cannot
            // serve as the opening half of a gesture.
            boolean tapped = pressTick != NONE && tick - pressTick <= windowTicks;
            bankedTapTick = tapped ? tick : NONE;
            pressTick = NONE;
            return false;
        }
        if (bankedTapTick != NONE && tick - bankedTapTick > windowTicks) {
            bankedTapTick = NONE;
        }
        return false;
    }

    /**
     * Abandons any gesture in flight and re-seeds the observed sneak state — called on every tick the
     * player has no perched pet to drop, so a gesture made with an empty shoulder cannot bank toward
     * a later one, and a bird landing mid-crouch is not shrugged straight back off.
     */
    public void standDown(boolean sneakingNow) {
        sneaking = sneakingNow;
        clear();
    }

    private void clear() {
        pressTick = NONE;
        bankedTapTick = NONE;
    }
}
