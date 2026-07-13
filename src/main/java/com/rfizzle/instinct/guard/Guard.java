package com.rfizzle.instinct.guard;

import com.rfizzle.instinct.Instinct;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Guard stance ({@code design/SPEC.md} §6): a whistle order that posts the combat-capable pack at a
 * spot and holds it there — patrolling a small radius and engaging hostiles that enter it, returning
 * to post afterward. This class owns the entity-load wiring (add a {@link GuardGoal} to every tamable
 * animal) and the pure geometry the goal and its tests share; the order itself is issued from the
 * whistle ({@code WhistleActions.guardOrder}) and rides a persistent {@code GuardData} attachment.
 *
 * <p>Like predator watch, the goal is added to every tamable on load but stays inert — it does
 * nothing until the pet carries a guard order — so an unposted pet is exactly vanilla and the world
 * pays nothing for pets that were never stationed. Every gate ({@code enableWhistle}, the guard
 * radius, coverage) is re-read live, so a config change or a fresh tame takes effect without a reload.
 */
public final class Guard {

    private Guard() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof TamableAnimal pet)) {
                return;
            }
            try {
                addGuardGoal(pet);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to install guard goal on {}", entity.getType(), e);
            }
        });
    }

    /** Adds the guard goal to a pet once. Inert until a whistle posts the pet, so it is safe to add
     *  to every tamable animal on load. */
    private static void addGuardGoal(TamableAnimal pet) {
        boolean present = pet.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof GuardGoal);
        if (!present) {
            pet.goalSelector.addGoal(GuardGoal.PRIORITY, new GuardGoal(pet));
        }
    }

    /**
     * Whether a pet can actually fight a hostile — the gate for taking a guard post. A pet qualifies
     * only if it carries a vanilla {@link MeleeAttackGoal} (wolves, and modded fighters that reuse it),
     * because a guard order works by setting a target and letting that goal do the fighting. This is a
     * truer capability check than an attack-damage attribute alone: cats and parrots carry the
     * attribute in 1.21.1 yet have no melee goal to act on a target, so they are not posted.
     */
    public static boolean canFight(TamableAnimal pet) {
        return pet.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof MeleeAttackGoal);
    }

    // ── Pure geometry (unit-tested; no Minecraft types) ─────────────────────────────────────────

    /**
     * Whether a posted pet with no hostile to fight should path back to its anchor: true once it has
     * drifted more than {@code holdRadius} blocks from the post. Keeps the pet loosely on its ground
     * without twitching it back on every small step. Both distances are compared squared by the
     * caller-supplied values, so this is a plain threshold.
     */
    public static boolean shouldReturnToPost(double distanceToAnchorSq, double holdRadius) {
        return distanceToAnchorSq > holdRadius * holdRadius;
    }
}
