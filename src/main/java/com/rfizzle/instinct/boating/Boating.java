package com.rfizzle.instinct.boating;

import com.rfizzle.instinct.Instinct;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.TamableAnimal;

import java.util.Map;
import java.util.UUID;

/**
 * Pets riding boats ({@code design/SPEC.md} §4, "Water crossings"). A following pet boards its
 * owner's boat when a seat is free and hops back out when the owner lands, so a river crossing no
 * longer strands the dog on the shore. This class owns the entity-load wiring — a {@link
 * BoardBoatGoal} added to every tamed pet, inert until its owner is actually boating — and the pure
 * seat-priority logic the goal and its unit tests share.
 *
 * <p>A vanilla boat seats two, and the owner fills one, so there is exactly one spare seat and thus
 * at most one boarding pet per boat. The goal boards with an <em>unforced</em> {@code startRiding},
 * so the vanilla seat cap arbitrates the race and a boat can never be overfilled; the pure {@link
 * #chooseBoarder} pick only decides which single pet <em>walks up</em> to claim that seat, keeping
 * the rest from stampeding the boat. There is no server-side state to track — the seat itself is the
 * claim — so, unlike herding, this feature clears nothing on stop.
 */
public final class Boating {

    /** Priority of the boarding goal. Below the creeper berth (1) and sit (2) so self-preservation
     *  and a stay order still win, and above {@code HerdWorkGoal} (5) and {@code FollowOwnerGoal} (6)
     *  so a pet breaks off idle following/driving to take the seat. It ties {@code LeapAtTargetGoal}
     *  (4 on wolf) and outranks {@code MeleeAttackGoal} (5), which share its {@code MOVE} flag: the
     *  engine can't let an equal/lower-priority goal preempt it, so combat winning is enforced not by
     *  priority but by {@code BoardBoatGoal} standing down the moment {@code getTarget() != null} —
     *  the same resolution {@code HerdWorkGoal} uses. Inert unless the owner is boating, so unrelated
     *  pets are untouched. */
    static final int BOARD_GOAL_PRIORITY = 4;

    private Boating() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof TamableAnimal pet) {
                try {
                    addBoardGoal(pet);
                } catch (Exception e) {
                    Instinct.LOGGER.error("Failed to install boat-boarding on {}", entity.getType(), e);
                }
            }
        });
    }

    /** Adds the boarding goal to a pet once. Inert until the pet's owner is riding a boat with a free
     *  seat, so it is safe to add to every tamed animal regardless of live config. */
    private static void addBoardGoal(TamableAnimal pet) {
        boolean present = pet.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof BoardBoatGoal);
        if (!present) {
            pet.goalSelector.addGoal(BOARD_GOAL_PRIORITY, new BoardBoatGoal(pet));
        }
    }

    // ── Pure logic (unit-tested; no Minecraft types) ────────────────────────────────────────────

    /**
     * Whether a pet may take a boat seat this scan: the feature is on, the pet is a tamed, following
     * (not sitting) pet that is neither downed nor already a passenger, and its owner is riding a boat
     * that still has a spare seat. The {@link BoardBoatGoal} shell resolves each condition from game
     * state and passes plain booleans here.
     */
    public static boolean eligibleToBoard(boolean enabled, boolean tame, boolean following,
                                          boolean downed, boolean passenger,
                                          boolean ownerInBoat, boolean seatFree) {
        return enabled && tame && following && !downed && !passenger && ownerInBoat && seatFree;
    }

    /**
     * The pet that should walk up to the single spare seat: the nearest candidate by squared distance
     * to the boat, ties broken by the lower UUID so the pick is deterministic across ticks and clients.
     * Returns {@code null} for an empty map (no eligible pet in range).
     */
    public static UUID chooseBoarder(Map<UUID, Double> distanceSqByPet) {
        UUID best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Map.Entry<UUID, Double> entry : distanceSqByPet.entrySet()) {
            double distSq = entry.getValue();
            UUID id = entry.getKey();
            if (distSq < bestDistSq || (distSq == bestDistSq && (best == null || id.compareTo(best) < 0))) {
                bestDistSq = distSq;
                best = id;
            }
        }
        return best;
    }
}
