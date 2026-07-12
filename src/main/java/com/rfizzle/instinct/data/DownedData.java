package com.rfizzle.instinct.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per pet/mount downed state ({@code design/SPEC.md} §7), persisted as an entity attachment.
 * Presence of the attachment is the downed flag; {@code downedAtGameTime} records when the animal
 * went down.
 */
public record DownedData(long downedAtGameTime) {

    public static final Codec<DownedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("downedAtGameTime", 0L).forGetter(DownedData::downedAtGameTime)
    ).apply(instance, DownedData::new));

    public DownedData() {
        this(0L);
    }
}
