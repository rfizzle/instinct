package com.rfizzle.instinct.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.Perk;
import net.minecraft.util.StringRepresentable;

/**
 * Per-livestock genetics state ({@code design/SPEC.md} §3), persisted as an entity attachment.
 * {@code grade} is the bloodline grade level 0–2; {@code primeNextOffspring} is the pedigree-treat
 * flag; {@code lastTroughFeedTime} is the world game time the animal last ate from a trough
 * (0 = never — §5 writes it, §3's well-fed test reads it).
 */
public record GeneticsData(int grade, Perk perk, boolean primeNextOffspring, long lastTroughFeedTime) {

    private static final Codec<Perk> PERK_CODEC = StringRepresentable.fromEnum(Perk::values);

    public static final Codec<GeneticsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("grade", 0).forGetter(GeneticsData::grade),
            // Lenient: an unknown perk name (a save touched by a newer Instinct) heals to NONE
            // instead of failing the whole attachment decode.
            PERK_CODEC.lenientOptionalFieldOf("perk", Perk.NONE).forGetter(GeneticsData::perk),
            Codec.BOOL.optionalFieldOf("primeNextOffspring", false).forGetter(GeneticsData::primeNextOffspring),
            Codec.LONG.optionalFieldOf("lastTroughFeedTime", 0L).forGetter(GeneticsData::lastTroughFeedTime)
    ).apply(instance, GeneticsData::new));

    /** A save file is untrusted input: the grade heals into 0–2, a null perk to {@code NONE}. */
    public GeneticsData {
        grade = Math.clamp(grade, Grade.ORDINARY.level(), Grade.PRIME.level());
        if (perk == null) {
            perk = Perk.NONE;
        }
    }

    public GeneticsData() {
        this(0, Perk.NONE, false, 0L);
    }
}
