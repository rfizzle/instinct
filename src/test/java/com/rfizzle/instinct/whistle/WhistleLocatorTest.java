package com.rfizzle.instinct.whistle;

import com.rfizzle.instinct.whistle.WhistleLocator.Compass8;
import com.rfizzle.instinct.whistle.WhistleLocator.PetState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SPEC §6 lost-pet locator geometry and text-key logic as pure functions: the eight-point compass
 * bearing (Minecraft axes, so north is {@code -z}), whole-block distance rounding, the same- vs
 * cross-dimension line-key choice, and the localized compass/posture key mapping. No Minecraft types
 * — the Tier-1 seam under {@code WhistleActions}.
 */
class WhistleLocatorTest {

    @Test
    void bearingResolvesTheFourCardinals() {
        assertEquals(Compass8.N, WhistleLocator.bearing(0, -10), "-z is north");
        assertEquals(Compass8.E, WhistleLocator.bearing(10, 0), "+x is east");
        assertEquals(Compass8.S, WhistleLocator.bearing(0, 10), "+z is south");
        assertEquals(Compass8.W, WhistleLocator.bearing(-10, 0), "-x is west");
    }

    @Test
    void bearingResolvesTheFourIntercardinals() {
        assertEquals(Compass8.NE, WhistleLocator.bearing(10, -10), "+x/-z is northeast");
        assertEquals(Compass8.SE, WhistleLocator.bearing(10, 10), "+x/+z is southeast");
        assertEquals(Compass8.SW, WhistleLocator.bearing(-10, 10), "-x/+z is southwest");
        assertEquals(Compass8.NW, WhistleLocator.bearing(-10, -10), "-x/-z is northwest");
    }

    @Test
    void bearingRoundsToTheNearestSector() {
        // Just east of due north (well under 22.5°) still reads north; past it tips to northeast.
        assertEquals(Compass8.N, WhistleLocator.bearing(1, -10), "a slight east lean of a northward pet still reads north");
        assertEquals(Compass8.NE, WhistleLocator.bearing(10, -9), "a strong east lean tips to northeast");
    }

    @Test
    void bearingOfAZeroOffsetIsNorth() {
        assertEquals(Compass8.N, WhistleLocator.bearing(0, 0), "a pet on top of the player defaults to north");
    }

    @Test
    void distanceRoundsToWholeBlocks() {
        assertEquals(240, WhistleLocator.roundedBlocks(240.2), "rounds down toward the nearer block");
        assertEquals(241, WhistleLocator.roundedBlocks(240.6), "rounds up toward the nearer block");
        assertEquals(0, WhistleLocator.roundedBlocks(0.0), "zero distance rounds to zero");
    }

    @Test
    void sameDimensionUsesTheDistanceAndBearingLine() {
        assertEquals("notification.instinct.whistle.locate.line", WhistleLocator.lineKey(true, false),
                "a same-dimension pet carries a distance/bearing line");
        assertEquals("notification.instinct.whistle.locate.line", WhistleLocator.lineKey(true, true),
                "a downed same-dimension pet still uses the distance/bearing line (its posture rides as an argument)");
    }

    @Test
    void crossDimensionFlagsTheDownedPatient() {
        assertEquals("notification.instinct.whistle.locate.line_other", WhistleLocator.lineKey(false, false),
                "a cross-dimension pet reports only its dimension");
        assertEquals("notification.instinct.whistle.locate.line_other_downed", WhistleLocator.lineKey(false, true),
                "a downed cross-dimension pet is flagged as the patient");
    }

    @Test
    void compassKeysMapToTheLangSurface() {
        assertEquals("notification.instinct.whistle.locate.dir.n", Compass8.N.langKey());
        assertEquals("notification.instinct.whistle.locate.dir.nw", Compass8.NW.langKey());
    }

    @Test
    void stateKeysMapToTheLangSurface() {
        assertEquals("notification.instinct.whistle.locate.state.sitting", PetState.SITTING.langKey());
        assertEquals("notification.instinct.whistle.locate.state.following", PetState.FOLLOWING.langKey());
        assertEquals("notification.instinct.whistle.locate.state.guarding", PetState.GUARDING.langKey());
        assertEquals("notification.instinct.whistle.locate.state.downed", PetState.DOWNED.langKey());
    }
}
