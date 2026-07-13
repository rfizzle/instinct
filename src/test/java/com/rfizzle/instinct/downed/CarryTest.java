package com.rfizzle.instinct.downed;

import com.rfizzle.instinct.config.InstinctConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carry eligibility and cost ({@code design/SPEC.md} §7): a downed, pets-set animal is carryable
 * when it is a baby or tagged carryable; the slowdown is a negative multiplier of the configured
 * fraction. Tested through the pure core so no game instance is needed.
 */
class CarryTest {

    @Test
    void downedBabyPetIsCarryable() {
        assertTrue(Carry.carryable(true, true, true, false),
                "a downed pup is carryable by size, tag or not");
    }

    @Test
    void downedTaggedPetIsCarryable() {
        assertTrue(Carry.carryable(true, true, false, true),
                "a downed cat/parrot is carryable by tag");
    }

    @Test
    void downedFullSizePetIsNotCarryable() {
        assertFalse(Carry.carryable(true, true, false, false),
                "a downed adult wolf is neither baby nor tagged — it stays where it falls");
    }

    @Test
    void healthyPetIsNotCarryable() {
        assertFalse(Carry.carryable(false, true, true, true),
                "only a downed animal can be carried — no pocketing healthy pets");
    }

    @Test
    void nonPetIsNotCarryable() {
        // A mounts-set animal reaches here with pet=false and is never carryable, even as a baby.
        assertFalse(Carry.carryable(true, false, true, true),
                "a mount stays where it falls, baby or not");
    }

    @Test
    void slowdownIsNegativeFraction() {
        assertEquals(-0.30, Carry.slowdownAmount(0.30), 1e-9);
        assertEquals(0.0, Carry.slowdownAmount(0.0), 1e-9);
    }

    @Test
    void configClampsSlowdownFractionToRange() {
        InstinctConfig config = new InstinctConfig();
        config.carrySlowdownFraction = 5.0;
        config.validate();
        assertEquals(0.9, config.carrySlowdownFraction, 1e-9, "above range clamps to 0.9");

        config.carrySlowdownFraction = -1.0;
        config.validate();
        assertEquals(0.0, config.carrySlowdownFraction, 1e-9, "below range clamps to 0.0");
    }
}
