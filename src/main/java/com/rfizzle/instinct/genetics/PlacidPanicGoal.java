package com.rfizzle.instinct.genetics;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.config.InstinctConfig;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Animal;

/**
 * The placid perk's panic behavior ({@code design/SPEC.md} §3): a {@link PanicGoal} replacement that
 * stands down for a placid animal unless it is on fire or in lava, where flight overrides calm. For
 * a non-placid animal, or with genetics disabled, it behaves exactly like the vanilla goal it
 * replaced — both checks are re-read live, so a config toggle or a mid-life perk takes effect
 * without a reload. Installed only in place of an exact-class vanilla {@code PanicGoal}, preserving
 * its priority and speed; a species whose panic is a {@code PanicGoal} subclass (rabbit) or a brain
 * behavior (goat) keeps its vanilla panic untouched.
 */
public class PlacidPanicGoal extends PanicGoal {

    private final Animal animal;

    public PlacidPanicGoal(Animal animal, double speedModifier) {
        super(animal, speedModifier);
        this.animal = animal;
    }

    @Override
    public boolean canUse() {
        if (isCalm()) {
            return false;
        }
        return super.canUse();
    }

    /** Placid and not on fire or in lava: the animal holds the line instead of panicking. */
    private boolean isCalm() {
        if (animal.isOnFire() || animal.isInLava()) {
            return false;
        }
        return InstinctConfig.get().enableGenetics && InstinctAPI.getPerk(animal) == Perk.PLACID;
    }
}
