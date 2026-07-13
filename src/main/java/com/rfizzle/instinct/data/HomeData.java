package com.rfizzle.instinct.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A pet's assigned home — the kennel post it was told to call home ({@code design/SPEC.md} §9),
 * persisted as an entity attachment. Presence of the attachment is the "homed" flag; {@code post} is
 * the block the whistle sent it to and {@code dimension} the level that post stands in, so a Stay
 * order can send the pet home only when it shares the post's dimension (there is no cross-dimension
 * pathing). A latent attachment — a pet carries none until the whistle assigns one — so readers gate
 * on {@code getAttached(...) == null → not homed}.
 */
public record HomeData(BlockPos post, ResourceKey<Level> dimension) {

    public static final Codec<HomeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("post", BlockPos.ZERO).forGetter(HomeData::post),
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension", Level.OVERWORLD)
                    .forGetter(HomeData::dimension)
    ).apply(instance, HomeData::new));

    public HomeData() {
        this(BlockPos.ZERO, Level.OVERWORLD);
    }
}
