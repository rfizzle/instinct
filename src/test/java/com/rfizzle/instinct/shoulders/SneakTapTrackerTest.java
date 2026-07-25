package com.rfizzle.instinct.shoulders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The double-tap sneak gesture (SPEC §1): two quick sneak taps set a perched pet down, and everything
 * a player does with sneak incidentally — crouching to place a block, holding a crouch on a ledge, one
 * stray tap — does not. The window boundaries are pinned on both sides, since this is the whole
 * difference between a deliberate gesture and the plain-sneak rule it replaces.
 */
class SneakTapTrackerTest {

    private static final int WINDOW = 12;

    private SneakTapTracker tracker;
    private long tick;

    @BeforeEach
    void setUp() {
        tracker = new SneakTapTracker(false);
        tick = 0;
    }

    /** Advances exactly one tick with the given sneak state. */
    private boolean at(boolean sneaking) {
        return tracker.advance(sneaking, tick++, WINDOW);
    }

    /** Advances n ticks with sneak released, none of which may complete the gesture. */
    private void idle(int ticks) {
        for (int i = 0; i < ticks; i++) {
            assertFalse(at(false), "an idle tick completed the gesture");
        }
    }

    @Test
    void twoQuickTapsComplete() {
        assertFalse(at(true), "the first press alone is not the gesture");
        assertFalse(at(false), "the first release alone is not the gesture");
        assertTrue(at(true), "the second press completes the double tap");
    }

    @Test
    void singleTapNeverCompletes() {
        assertFalse(at(true));
        assertFalse(at(false));
        idle(40);
    }

    @Test
    void heldSneakNeverCompletes() {
        // The crouch a player holds to work a ledge or place a block precisely.
        for (int i = 0; i < 40; i++) {
            assertFalse(at(true), "a held crouch completed the gesture at tick " + i);
        }
    }

    @Test
    void tapAtTheWindowEdgeStillCompletes() {
        assertFalse(at(true));          // press  @0
        assertFalse(at(false));         // release @1, tap banked
        idle(11);                       // ticks 2..12
        assertTrue(at(true), "a second press exactly " + WINDOW + " ticks after the tap completes");
    }

    @Test
    void tapOneTickBeyondTheWindowDoesNotCombine() {
        assertFalse(at(true));          // press  @0
        assertFalse(at(false));         // release @1, tap banked
        idle(12);                       // ticks 2..13
        assertFalse(at(true), "a second press " + (WINDOW + 1) + " ticks after the tap is a fresh press");
    }

    @Test
    void aCrouchHeldPastTheWindowCannotOpenAGesture() {
        assertFalse(at(true));                      // press @0
        for (int i = 0; i < WINDOW + 1; i++) {
            assertFalse(at(true));                  // held through @13
        }
        assertFalse(at(false), "a long crouch banks no tap on release");
        assertFalse(at(true), "a press after a long crouch has nothing banked to pair with");
    }

    @Test
    void aCrouchAlreadyUnderwayWhenTrackingStartsBanksNothing() {
        // A bird landing on an already-crouching shoulder must not be shrugged straight back off:
        // seeding the tracker means the first sample is not read as a press.
        tracker = new SneakTapTracker(true);
        assertFalse(at(true), "the seeded state is not a press");
        assertFalse(at(false), "releasing a crouch that was never observed pressing banks no tap");
        assertFalse(at(true), "so the next press has nothing to pair with");
    }

    @Test
    void completingTheGestureResetsSoTheNextTapStartsOver() {
        assertFalse(at(true));
        assertFalse(at(false));
        assertTrue(at(true), "precondition: the gesture completes");
        assertFalse(at(false), "the completing press does not bank as a fresh tap");
        assertFalse(at(true), "so the very next press cannot re-fire");
    }

    @Test
    void standingDownAbandonsAGestureInFlight() {
        assertFalse(at(true));
        assertFalse(at(false), "precondition: a tap is banked");
        tracker.standDown(false);
        assertFalse(at(true), "the banked tap did not survive standing down");
    }

    @Test
    void standingDownReSeedsTheObservedSneakState() {
        assertFalse(at(true));
        tracker.standDown(true);
        assertFalse(at(true), "a continuing hold is not re-read as a press after standing down");
        assertFalse(at(false), "and releasing it banks no tap");
        assertFalse(at(true));
    }

    @Test
    void aWindowOfTwoStillAcceptsTheTightestTap() {
        // The configured floor: press, release, press on three consecutive ticks.
        tracker = new SneakTapTracker(false);
        assertFalse(tracker.advance(true, 0L, 2));
        assertFalse(tracker.advance(false, 1L, 2));
        assertTrue(tracker.advance(true, 2L, 2), "the tightest possible double tap completes");
    }

    @Test
    void aGameTimeOfZeroIsATickLikeAnyOther() {
        // The banked-tap sentinel must not collide with a real tick value at world start.
        tracker = new SneakTapTracker(false);
        assertFalse(tracker.advance(true, 0L, WINDOW));
        assertFalse(tracker.advance(false, 0L, WINDOW), "a release banks on tick 0");
        assertTrue(tracker.advance(true, 1L, WINDOW), "and pairs with the next press");
    }
}
