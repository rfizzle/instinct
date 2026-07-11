package com.rfizzle.instinct.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The sheep shear bonus ({@code design/SPEC.md} §3 renewables): a graded sheep drops +1 wool at
 * sturdy and +2 at prime on top of the vanilla 1–3. The vanilla count is {@code 1 + nextInt(3)};
 * this adds the grade bonus to that random draw. The handler returns 0 for an ordinary, uncovered,
 * or genetics-disabled sheep, so vanilla shearing is unchanged.
 */
@Mixin(Sheep.class)
abstract class SheepMixin {

    @ModifyExpressionValue(method = "shear", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int instinct$shearWoolBonus(int original) {
        return original + GeneticsHandler.shearWoolBonus((Animal) (Object) this);
    }
}
