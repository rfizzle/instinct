package com.rfizzle.instinct.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The {@code instinct:pet_revived} criterion ({@code design/SPEC.md} §Advancements): fires for the
 * player who revives a downed pet — any player, not only the owner. Carries no condition beyond the
 * optional player predicate; a single revival grants Back from the Brink.
 */
public class PetRevivedTrigger extends SimpleCriterionTrigger<PetRevivedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer reviver) {
        this.trigger(reviver, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
        ).apply(instance, TriggerInstance::new));

        public static TriggerInstance instance() {
            return new TriggerInstance(Optional.empty());
        }
    }
}
