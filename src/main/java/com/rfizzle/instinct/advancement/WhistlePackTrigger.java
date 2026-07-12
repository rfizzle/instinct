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
 * The {@code instinct:whistle_pack} criterion ({@code design/SPEC.md} §6, §Advancements): fires with
 * the number of pets a single whistle press commanded, for the whistling player. {@code min_pets}
 * matches with {@code >=}, so commanding more than the threshold in one press still grants Pack
 * Leader.
 */
public class WhistlePackTrigger extends SimpleCriterionTrigger<WhistlePackTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int commanded) {
        this.trigger(player, instance -> instance.matches(commanded));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int minPets)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("min_pets").forGetter(TriggerInstance::minPets)
        ).apply(instance, TriggerInstance::new));

        public static TriggerInstance forCount(int minPets) {
            return new TriggerInstance(Optional.empty(), minPets);
        }

        public boolean matches(int commanded) {
            return commanded >= minPets;
        }
    }
}
