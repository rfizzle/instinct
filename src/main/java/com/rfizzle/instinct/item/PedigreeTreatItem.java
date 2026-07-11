package com.rfizzle.instinct.item;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The pedigree treat ({@code design/SPEC.md} §3): fed to an adult covered animal, it flags that
 * animal's next offspring to be born prime, consuming one treat with the vanilla eat sound and
 * {@code happy_villager} particles. Feeding a second treat to an already-flagged animal is refused
 * with a hand swing but no consume and no message; a non-covered animal, a baby, or genetics being
 * disabled passes through so the item does nothing.
 */
public class PedigreeTreatItem extends Item {

    public PedigreeTreatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!InstinctConfig.get().enableGenetics
                || !(target instanceof Animal animal)
                || animal.isBaby()
                || !AnimalCoverage.membershipOf(animal).livestock()) {
            return InteractionResult.PASS;
        }
        Level level = animal.level();
        if (level.isClientSide) {
            // Swing the arm for either outcome; the server decides whether it consumes.
            return InteractionResult.sidedSuccess(true);
        }
        GeneticsData data = animal.getAttachedOrCreate(InstinctAttachments.GENETICS);
        if (data.primeNextOffspring()) {
            // Already flagged: refused with a swing, no consume, no message (SPEC §3).
            return InteractionResult.sidedSuccess(false);
        }
        animal.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(data.grade(), data.perk(), true, data.lastTroughFeedTime()));
        level.playSound(null, animal, SoundEvents.GENERIC_EAT, animal.getSoundSource(),
                1.0F, 1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.4F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    animal.getX(), animal.getY() + animal.getBbHeight() * 0.5, animal.getZ(),
                    7, animal.getBbWidth() * 0.5, animal.getBbHeight() * 0.4, animal.getBbWidth() * 0.5, 0.02);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(false);
    }
}
