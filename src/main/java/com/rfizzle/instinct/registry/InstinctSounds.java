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

    /** The whistle Follow cue — a rising two-note call (SPEC §6). */
    public static final SoundEvent WHISTLE_FOLLOW =
            SoundEvent.createVariableRangeEvent(Instinct.id("whistle_follow"));

    /** The whistle Stay cue — a falling two-note call (SPEC §6). */
    public static final SoundEvent WHISTLE_STAY =
            SoundEvent.createVariableRangeEvent(Instinct.id("whistle_stay"));

    /** The whistle Attack cue — a sharp rising blast (SPEC §6). */
    public static final SoundEvent WHISTLE_ATTACK =
            SoundEvent.createVariableRangeEvent(Instinct.id("whistle_attack"));

    /** The whistle round-up cue — a rolling trill (SPEC §6). */
    public static final SoundEvent WHISTLE_HERD =
            SoundEvent.createVariableRangeEvent(Instinct.id("whistle_herd"));

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
        Registry.register(BuiltInRegistries.SOUND_EVENT, Instinct.id("whistle_follow"), WHISTLE_FOLLOW);
        Registry.register(BuiltInRegistries.SOUND_EVENT, Instinct.id("whistle_stay"), WHISTLE_STAY);
        Registry.register(BuiltInRegistries.SOUND_EVENT, Instinct.id("whistle_attack"), WHISTLE_ATTACK);
        Registry.register(BuiltInRegistries.SOUND_EVENT, Instinct.id("whistle_herd"), WHISTLE_HERD);
    }
}
