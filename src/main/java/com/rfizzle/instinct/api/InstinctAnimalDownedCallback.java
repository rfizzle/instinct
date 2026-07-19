package com.rfizzle.instinct.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Animal;

/**
 * Fired server-side the instant a tamed covered animal enters the downed state ({@code
 * design/SPEC.md} §7) — after its health is pinned to 1.0, its AI stopped, any rider ejected, and
 * its owner notified, but never for a death that was beyond saving (fire, lava, void,
 * {@code /kill}). Carries the animal and the lethal damage source that downed it.
 *
 * <p>Fires for every set the downed state covers — a pets-set pet and a mounts-set mount alike —
 * so consumers see one signal per down regardless of species. The parameter is {@link Animal}
 * rather than a narrower type because mounts-set membership resolves by entity-type id through
 * config and the {@code #instinct:mounts} tag, so a mount need not be an {@code AbstractHorse}.
 *
 * <p>Fires only when {@code enableDownedState} is on.
 */
@Stable
@FunctionalInterface
public interface InstinctAnimalDownedCallback {

    Event<InstinctAnimalDownedCallback> EVENT = EventFactory.createArrayBacked(InstinctAnimalDownedCallback.class,
            listeners -> (animal, source) -> {
                for (InstinctAnimalDownedCallback listener : listeners) {
                    listener.onAnimalDowned(animal, source);
                }
            });

    void onAnimalDowned(Animal animal, DamageSource source);
}
