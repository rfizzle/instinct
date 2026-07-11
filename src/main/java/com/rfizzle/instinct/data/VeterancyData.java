package com.rfizzle.instinct.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-pet veterancy state ({@code design/SPEC.md} §2), persisted as an entity attachment.
 * {@code accruedDays} is in-game days survived since taming; {@code lastAccrualGameTime} is the
 * world game time of the last accrual pass (0 = never accrued).
 */
public record VeterancyData(double accruedDays, long lastAccrualGameTime) {

    public static final Codec<VeterancyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("accruedDays", 0.0).forGetter(VeterancyData::accruedDays),
            Codec.LONG.optionalFieldOf("lastAccrualGameTime", 0L).forGetter(VeterancyData::lastAccrualGameTime)
    ).apply(instance, VeterancyData::new));

    /** A save file is untrusted input: negative or non-finite days heal to 0. */
    public VeterancyData {
        if (!Double.isFinite(accruedDays) || accruedDays < 0.0) {
            accruedDays = 0.0;
        }
    }

    public VeterancyData() {
        this(0.0, 0L);
    }
}
