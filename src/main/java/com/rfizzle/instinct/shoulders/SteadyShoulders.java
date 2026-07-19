package com.rfizzle.instinct.shoulders;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/**
 * Steady shoulders ({@code design/SPEC.md} §1): a perched pets-set animal stays on the shoulder
 * through the incidental knocks that dislodge a vanilla parrot — jumps, sprint-jumps, short falls,
 * and minor damage. It comes off only when the owner drops it on purpose (sneak) or takes a serious
 * hit, and in vanilla's genuinely incompatible states (in water, flying, sleeping, powder snow),
 * which stay untouched.
 *
 * <p>Vanilla funnels every dismount through the parameterless {@code Player#removeEntitiesOnShoulder},
 * called from {@code aiStep()} (fall/water/flying/sleeping/powder-snow) and {@code hurt()} (any damage).
 * {@code PlayerMixin} gates those two call sites through the pure predicates here and adds the sneak
 * drop; the riptide spin-attack call site is left alone. Everything is gated on the perched entity
 * actually being an Instinct pets-set animal, resolved from the stored shoulder {@link CompoundTag},
 * so a non-pet modded shoulder-rider keeps exact vanilla behavior. Server-authoritative in effect:
 * the two suppressible call sites that matter run server-side, and the coverage resolve reads a
 * server-seeded cache.
 */
public final class SteadyShoulders {

    private SteadyShoulders() {
    }

    /**
     * Whether the {@code aiStep()} removal should be suppressed for a perched pet: true when the only
     * reason vanilla wants to dismount is the fall-distance branch. In water, flying, sleeping, and
     * powder snow are left to vanilla — they are not the incidental knocks this feature targets, and
     * suppressing them could strand a bird underwater or mid-flight.
     */
    public static boolean keepsThroughFall(Player player) {
        if (!InstinctConfig.get().enableSteadyShoulders || !holdsInstinctPet(player)) {
            return false;
        }
        return suppressesFallDismount(player.isInWater(), player.getAbilities().flying,
                player.isSleeping(), player.isInPowderSnow);
    }

    /**
     * Whether the {@code hurt()} removal should be suppressed for a perched pet: true when the raw
     * incoming damage is below the serious-hit threshold. A scratch (cactus, berries, a weak blow,
     * a small fall) keeps the bird; a real hit — and the fall damage of a hard landing — dislodges it.
     */
    public static boolean keepsThroughHit(Player player, float amount) {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableSteadyShoulders || !holdsInstinctPet(player)) {
            return false;
        }
        return !dismountsOnHit(amount, config.steadyShoulderDismountDamage);
    }

    /** Whether a deliberate sneak should drop a perched pet this tick. */
    public static boolean dropsOnSneak(Player player) {
        return InstinctConfig.get().enableSteadyShoulders
                && player.isShiftKeyDown()
                && holdsInstinctPet(player);
    }

    /** True when either shoulder holds an Instinct pets-set animal. */
    public static boolean holdsInstinctPet(Player player) {
        return isInstinctPet(player.getShoulderEntityLeft())
                || isInstinctPet(player.getShoulderEntityRight());
    }

    private static boolean isInstinctPet(CompoundTag shoulder) {
        if (shoulder == null || shoulder.isEmpty()) {
            return false;
        }
        EntityType<?> type = AnimalCoverage.typeById(shoulder.getString("id"));
        return type != null && AnimalCoverage.isPet(type);
    }

    /**
     * Pure core of {@link #keepsThroughFall(Player)}: the vanilla {@code aiStep()} guard fires on
     * fall/water/flying/sleeping/powder-snow, so if none of the latter four hold the reason is the
     * fall branch alone — the case this feature suppresses.
     */
    static boolean suppressesFallDismount(boolean inWater, boolean flying, boolean sleeping,
                                          boolean inPowderSnow) {
        return !(inWater || flying || sleeping || inPowderSnow);
    }

    /** Pure core of {@link #keepsThroughHit(Player, float)}: a hit dislodges at or above the threshold. */
    static boolean dismountsOnHit(float amount, double threshold) {
        return amount >= threshold;
    }
}
