package com.rfizzle.instinct.selfpreservation;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.coverage.OwnedAnimals;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Self-preservation ({@code design/SPEC.md} §1): hazard-aware pathing, the creeper berth, and
 * teleport refusal. It covers tamed pets-set animals and tamed mounts-set animals (the horse
 * family). The first two protections attach here on entity load; teleport refusal is a mixin gate
 * on {@code TamableAnimal#shouldTryTeleportToOwner()} that calls
 * {@link #ownerUnsafeToJoin(LivingEntity)} — and is inherently pet-only, since a mount never
 * follow-teleports.
 */
public final class SelfPreservation {

    private SelfPreservation() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            // Every covered pet (TamableAnimal) and mount (AbstractHorse) is an Animal, so gate on
            // Animal rather than the broader PathfinderMob — this keeps the membership resolve off
            // the hostile/villager load path entirely.
            if (!(entity instanceof Animal animal)
                    || !InstinctConfig.get().enableSelfPreservation) {
                return;
            }
            if (!AnimalCoverage.isPet(animal) && !AnimalCoverage.isMount(animal)) {
                return;
            }
            try {
                attach(animal);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to attach self-preservation to {}", entity.getType(), e);
            }
        });
    }

    /**
     * Attaches the load-time protections. Idempotent: a chunk re-load never stacks a second berth
     * goal, and re-setting a malus to the same value is harmless.
     *
     * <p>The creeper berth is a goal-selector {@link CreeperBerthGoal}, so it attaches only to
     * goal-driven animals; a brain-driven mount (the camel, whose movement runs on a {@code Brain}
     * via {@code CamelAi} and whose goal selector is empty) would fight the goal every tick, so it
     * gets hazard-aware pathing (the maluses below, which price path nodes regardless of AI
     * architecture) but no berth — graceful degradation, per Animal Coverage. Modded brain-driven
     * mounts degrade the same way.
     */
    private static void attach(Animal animal) {
        if (!animal.goalSelector.getAvailableGoals().isEmpty()) {
            boolean alreadyInjected = animal.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.getGoal() instanceof CreeperBerthGoal);
            if (!alreadyInjected) {
                // Priority 1 — above SitWhenOrderedToGoal (2 on wolf/cat/parrot) so a sitting pet
                // stands to step clear, and above FollowOwnerGoal (6 wolf/cat, 2 parrot). It ties
                // with TamableAnimalPanicGoal (1 on wolf/cat) and, on the horse family, with a
                // vanilla priority-1 goal (RunAroundLikeCrazyGoal on every AbstractHorse, plus
                // PanicGoal on horse/donkey/mule/trader llama): same-priority goals never preempt
                // each other mid-run, which is acceptable — both flee. The goal is inert until the
                // animal is tamed (and, for a mount, riderless), so an untamed load keeps vanilla
                // behavior.
                animal.goalSelector.addGoal(1, new CreeperBerthGoal(animal));
            }
        }
        if (OwnedAnimals.isTamed(animal)) {
            applyHazardMaluses(animal);
        }
    }

    /**
     * Hazard path nodes become unenterable: lava, fire damage, and cactus/berry-bush damage
     * ({@code DAMAGE_OTHER}) all cost -1. These match vanilla's own defaults for every vanilla
     * animal, so this is a pin for vanilla pets and a real change only for a modded pets-set
     * animal whose type overrode a hazard malus positive. Applied only while tamed — untamed
     * animals keep exactly the maluses their author gave them. Unlike the berth goal and the
     * teleport gate (which re-read tame state live), maluses land at load time: an animal tamed
     * mid-session picks them up at its next load — observable only for those malus-overriding
     * modded pets. Escape stays possible: maluses price nodes being entered, never the node the
     * animal already stands in. Harmless while a mount is ridden — path maluses only affect the
     * animal's own navigation, which is idle while a rider steers.
     */
    private static void applyHazardMaluses(Animal animal) {
        animal.setPathfindingMalus(PathType.LAVA, -1.0F);
        animal.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        animal.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
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
