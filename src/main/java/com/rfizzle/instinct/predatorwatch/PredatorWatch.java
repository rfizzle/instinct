package com.rfizzle.instinct.predatorwatch;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

/**
 * Predator Watch ({@code design/SPEC.md} §8): a tamed pet on Stay near livestock turns wild
 * predators from the pasture. This class owns the entity-load wiring (add a
 * {@link PredatorWatchGoal} to every tamed pets-set animal), the predator-set resolution shared
 * with that goal, and the pure geometry the goal and its tests share.
 *
 * <p>Both halves of the feature — deterrence (clearing a predator's livestock target and nudging it
 * away) and interception (the pet moving to block the bold one) — are driven entirely from the
 * guardian pet's goal, so <em>no</em> goal is ever injected onto a wild fox or wolf: an untamed
 * predator with no guardian nearby is exactly vanilla, and the world pays nothing for predators that
 * never meet a stationed pet. Every gate — {@code enablePredatorWatch}, the watch radius, coverage
 * and predator-set edits — is re-read live, so a config change or a fresh tame takes effect without
 * a reload.
 */
public final class PredatorWatch {

    private PredatorWatch() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            try {
                if (entity instanceof TamableAnimal pet) {
                    addWatchGoal(pet);
                }
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to install predator watch on {}", entity.getType(), e);
            }
        });
    }

    /** Adds the watch goal to a pet once. Inert until the pet is tamed, on Stay, and a predator
     *  presses a nearby pasture, so it is safe to add to every tamable animal on load. */
    private static void addWatchGoal(TamableAnimal pet) {
        boolean present = pet.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof PredatorWatchGoal);
        if (!present) {
            pet.goalSelector.addGoal(PredatorWatchGoal.PRIORITY, new PredatorWatchGoal(pet));
        }
    }

    // ── Predator-set resolution ─────────────────────────────────────────────────────────────────

    /**
     * Whether the animal is a wild predator a guardian watches for: its type is in the predator set
     * ({@code #instinct:predators} or {@code predatorsInclude}, minus {@code predatorsExclude}) and
     * it is not a tamed animal. A tamed wolf is someone's pet, never prey's predator, so it never
     * qualifies no matter what the set says.
     */
    public static boolean isPredator(Animal animal) {
        if (animal instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        return isPredatorType(animal.getType());
    }

    /** Type-level predator-set membership: config include or the tag, minus the config exclude. */
    static boolean isPredatorType(EntityType<?> type) {
        InstinctConfig config = InstinctConfig.get();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        if (config.predatorsExcludeSet.contains(id)) {
            return false;
        }
        return config.predatorsIncludeSet.contains(id) || type.is(AnimalCoverage.PREDATORS_TAG);
    }

    // ── Pure geometry (unit-tested; no Minecraft types) ─────────────────────────────────────────

    /**
     * The interception point {@code standoff} blocks from the predator toward the prey, in the X/Z
     * plane — where the guardian plants itself to put its body between the predator and the animal
     * it is stalking. Returns {@code {x, z}}. When predator and prey coincide the axis is undefined
     * and the predator's own position is returned.
     */
    public static double[] interceptPoint(double predatorX, double predatorZ,
                                          double preyX, double preyZ, double standoff) {
        double dx = preyX - predatorX;
        double dz = preyZ - predatorZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            return new double[]{predatorX, predatorZ};
        }
        return new double[]{predatorX + (dx / len) * standoff, predatorZ + (dz / len) * standoff};
    }

    /**
     * A point {@code distance} blocks directly away from the pasture anchor on the anchor→predator
     * axis — where the guardian drives the predator so it paths away from the watched pasture.
     * Returns {@code {x, z}}. When the predator sits exactly on the anchor the axis is undefined and
     * the predator's own position is returned (the guardian's approach breaks the tie next scan).
     */
    public static double[] fleePoint(double predatorX, double predatorZ,
                                     double anchorX, double anchorZ, double distance) {
        double dx = predatorX - anchorX;
        double dz = predatorZ - anchorZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            return new double[]{predatorX, predatorZ};
        }
        return new double[]{predatorX + (dx / len) * distance, predatorZ + (dz / len) * distance};
    }
}
