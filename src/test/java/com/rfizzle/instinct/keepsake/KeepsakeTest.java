package com.rfizzle.instinct.keepsake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The keepsake-collar classification ({@code design/SPEC.md} §7): fire, lava, or the void each leave
 * an engraved collar; any other death leaves none. Tested through the pure boolean core so no game
 * instance is needed. The {@code /kill} exclusion is not visible here — it is dropped one layer up,
 * in the {@link Keepsake#keepsakeWorthy(net.minecraft.world.damagesource.DamageSource)} source map,
 * which never passes a kill flag into this core.
 */
class KeepsakeTest {

    @Test
    void ordinaryDeathLeavesNoKeepsake() {
        assertFalse(Keepsake.keepsakeWorthy(false, false, false),
                "a mob, fall, or drowning death leaves no collar — only a beyond-saving loss does");
    }

    @Test
    void fireLeavesAKeepsake() {
        assertTrue(Keepsake.keepsakeWorthy(true, false, false));
    }

    @Test
    void lavaLeavesAKeepsake() {
        assertTrue(Keepsake.keepsakeWorthy(false, true, false));
    }

    @Test
    void voidLeavesAKeepsake() {
        assertTrue(Keepsake.keepsakeWorthy(false, false, true));
    }

    @Test
    void anyEdgeAloneLeavesAKeepsake() {
        assertTrue(Keepsake.keepsakeWorthy(true, false, false));
        assertTrue(Keepsake.keepsakeWorthy(false, true, false));
        assertTrue(Keepsake.keepsakeWorthy(false, false, true));
    }
}
