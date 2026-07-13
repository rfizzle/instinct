package com.rfizzle.instinct.predatorwatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tier-1 coverage of §8's pure geometry: the interception point the guardian plants between a
 * predator and its prey, and the flee point it drives a predator to so it paths away from the
 * pasture. Primitive in / primitive out — no Minecraft bootstrap. (The predator-set resolution and
 * the goal's live behavior need registry and world state, so they are covered in the gametest.)
 */
class PredatorWatchTest {

    @Test
    void interceptPointSitsOnThePreySideOfThePredator() {
        // predator at (10,0), prey at the origin: the axis points back toward the prey, so a
        // 1.5-block standoff lands at (8.5, 0) — between the predator and its target.
        double[] point = PredatorWatch.interceptPoint(10.0, 0.0, 0.0, 0.0, 1.5);
        assertEquals(8.5, point[0], 1.0e-9, "intercept is toward the prey from the predator");
        assertEquals(0.0, point[1], 1.0e-9);
    }

    @Test
    void interceptPointFollowsADiagonalAxis() {
        // predator at (0,0), prey at (3,4): axis length 5, so a 5-block standoff lands on the prey.
        double[] point = PredatorWatch.interceptPoint(0.0, 0.0, 3.0, 4.0, 5.0);
        assertEquals(3.0, point[0], 1.0e-9);
        assertEquals(4.0, point[1], 1.0e-9);
    }

    @Test
    void interceptPointDegeneratesToThePredatorWhenPreyCoincides() {
        double[] point = PredatorWatch.interceptPoint(5.0, 5.0, 5.0, 5.0, 1.5);
        assertEquals(5.0, point[0], 1.0e-9);
        assertEquals(5.0, point[1], 1.0e-9);
    }

    @Test
    void fleePointDrivesThePredatorDirectlyAwayFromTheAnchor() {
        // predator at (10,0), guardian anchor at the origin: 8 blocks further out is (18, 0).
        double[] point = PredatorWatch.fleePoint(10.0, 0.0, 0.0, 0.0, 8.0);
        assertEquals(18.0, point[0], 1.0e-9, "flee point is further from the anchor along the same axis");
        assertEquals(0.0, point[1], 1.0e-9);
    }

    @Test
    void fleePointFollowsADiagonalAxis() {
        // predator at (3,4) from the anchor: axis length 5, so 5 blocks further out lands at (6,8).
        double[] point = PredatorWatch.fleePoint(3.0, 4.0, 0.0, 0.0, 5.0);
        assertEquals(6.0, point[0], 1.0e-9);
        assertEquals(8.0, point[1], 1.0e-9);
    }

    @Test
    void fleePointDegeneratesToThePredatorWhenOnTheAnchor() {
        double[] point = PredatorWatch.fleePoint(5.0, 5.0, 5.0, 5.0, 8.0);
        assertEquals(5.0, point[0], 1.0e-9);
        assertEquals(5.0, point[1], 1.0e-9);
    }
}
