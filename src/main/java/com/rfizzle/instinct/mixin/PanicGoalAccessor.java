package com.rfizzle.instinct.mixin;

import net.minecraft.world.entity.ai.goal.PanicGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a {@link PanicGoal}'s protected {@code speedModifier} so the placid swap can construct its
 * replacement at the same flee speed as the vanilla goal it displaces (§3).
 */
@Mixin(PanicGoal.class)
public interface PanicGoalAccessor {

    @Accessor("speedModifier")
    double instinct$getSpeedModifier();
}
