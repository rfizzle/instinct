package com.rfizzle.instinct.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * "Knows your swing" ({@code design/SPEC.md} §2, rank 2+): filters the sweep-attack victim list
 * in {@code Player#attack} so the attacker's own rank-2+ pets are skipped by the arc. Direct hits
 * are untouched — only the {@code getEntitiesOfClass} sweep collection is filtered. This sits on
 * the melee hot path, so the config gate runs before any per-victim work and the common no-pet
 * case allocates nothing. Server-authoritative in effect: on the client the veterancy attachment
 * never syncs, every victim reads rank 0, and the filter passes the list through unchanged.
 */
@Mixin(Player.class)
abstract class PlayerMixin {

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
}
