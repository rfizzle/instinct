package com.rfizzle.instinct.downed;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Pure downed-state classification ({@code design/SPEC.md} §7). Minecraft-adjacent but
 * game-instance-free at its core so it unit-tests without a world.
 */
public final class Downed {

    private Downed() {
    }

    /**
     * Whether a lethal blow is <b>beyond saving</b> — fire or lava, the void, or a kill command.
     * Such a blow is never cancelled into the downed state; the pet dies exactly as vanilla, downed
     * or not (a downed pet that falls into the void, or is {@code /kill}ed, dies). This tests only
     * the lethal blow that would have killed a healthy pet, never damage after downing.
     */
    public static boolean beyondSaving(DamageSource source) {
        return beyondSaving(
                source.is(DamageTypeTags.IS_FIRE),
                source.is(DamageTypes.LAVA),
                source.is(DamageTypes.FELL_OUT_OF_WORLD),
                source.is(DamageTypes.GENERIC_KILL));
    }

    /** Pure core of {@link #beyondSaving(DamageSource)} — any one of the four edges is fatal. */
    static boolean beyondSaving(boolean fire, boolean lava, boolean voidDamage, boolean kill) {
        return fire || lava || voidDamage || kill;
    }
}
