package com.rfizzle.instinct.keepsake;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Pure keepsake-collar classification ({@code design/SPEC.md} §7). A tamed pet lost <b>beyond
 * saving</b> to fire, lava, or the void leaves an engraved collar; these are exactly the deaths the
 * downed state cannot cancel ({@link com.rfizzle.instinct.downed.Downed#beyondSaving}), minus the
 * {@code /kill} command — an admin act, not grief, so it leaves nothing. Game-instance-free at its
 * core so it unit-tests without a world.
 */
public final class Keepsake {

    private Keepsake() {
    }

    /**
     * Whether a fatal blow leaves a keepsake collar — fire, lava, or the void. Tests only the lethal
     * blow itself; the caller has already established the victim is a tamed pet.
     */
    public static boolean keepsakeWorthy(DamageSource source) {
        return keepsakeWorthy(
                source.is(DamageTypeTags.IS_FIRE),
                source.is(DamageTypes.LAVA),
                source.is(DamageTypes.FELL_OUT_OF_WORLD));
    }

    /** Pure core of {@link #keepsakeWorthy(DamageSource)} — any one of the three edges leaves one. */
    static boolean keepsakeWorthy(boolean fire, boolean lava, boolean voidDamage) {
        return fire || lava || voidDamage;
    }

    /**
     * Whether the loss is a void loss — the drop must be lifted to safe ground at the pet's column
     * rather than left where the pet fell below the world. Fire and lava losses drop in place; the
     * fire-resistant collar survives them.
     */
    public static boolean isVoidLoss(DamageSource source) {
        return source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }
}
