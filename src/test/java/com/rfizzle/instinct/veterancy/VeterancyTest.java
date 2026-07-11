package com.rfizzle.instinct.veterancy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VeterancyTest {

    private static final List<Integer> DEFAULTS = List.of(10, 30, 60);

    @Test
    void rankBoundariesAreInclusive() {
        assertEquals(0, Veterancy.rankFor(0.0, DEFAULTS));
        assertEquals(0, Veterancy.rankFor(9.99, DEFAULTS));
        assertEquals(1, Veterancy.rankFor(10.0, DEFAULTS));
        assertEquals(1, Veterancy.rankFor(29.5, DEFAULTS));
        assertEquals(2, Veterancy.rankFor(30.0, DEFAULTS));
        assertEquals(3, Veterancy.rankFor(60.0, DEFAULTS));
        assertEquals(3, Veterancy.rankFor(10_000.0, DEFAULTS));
    }

    @Test
    void shorterThresholdListCapsTheRank() {
        assertEquals(2, Veterancy.rankFor(500.0, List.of(5, 15)), "two thresholds mean max rank 2");
    }

    @Test
    void rankKeysNameTheThreeRanks() {
        assertEquals("instinct.rank.seasoned", Veterancy.rankKey(1));
        assertEquals("instinct.rank.veteran", Veterancy.rankKey(2));
        assertEquals("instinct.rank.venerable", Veterancy.rankKey(3));
    }
}
