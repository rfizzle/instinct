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
 * The {@code instinct:pet_rank} criterion ({@code design/SPEC.md} §Advancements): fires with a
 * pet's derived veterancy rank for its online owner. {@code min_rank} matches with {@code >=} so
 * a multi-rank jump (threshold edit, {@code /instinct set veterancy}) grants every satisfied
 * milestone at once instead of skipping intermediates.
 */
public class PetRankTrigger extends SimpleCriterionTrigger<PetRankTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer owner, int rank) {
        this.trigger(owner, instance -> instance.matches(rank));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, int minRank)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("min_rank").forGetter(TriggerInstance::minRank)
        ).apply(instance, TriggerInstance::new));

        public static TriggerInstance forRank(int minRank) {
            return new TriggerInstance(Optional.empty(), minRank);
        }

        public boolean matches(int rank) {
            return rank >= minRank;
        }
    }
}
