package com.rfizzle.instinct.compat.tribulation;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.tribulation.api.TribulationAPI;
import net.minecraft.world.entity.TamableAnimal;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Soft integration with Tribulation (SPEC §Compat, consumer): a pet's live veterancy accrual counts
 * double while its local difficulty tier is {@value TribulationTier#DOUBLING_TIER} or higher — pets
 * forged in a harder world grow veteran faster. Registered through Instinct's own veterancy-rate
 * provider slot, so it composes multiplicatively with the §2 mentor bonus (2.0 × 1.25 = 2.5) and
 * only ever affects loaded, live accrual.
 *
 * <p>{@link #register} is reached only behind an {@code isModLoaded("tribulation")} guard in
 * {@code onInitialize}. Every {@code TribulationAPI} reference lives in the nested {@link Api}
 * holder, class-loaded only once that guard has passed, and the provider body catches
 * {@link Throwable} — an older Tribulation jar missing a method surfaces as a {@link LinkageError},
 * which must degrade to the un-integrated 1.0 rate, never crash the accrual sweep.
 */
public final class TribulationCompat {

    private static final AtomicBoolean CALL_FAILURE_LOGGED = new AtomicBoolean(false);

    private TribulationCompat() {
    }

    /** Registers the doubling veterancy-rate provider. Called once, behind the mod-loaded guard. */
    public static void register() {
        InstinctAPI.setVeterancyRateProvider(TribulationCompat::rateFor);
    }

    /**
     * The local-tier accrual rate for one pet: Tribulation's effective level at the pet, classified
     * against its tier thresholds. Any API failure (absent method, throwing call) degrades to the
     * base 1.0 rate — the host's provider slot also clamps a bad return, but the consumer owns its
     * own {@code Throwable} isolation.
     */
    private static double rateFor(TamableAnimal pet) {
        try {
            return TribulationTier.rateFor(Api.effectiveLevel(pet), Api.tierThresholds());
        } catch (Throwable t) {
            if (CALL_FAILURE_LOGGED.compareAndSet(false, true)) {
                Instinct.LOGGER.warn("Tribulation API call failed; veterancy accruing at the base rate", t);
            }
            return TribulationTier.BASE_RATE;
        }
    }

    /**
     * The only class that touches {@code TribulationAPI}. Kept nested so the JVM never resolves the
     * Tribulation classes unless the {@code isModLoaded} guard that gates {@link #register} passed.
     */
    private static final class Api {
        private Api() {
        }

        static int effectiveLevel(TamableAnimal pet) {
            return TribulationAPI.getEffectiveLevel(pet);
        }

        static int[] tierThresholds() {
            return TribulationAPI.getTierThresholds();
        }
    }
}
