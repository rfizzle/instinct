package com.rfizzle.instinct.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves genetics across the mooshroom → cow shear conversion ({@code design/SPEC.md} §3 edge
 * case). Vanilla builds a fresh {@link Cow} and copies only a hand-picked field subset, so the
 * {@code genetics} attachment would be lost; this copies it onto the new cow just before it is
 * added to the world, where its load re-asserts the birth bonuses.
 */
@Mixin(MushroomCow.class)
abstract class MushroomCowMixin {

    @Inject(method = "shear", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private void instinct$copyGenetics(SoundSource soundSource, CallbackInfo ci, @Local Cow cow) {
        GeneticsHandler.copyGeneticsOnConversion((Animal) (Object) this, cow);
    }
}
