package com.rfizzle.instinct.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The engraving on a {@link KeepsakeCollarItem keepsake collar} ({@code design/SPEC.md} §7): a
 * frozen snapshot of the pet it belonged to, taken at the moment of a beyond-saving loss. The
 * name is the pet's own {@code getName()} at death; {@code rank} (0–3) and {@code daysSeen} are
 * its veterancy standing then, snapshotted so a later threshold-config edit never rewrites a
 * memento. Rides a specific stack as the {@code instinct:keepsake_engraving} data component.
 */
public record KeepsakeEngraving(Component petName, int rank, int daysSeen) {

    public static final Codec<KeepsakeEngraving> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.optionalFieldOf("pet_name", Component.empty())
                    .forGetter(KeepsakeEngraving::petName),
            Codec.INT.optionalFieldOf("rank", 0).forGetter(KeepsakeEngraving::rank),
            Codec.INT.optionalFieldOf("days_seen", 0).forGetter(KeepsakeEngraving::daysSeen)
    ).apply(instance, KeepsakeEngraving::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, KeepsakeEngraving> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, KeepsakeEngraving::petName,
            ByteBufCodecs.VAR_INT, KeepsakeEngraving::rank,
            ByteBufCodecs.VAR_INT, KeepsakeEngraving::daysSeen,
            KeepsakeEngraving::new);
}
