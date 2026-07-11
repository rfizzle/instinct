package com.rfizzle.instinct.selfpreservation;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Pet self-preservation ({@code design/SPEC.md} §1): hazard-aware pathing, the creeper berth, and
 * teleport refusal for tamed pets-set animals. The first two attach here on entity load; teleport
 * refusal is a mixin gate on {@code TamableAnimal#shouldTryTeleportToOwner()} that calls
 * {@link #ownerUnsafeToJoin(LivingEntity)}.
 */
public final class SelfPreservation {

    private SelfPreservation() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof TamableAnimal pet)
                    || !InstinctConfig.get().enableSelfPreservation
                    || !AnimalCoverage.membershipOf(pet).pet()) {
                return;
            }
            try {
                attach(pet);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to attach self-preservation to {}", entity.getType(), e);
            }
        });
    }

    /**
     * Attaches the load-time protections. Idempotent: a chunk re-load never stacks a second berth
     * goal, and re-setting a malus to the same value is harmless.
     */
    private static void attach(TamableAnimal pet) {
        boolean alreadyInjected = pet.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof CreeperBerthGoal);
        if (!alreadyInjected) {
            // Priority 1 — above SitWhenOrderedToGoal (2 on wolf/cat/parrot) so a sitting pet
            // stands to step clear, and above FollowOwnerGoal (6 wolf/cat, 2 parrot). It ties
            // with TamableAnimalPanicGoal (1 on wolf/cat): same-priority goals never preempt
            // each other mid-run, which is acceptable — both flee. The goal is inert until the
            // pet is tamed, so an untamed load keeps vanilla behavior.
            pet.goalSelector.addGoal(1, new CreeperBerthGoal(pet));
        }
        if (pet.isTame()) {
            applyHazardMaluses(pet);
        }
    }

    /**
     * Hazard path nodes become unenterable: lava, fire damage, and cactus/berry-bush damage
     * ({@code DAMAGE_OTHER}) all cost -1. These match vanilla's own defaults for every vanilla
     * animal, so this is a pin for vanilla pets and a real change only for a modded pets-set
     * animal whose type overrode a hazard malus positive. Applied only while tamed — untamed
     * animals keep exactly the maluses their author gave them. Unlike the berth goal and the
     * teleport gate (which re-read tame state live), maluses land at load time: a pet tamed
     * mid-session picks them up at its next load — observable only for those malus-overriding
     * modded pets. Escape stays possible: maluses price nodes being entered, never the node the
     * pet already stands in.
     */
    private static void applyHazardMaluses(TamableAnimal pet) {
        pet.setPathfindingMalus(PathType.LAVA, -1.0F);
        pet.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        pet.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
    }

    /**
     * Teleport refusal (SPEC §1 protection 3): the owner is unsafe to teleport to while falling
     * more than {@code teleportSuppressFallDistance}, in lava, or gliding. Re-evaluated on every
     * teleport attempt, so teleporting resumes the tick the owner is safe.
     */
    public static boolean ownerUnsafeToJoin(LivingEntity owner) {
        return ownerUnsafeToJoin(owner.fallDistance, owner.isInLava(), owner.isFallFlying(),
                InstinctConfig.get().teleportSuppressFallDistance);
    }

    /** Pure core of {@link #ownerUnsafeToJoin(LivingEntity)}; fall distance suppresses strictly above the threshold. */
    static boolean ownerUnsafeToJoin(double fallDistance, boolean inLava, boolean gliding, double fallThreshold) {
        return fallDistance > fallThreshold || inLava || gliding;
    }
}
