package com.rfizzle.instinct.veterancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarningTrackerTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final int THREAT = 42;

    @Test
    void oneWarningPerThreatPerOwnerInsideTheWindow() {
        WarningTracker tracker = new WarningTracker();
        assertFalse(tracker.warnedRecently(OWNER, THREAT, 1000L), "fresh threat is warnable");
        tracker.markWarned(OWNER, THREAT, 1000L);
        assertTrue(tracker.warnedRecently(OWNER, THREAT, 1000L), "same tick dedupes");
        assertTrue(tracker.warnedRecently(OWNER, THREAT, 1000L + WarningTracker.WARNING_COOLDOWN_TICKS - 1),
                "still quiet on the window's last tick");
    }

    @Test
    void warningExpiresAfterTheCooldown() {
        WarningTracker tracker = new WarningTracker();
        tracker.markWarned(OWNER, THREAT, 1000L);
        assertFalse(tracker.warnedRecently(OWNER, THREAT, 1000L + WarningTracker.WARNING_COOLDOWN_TICKS),
                "the threat is warnable again once the window closes");
    }

    @Test
    void dedupeIsPerOwnerAndPerThreat() {
        WarningTracker tracker = new WarningTracker();
        tracker.markWarned(OWNER, THREAT, 1000L);
        assertFalse(tracker.warnedRecently(OTHER_OWNER, THREAT, 1000L),
                "another owner's pets still warn about the same threat");
        assertFalse(tracker.warnedRecently(OWNER, THREAT + 1, 1000L),
                "a different threat still gets its warning");
    }

    @Test
    void forgetOwnerAndClearDropBookkeeping() {
        WarningTracker tracker = new WarningTracker();
        tracker.markWarned(OWNER, THREAT, 1000L);
        tracker.forgetOwner(OWNER);
        assertFalse(tracker.warnedRecently(OWNER, THREAT, 1000L), "disconnect drops the owner");

        tracker.markWarned(OWNER, THREAT, 1000L);
        tracker.clear();
        assertFalse(tracker.warnedRecently(OWNER, THREAT, 1000L), "server stop drops everything");
    }
}
