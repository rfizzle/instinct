package com.rfizzle.instinct.mixin;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.selfpreservation.SelfPreservation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teleport refusal ({@code design/SPEC.md} §1 protection 3). {@code shouldTryTeleportToOwner()}
 * is the single vanilla decision point both {@code FollowOwnerGoal} and
 * {@code TamableAnimalPanicGoal} consult before teleporting to the owner, so gating it here
 * covers every vanilla teleport-to-owner path with one injection while leaving custom follow
 * goals (which never call it) untouched. Vanilla's {@code false} verdicts pass through; only a
 * would-be teleport to an unsafe owner is refused, and the predicate re-evaluates per call so
 * teleporting resumes the tick the owner is safe.
 */
@Mixin(TamableAnimal.class)
abstract class TamableAnimalMixin {

    @Inject(method = "shouldTryTeleportToOwner", at = @At("RETURN"), cancellable = true)
    private void instinct$refuseTeleportToUnsafeOwner(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !InstinctConfig.get().enableSelfPreservation) {
            return;
        }
        TamableAnimal self = (TamableAnimal) (Object) this;
        LivingEntity owner = self.getOwner();
        if (owner == null || !SelfPreservation.ownerUnsafeToJoin(owner)) {
            return;
        }
        // Membership is checked last: it is the costliest test and the unsafe-owner case is rare.
        if (AnimalCoverage.isPet(self.getType())) {
            cir.setReturnValue(false);
        }
    }
}
