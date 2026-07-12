package com.rfizzle.instinct.mixin;

import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Predicate;

/**
 * Reads a {@link TemptGoal}'s protected/private construction parameters so the flocking swap (§4)
 * can rebuild an exact-class vanilla tempt goal as a {@code FlockingTemptGoal} at the same flee
 * speed, same tempt items, and same scare behavior — preserving each goal's semantics (an animal
 * with two exact-class tempt goals, e.g. a pig's food and carrot-on-a-stick goals, keeps both
 * predicates verbatim).
 */
@Mixin(TemptGoal.class)
public interface TemptGoalAccessor {

    @Accessor("speedModifier")
    double instinct$getSpeedModifier();

    @Accessor("items")
    Predicate<ItemStack> instinct$getItems();

    @Accessor("canScare")
    boolean instinct$getCanScare();
}
