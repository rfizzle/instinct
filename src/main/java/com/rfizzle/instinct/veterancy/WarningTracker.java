package com.rfizzle.instinct.veterancy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-owner warned-threat bookkeeping for the rank-1 warning behavior ({@code design/SPEC.md}
 * §2): one warning per threat per owner per {@link #WARNING_COOLDOWN_TICKS}, however many pets
 * qualify. Deliberately transient — a restart forgetting who was warned is harmless, so nothing
 * here persists; the owning handler clears it on server stop and drops an owner's entries on
 * disconnect. Server-thread only; entries self-prune as they expire. Minecraft-free so the dedupe
 * window unit-tests without a game.
 */
final class WarningTracker {

    /** How long a warned threat stays quiet for an owner, per SPEC §2. */
    static final int WARNING_COOLDOWN_TICKS = 300;

    /** owner UUID → (threat entity id → game time the warning expires). */
    private final Map<UUID, Map<Integer, Long>> warned = new HashMap<>();

    /** Whether this owner was already warned about this threat inside the cooldown window. */
    boolean warnedRecently(UUID owner, int threatId, long now) {
        Map<Integer, Long> threats = warned.get(owner);
        if (threats == null) {
            return false;
        }
        threats.values().removeIf(expiry -> expiry <= now);
        if (threats.isEmpty()) {
            warned.remove(owner);
            return false;
        }
        return threats.containsKey(threatId);
    }

    /** Records a warning fired now; the threat goes quiet for this owner for the cooldown. */
    void markWarned(UUID owner, int threatId, long now) {
        warned.computeIfAbsent(owner, o -> new HashMap<>()).put(threatId, now + WARNING_COOLDOWN_TICKS);
    }

    /** Drops one owner's bookkeeping (player disconnect). */
    void forgetOwner(UUID owner) {
        warned.remove(owner);
    }

    /** Drops everything (server stop). */
    void clear() {
        warned.clear();
    }
}
