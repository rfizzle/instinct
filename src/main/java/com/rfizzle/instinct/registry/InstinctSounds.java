package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Instinct's custom sound events — synthesized cues only ({@code design/SPEC.md} §Sound Design);
 * everything organic stays vanilla. Registered once from {@code onInitialize}.
 */
public final class InstinctSounds {

    /** The veterancy rank-up cue — a warm two-chime played at the pet (SPEC §2). */
    public static final SoundEvent RANK_UP =
            SoundEvent.createVariableRangeEvent(Instinct.id("rank_up"));

    /** The revival cue — a soft rising shimmer played at a revived pet (SPEC §7). */
    public static final SoundEvent REVIVE =
            SoundEvent.createVariableRangeEvent(Instinct.id("revive"));

    private static boolean registered = false;

    private InstinctSounds() {
    }

    /** Idempotent: datagen bootstrap and test setup may reach this beside {@code onInitialize}. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.SOUND_EVENT, Instinct.id("rank_up"), RANK_UP);
        Registry.register(BuiltInRegistries.SOUND_EVENT, Instinct.id("revive"), REVIVE);
    }
}
