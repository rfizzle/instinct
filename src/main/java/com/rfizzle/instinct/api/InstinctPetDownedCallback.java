package com.rfizzle.instinct.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.TamableAnimal;

/**
 * Fired server-side the instant a tamed pets-set animal enters the downed state ({@code
 * design/SPEC.md} §7) — after its health is pinned to 1.0, its AI stopped, and its owner notified,
 * but never for a death that was beyond saving (fire, lava, void, {@code /kill}). Carries the pet
 * and the lethal damage source that downed it. Fires only when {@code enableDownedState} is on.
 */
@Stable
@FunctionalInterface
public interface InstinctPetDownedCallback {

    Event<InstinctPetDownedCallback> EVENT = EventFactory.createArrayBacked(InstinctPetDownedCallback.class,
            listeners -> (pet, source) -> {
                for (InstinctPetDownedCallback listener : listeners) {
                    listener.onPetDowned(pet, source);
                }
            });

    void onPetDowned(TamableAnimal pet, DamageSource source);
}
