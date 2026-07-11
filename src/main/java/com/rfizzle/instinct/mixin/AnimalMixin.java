package com.rfizzle.instinct.mixin;

import com.rfizzle.instinct.genetics.GeneticsHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Genetics at breeding ({@code design/SPEC.md} §3). At the head of
 * {@code finalizeSpawnChildFromBreeding} — after the child exists and is positioned, before vanilla
 * clears the parents' love state — the child's grade and perk resolve and its birth bonuses apply.
 * At the tail — after vanilla set both parents to a 6000-tick love cooldown — a fertile parent's
 * cooldown is scaled down. The handler gates on {@code enableGenetics} and livestock membership, so
 * a non-covered pair or disabled genetics leaves vanilla breeding untouched.
 */
@Mixin(Animal.class)
abstract class AnimalMixin {

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
    private void instinct$resolveGenetics(ServerLevel level, Animal partner, AgeableMob child,
                                          CallbackInfo ci) {
        if (child instanceof Animal childAnimal) {
            GeneticsHandler.onBred((Animal) (Object) this, partner, childAnimal);
        }
    }

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("TAIL"))
    private void instinct$scaleFertileCooldowns(ServerLevel level, Animal partner, AgeableMob child,
                                                CallbackInfo ci) {
        GeneticsHandler.scaleFertileCooldowns((Animal) (Object) this, partner);
    }
}
