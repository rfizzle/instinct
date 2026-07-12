package com.rfizzle.instinct.trough;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The feeding trough's shared core ({@code design/SPEC.md} §5). This class owns the pure decision
 * logic the block, its block entity, and their tests all share ({@link #canInsert},
 * {@link #hayWheatYield}, {@link #comparatorLevel}, {@link #capAllows}), plus the two transient
 * server-side registers the feeding loop rides on: the <em>claim map</em> (which animal each trough
 * has claimed to path in, so overlapping troughs never fight over the same animal) and the
 * per-baby feed cooldown.
 *
 * <p>Both maps are confined to the server thread: every writer is a block-entity server tick or a
 * lifecycle event. Claims carry a 400-tick (20-second) expiry so an animal that can't reach a
 * trough never holds its slot forever, and both maps clear on {@code SERVER_STOPPED} so nothing
 * leaks across worlds. Neither is persisted — a reload simply re-scans (SPEC "Unloaded chunks").
 */
public final class Trough {

    /** Internal capacity of a trough — one stack of a single accepted food type (§5). */
    public static final int CAPACITY = 64;
    /** A hay bale converts to this many wheat on insert (§5). */
    public static final int HAY_WHEAT_YIELD = 9;
    /** A claim expires this many ticks after it is made — the 20-second pathing timeout (§5). */
    public static final int CLAIM_EXPIRY_TICKS = 400;
    /** How often a claimed animal's navigation is re-issued while it walks in, decoupled from the
     *  feed interval so the animal's own wander/panic goals can't leave it stranded. */
    public static final int REPATH_INTERVAL_TICKS = 10;
    /** A baby may eat from a trough at most once per this many ticks (§5). */
    public static final int BABY_FEED_COOLDOWN_TICKS = 600;
    /** Arrival radius: an animal within this many blocks of the trough centre eats (§5). */
    public static final double ARRIVAL_DISTANCE = 1.5;
    /** Navigation speed for an animal pathing to a trough (vanilla follow/tempt range). */
    public static final double MOVE_SPEED = 1.1;

    /** animal UUID → its live claim. Server-thread confined; see the class javadoc. */
    private static final Map<UUID, Claim> CLAIMS = new ConcurrentHashMap<>();
    /** baby UUID → the game time it last ate. Server-thread confined; cleared on stop. */
    private static final Map<UUID, Long> BABY_LAST_FED = new ConcurrentHashMap<>();

    private record Claim(BlockPos trough, long expiryTick) {
    }

    private Trough() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CLAIMS.clear();
            BABY_LAST_FED.clear();
        });
    }

    // ── Pure decision logic (unit-tested; no Minecraft types) ───────────────────────────────────

    /**
     * Whether an incoming stack may enter the trough: it must be an accepted food, the trough must
     * be empty or already holding that same item, and there must be room. One predicate for both
     * the player right-click insert and the hopper's {@code canPlaceItem} gate.
     */
    public static boolean canInsert(boolean isFood, boolean storedEmpty, boolean sameType, int room) {
        return isFood && (storedEmpty || sameType) && room > 0;
    }

    /**
     * The wheat a hay bale yields on insert: {@link #HAY_WHEAT_YIELD} only when the trough is empty
     * or already holding wheat and at least that much room remains, else 0 (refused).
     */
    public static int hayWheatYield(boolean storedWheatOrEmpty, int room) {
        return storedWheatOrEmpty && room >= HAY_WHEAT_YIELD ? HAY_WHEAT_YIELD : 0;
    }

    /**
     * The comparator signal 0–15 for a fill of {@code count} against {@code maxStack}, matching
     * vanilla's proportional {@code getRedstoneSignalFromContainer} math for a single slot.
     */
    public static int comparatorLevel(int count, int maxStack) {
        if (count <= 0 || maxStack <= 0) {
            return 0;
        }
        return Math.min(15, (int) Math.floor((double) count / maxStack * 14.0) + 1);
    }

    /** Whether the population cap admits a new breeding: uncapped at 0, else in-radius count ≤ cap. */
    public static boolean capAllows(int coveredCount, int cap) {
        return cap <= 0 || coveredCount <= cap;
    }

    /** Whether a squared distance is within a spherical radius. */
    public static boolean withinRadiusSq(double distSq, double radius) {
        return distSq <= radius * radius;
    }

    // ── Claim map ───────────────────────────────────────────────────────────────────────────────

    /** Whether the animal holds a live (unexpired) claim to any trough. */
    public static boolean isClaimed(UUID animal, long now) {
        Claim claim = CLAIMS.get(animal);
        return claim != null && claim.expiryTick() > now;
    }

    /** Claims the animal for the trough at {@code pos} if it is currently unclaimed; true on success. */
    public static boolean tryClaim(UUID animal, BlockPos pos, long now) {
        // Prune expired claims here (a trough that unloaded mid-walk never releases its own claim).
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().expiryTick() <= now);
        if (isClaimed(animal, now)) {
            return false;
        }
        CLAIMS.put(animal, new Claim(pos.immutable(), now + CLAIM_EXPIRY_TICKS));
        return true;
    }

    /** The trough this animal is live-claimed to, or {@code null} if its claim is absent or expired. */
    public static BlockPos claimedTrough(UUID animal, long now) {
        Claim claim = CLAIMS.get(animal);
        if (claim == null || claim.expiryTick() <= now) {
            return null;
        }
        return claim.trough();
    }

    /** Drops the animal's claim (arrival, timeout, or the trough giving up). */
    public static void release(UUID animal) {
        CLAIMS.remove(animal);
    }

    // ── Baby feed cooldown ──────────────────────────────────────────────────────────────────────

    /** Whether a baby may eat now — never fed, or its last feed was at least the cooldown ago. */
    public static boolean canBabyEat(UUID baby, long now) {
        Long last = BABY_LAST_FED.get(baby);
        return last == null || now - last >= BABY_FEED_COOLDOWN_TICKS;
    }

    /** Records that a baby just ate, starting its cooldown. */
    public static void markBabyFed(UUID baby, long now) {
        // An entry older than the cooldown is indistinguishable from absence, so evict it — this
        // bounds the map to babies fed within the last cooldown window rather than growing forever.
        BABY_LAST_FED.entrySet().removeIf(entry -> now - entry.getValue() >= BABY_FEED_COOLDOWN_TICKS);
        BABY_LAST_FED.put(baby, now);
    }

    /** Test hook: drops all transient state, mirroring the {@code SERVER_STOPPED} clear. */
    static void clearForTest() {
        CLAIMS.clear();
        BABY_LAST_FED.clear();
    }
}
