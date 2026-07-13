package com.rfizzle.instinct.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * A pet's active guard order ({@code design/SPEC.md} §6), persisted as an entity attachment.
 * Presence of the attachment is the guarding flag; {@code anchor} is the post — the block the pet
 * holds and patrols around. A latent attachment (a pet carries none until a guard order writes one),
 * so readers gate on {@code getAttached(...) == null → not guarding}.
 */
public record GuardData(BlockPos anchor) {

    public static final Codec<GuardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("anchor", BlockPos.ZERO).forGetter(GuardData::anchor)
    ).apply(instance, GuardData::new));

    public GuardData() {
        this(BlockPos.ZERO);
    }
}
