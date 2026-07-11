package com.rfizzle.instinct.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Virtual-dispatch bridge to the protected {@code LivingEntity#getHurtSound}: the warning
 * behavior ({@code design/SPEC.md} §2) speaks in each species' own voice, and for species without
 * a curated call that voice is its own hurt sound — resolved through the subclass override, which
 * may be {@code null} for a silent species.
 */
@Mixin(LivingEntity.class)
public interface LivingEntitySoundInvoker {

    @Nullable
    @Invoker("getHurtSound")
    SoundEvent instinct$getHurtSound(DamageSource source);
}
