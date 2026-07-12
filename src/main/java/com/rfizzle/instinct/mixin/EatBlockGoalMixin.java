package com.rfizzle.instinct.mixin;

import com.rfizzle.instinct.genetics.GeneticsHandler;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The sheep wool-regrowth cadence bonus ({@code design/SPEC.md} §3 renewables): a graded or fertile
 * sheep seeks grass — and so regrows shorn wool — faster. Vanilla gates grass-eating on
 * {@code nextInt(this.mob.isBaby() ? 50 : 1000) == 0} in {@code canUse}; shrinking that modulus
 * raises the per-poll graze chance. {@code EatBlockGoal} is shared by every grass-eating mob, so the
 * handler gates to covered sheep specifically and floors the modulus at 1; a non-sheep, an
 * ordinary/non-fertile sheep, or a genetics-disabled world grazes exactly as vanilla.
 */
@Mixin(EatBlockGoal.class)
abstract class EatBlockGoalMixin {

    @Shadow
    @Final
    private Mob mob;

    @ModifyArg(method = "canUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"), index = 0)
    private int instinct$scaleGrazeChance(int bound) {
        return GeneticsHandler.scaledGrazeInterval(this.mob, bound);
    }
}
