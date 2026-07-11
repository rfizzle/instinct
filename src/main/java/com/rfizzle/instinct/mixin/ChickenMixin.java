package com.rfizzle.instinct.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The chicken egg-interval bonus ({@code design/SPEC.md} §3 renewables): a graded chicken lays 10%
 * faster at sturdy and 20% faster at prime. Vanilla rerolls the interval as
 * {@code nextInt(6000) + 6000}; this shifts the random draw so the whole interval scales by grade.
 * The only {@code nextInt(int)} call in {@code aiStep} is the egg reroll; the handler returns the
 * draw unchanged for an ordinary, uncovered, or genetics-disabled chicken.
 */
@Mixin(Chicken.class)
abstract class ChickenMixin {

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int instinct$scaleEggInterval(int original) {
        return GeneticsHandler.scaledEggRandom((Animal) (Object) this, original);
    }
}
