package com.rfizzle.instinct.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.shoulders.SteadyShoulders;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-side gameplay hooks for two features:
 *
 * <ul>
 *   <li>"Knows your swing" ({@code design/SPEC.md} §2, rank 2+): filters the sweep-attack victim
 *   list in {@code Player#attack} so the attacker's own rank-2+ pets are skipped by the arc. Direct
 *   hits are untouched — only the {@code getEntitiesOfClass} sweep collection is filtered. This sits
 *   on the melee hot path, so the config gate runs before any per-victim work and the common no-pet
 *   case allocates nothing.</li>
 *   <li>Steady shoulders ({@code design/SPEC.md} §1): a perched pets-set animal rides through the
 *   incidental knocks that dislodge a vanilla parrot. The two vanilla dismount call sites that
 *   matter — {@code aiStep()} (fall) and {@code hurt()} (damage) — are conditionally skipped through
 *   {@link SteadyShoulders}, and a sneak drops the bird deliberately from {@code tick()}. The
 *   riptide spin-attack dismount is left alone.</li>
 * </ul>
 *
 * Server-authoritative in effect: the veterancy attachment never syncs to the client (every victim
 * reads rank 0, so the sweep filter passes through unchanged), and the two suppressed shoulder call
 * sites run server-side.
 */
@Mixin(Player.class)
abstract class PlayerMixin {

    @Shadow
    protected abstract void removeEntitiesOnShoulder();

    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<LivingEntity> instinct$duckOwnersSweep(List<LivingEntity> victims) {
        InstinctConfig config = InstinctConfig.get();
        if (victims.isEmpty() || !config.enableVeterancy || !config.enableRankBehaviors) {
            return victims;
        }
        Player self = (Player) (Object) this;
        List<LivingEntity> kept = null;
        for (int i = 0; i < victims.size(); i++) {
            LivingEntity victim = victims.get(i);
            if (VeterancyHandler.ducksSweep(victim, self)) {
                if (kept == null) {
                    kept = new ArrayList<>(victims.subList(0, i));
                }
            } else if (kept != null) {
                kept.add(victim);
            }
        }
        return kept != null ? kept : victims;
    }

    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;removeEntitiesOnShoulder()V"))
    private boolean instinct$keepPerchedThroughFall(Player self) {
        return !SteadyShoulders.keepsThroughFall(self);
    }

    @WrapWithCondition(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;removeEntitiesOnShoulder()V"))
    private boolean instinct$keepPerchedThroughHit(Player self, @Local(argsOnly = true) float amount) {
        return !SteadyShoulders.keepsThroughHit(self, amount);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void instinct$dropPerchedOnSneak(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide && SteadyShoulders.dropsOnSneak(self)) {
            removeEntitiesOnShoulder();
        }
    }
}
