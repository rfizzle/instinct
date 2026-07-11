package com.rfizzle.instinct.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.animal.Animal;

/**
 * Fired server-side after Instinct resolves a newborn's bloodline grade at breeding
 * ({@code design/SPEC.md} §3), before the child is added to the world. Carries both parents, the
 * child, and the child's final {@link Grade}. Fires for every covered breeding while
 * {@code enableGenetics} is on — natural pairs, trough-fed pairs, and pedigree-treat (born-prime)
 * pairs alike — and does not fire when genetics is disabled or the child is uncovered.
 */
@Stable
@FunctionalInterface
public interface InstinctAnimalBredCallback {

    Event<InstinctAnimalBredCallback> EVENT = EventFactory.createArrayBacked(InstinctAnimalBredCallback.class,
            listeners -> (parentA, parentB, child, grade) -> {
                for (InstinctAnimalBredCallback listener : listeners) {
                    listener.onAnimalBred(parentA, parentB, child, grade);
                }
            });

    void onAnimalBred(Animal parentA, Animal parentB, Animal child, Grade grade);
}
