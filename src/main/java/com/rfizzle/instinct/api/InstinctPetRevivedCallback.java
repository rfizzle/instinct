package com.rfizzle.instinct.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fired server-side the instant a downed pet is revived ({@code design/SPEC.md} §7) — after its
 * health, regeneration, and post-revive invulnerability are applied and any rank penalty taken.
 * Carries the pet, the player who revived it (any player, not only the owner), and the item used
 * from {@code #instinct:revive_items}.
 */
@Stable
@FunctionalInterface
public interface InstinctPetRevivedCallback {

    Event<InstinctPetRevivedCallback> EVENT = EventFactory.createArrayBacked(InstinctPetRevivedCallback.class,
            listeners -> (pet, reviver, item) -> {
                for (InstinctPetRevivedCallback listener : listeners) {
                    listener.onPetRevived(pet, reviver, item);
                }
            });

    void onPetRevived(TamableAnimal pet, Player reviver, ItemStack item);
}
