package com.rfizzle.instinct.downed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "beyond saving" classification ({@code design/SPEC.md} §7): fire, lava, the void, or a kill
 * command each kill outright; anything else is cancelled into the downed state. Tested through the
 * pure boolean core so no game instance is needed.
 */
class DownedTest {

    @Test
    void ordinaryLethalDamageIsSurvivable() {
        assertFalse(Downed.beyondSaving(false, false, false, false),
                "a normal lethal blow (arrow, creeper, mob attack, fall) cancels into downed");
    }

    @Test
    void fireIsBeyondSaving() {
        assertTrue(Downed.beyondSaving(true, false, false, false));
    }

    @Test
    void lavaIsBeyondSaving() {
        assertTrue(Downed.beyondSaving(false, true, false, false));
    }

    @Test
    void voidIsBeyondSaving() {
        assertTrue(Downed.beyondSaving(false, false, true, false));
    }

    @Test
    void killCommandIsBeyondSaving() {
        assertTrue(Downed.beyondSaving(false, false, false, true));
    }

    @Test
    void anyEdgeAloneIsFatal() {
        // Each of the four edges independently forces a real death — no combination is required.
        assertTrue(Downed.beyondSaving(true, true, true, true));
    }
}
