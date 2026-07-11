package com.rfizzle.instinct.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Parrot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Bridge to the private static {@code Parrot#getImitatedSound}: a warning parrot imitates the
 * threat itself ({@code design/SPEC.md} §2 — "the parrot's threat imitation"), falling back to
 * vanilla's own parrot-ambient default for mobs the parrot cannot imitate.
 */
@Mixin(Parrot.class)
public interface ParrotSoundInvoker {

    @Invoker("getImitatedSound")
    static SoundEvent instinct$getImitatedSound(EntityType<?> threatType) {
        throw new AssertionError("mixin invoker not applied");
    }
}
