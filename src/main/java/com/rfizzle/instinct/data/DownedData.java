package com.rfizzle.instinct.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per pet/mount downed state ({@code design/SPEC.md} §7), persisted as an entity attachment.
 * Presence of the attachment is the downed flag; {@code downedAtGameTime} records when the animal
 * went down, and {@code recoveryTicks} accumulates the time a downed pet has spent beside a kennel
 * post (§9) — it reaches the recovery threshold and the pet gets back up on its own. A save written
 * before {@code recoveryTicks} existed loads it as 0 (no progress).
 */
public record DownedData(long downedAtGameTime, int recoveryTicks) {

    /** Recovery never needs more than a day of adjacent ticks; clamping a tampered or corrupt save here
     *  also keeps the per-beat increment (§9) from overflowing into a negative that would stall a pet. */
    private static final int MAX_RECOVERY_TICKS = 20 * 60 * 60 * 24;

    public static final Codec<DownedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("downedAtGameTime", 0L).forGetter(DownedData::downedAtGameTime),
            Codec.INT.optionalFieldOf("recoveryTicks", 0).forGetter(DownedData::recoveryTicks)
    ).apply(instance, DownedData::new));

    public DownedData {
        // A save file is untrusted input: clamp recovery progress to a sane, overflow-safe range.
        recoveryTicks = Math.clamp(recoveryTicks, 0, MAX_RECOVERY_TICKS);
    }

    public DownedData() {
        this(0L, 0);
    }

    /** The go-down constructor: a fresh downed animal has no recovery progress yet. */
    public DownedData(long downedAtGameTime) {
        this(downedAtGameTime, 0);
    }

    /** This record with recovery progress advanced to {@code ticks}. */
    public DownedData withRecoveryTicks(int ticks) {
        return new DownedData(downedAtGameTime, ticks);
    }
}
