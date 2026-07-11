package com.rfizzle.instinct.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * The {@code instinct:bred_grade} criterion ({@code design/SPEC.md} §Advancements): fires with the
 * grade of an animal the player bred, for the breeding's love-cause player. {@code min_grade}
 * matches with {@code >=} so a threshold family (were one to exist) grants every satisfied
 * milestone at once; Best in Show uses {@code min_grade = 2} (prime).
 */
public class BredGradeTrigger extends SimpleCriterionTrigger<BredGradeTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer breeder, int grade) {
        this.trigger(breeder, instance -> instance.matches(grade));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int minGrade)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("min_grade").forGetter(TriggerInstance::minGrade)
        ).apply(instance, TriggerInstance::new));

        public static TriggerInstance forGrade(int minGrade) {
            return new TriggerInstance(Optional.empty(), minGrade);
        }

        public boolean matches(int grade) {
            return grade >= minGrade;
        }
    }
}
