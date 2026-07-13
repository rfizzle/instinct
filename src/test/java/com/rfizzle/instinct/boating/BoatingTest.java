package com.rfizzle.instinct.boating;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of §4's boat-boarding pure core: the boarding-eligibility conjunction and the
 * deterministic seat pick (nearest, ties broken by UUID). All primitive in / primitive out — no
 * Minecraft bootstrap.
 */
class BoatingTest {

    @Test
    void eligibleOnlyWhenEveryConditionHolds() {
        assertTrue(Boating.eligibleToBoard(true, true, true, false, false, true, true),
                "a following, tamed, undowned, unseated pet whose owner is in a boat with a free seat boards");
    }

    @Test
    void anyMissingConditionBlocksBoarding() {
        assertFalse(Boating.eligibleToBoard(false, true, true, false, false, true, true), "feature off");
        assertFalse(Boating.eligibleToBoard(true, false, true, false, false, true, true), "not tamed");
        assertFalse(Boating.eligibleToBoard(true, true, false, false, false, true, true), "sitting, not following");
        assertFalse(Boating.eligibleToBoard(true, true, true, true, false, true, true), "downed");
        assertFalse(Boating.eligibleToBoard(true, true, true, false, true, true, true), "already a passenger");
        assertFalse(Boating.eligibleToBoard(true, true, true, false, false, false, true), "owner not in a boat");
        assertFalse(Boating.eligibleToBoard(true, true, true, false, false, true, false), "no spare seat");
    }

    @Test
    void chooseBoarderPicksTheNearest() {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        Map<UUID, Double> candidates = new LinkedHashMap<>();
        candidates.put(far, 40.0);
        candidates.put(near, 4.0);
        assertEquals(near, Boating.chooseBoarder(candidates), "the closest pet claims the seat");
    }

    @Test
    void chooseBoarderBreaksTiesByLowerUuid() {
        // Two exactly equidistant pets (a symmetric spawn): the lower UUID wins, deterministically.
        UUID a = new UUID(0L, 1L);
        UUID b = new UUID(0L, 2L);
        Map<UUID, Double> candidates = new LinkedHashMap<>();
        candidates.put(b, 9.0);
        candidates.put(a, 9.0);
        assertEquals(a, Boating.chooseBoarder(candidates), "an exact distance tie resolves to the lower UUID");
        // Insertion order must not change the verdict.
        Map<UUID, Double> reversed = new LinkedHashMap<>();
        reversed.put(a, 9.0);
        reversed.put(b, 9.0);
        assertEquals(a, Boating.chooseBoarder(reversed), "the pick is independent of map order");
    }

    @Test
    void chooseBoarderReturnsNullForNoCandidates() {
        assertNull(Boating.chooseBoarder(Map.of()), "no eligible pet means no boarder");
    }
}
