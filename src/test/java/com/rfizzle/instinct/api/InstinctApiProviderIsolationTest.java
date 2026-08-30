package com.rfizzle.instinct.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * API-STANDARD §3.1: the veterancy-rate provider slot is a trust boundary. A provider that throws
 * yields the host's default of 1.0 rather than escaping into the accrual sweep — including when it
 * throws an {@code Error}, which is what a consumer compiled against an older signature produces and
 * what a {@code catch (Exception)} would have let through. {@code VirtualMachineError} is the one
 * carve-out: the JVM is unrecoverable, so it is rethrown unchanged.
 *
 * <p>The provider ignores its argument in every case here, so {@code null} is a legal stand-in for a
 * pet — this exercises the isolation, not the accrual.
 */
class InstinctApiProviderIsolationTest {

    @AfterEach
    void restoreTheDefaultProvider() {
        InstinctAPI.setVeterancyRateProvider(pet -> 1.0);
    }

    @Test
    void aProviderThrowingAnExceptionYieldsTheDefaultRate() {
        InstinctAPI.setVeterancyRateProvider(pet -> {
            throw new IllegalStateException("provider blew up");
        });

        assertEquals(1.0, InstinctAPI.resolveVeterancyRate(null));
    }

    @Test
    void aProviderThrowingAnErrorYieldsTheDefaultRate() {
        // The stale-signature shape: an Exception catch would let this escape and kill the server tick.
        InstinctAPI.setVeterancyRateProvider(pet -> {
            throw new AbstractMethodError("compiled against an older signature");
        });

        assertEquals(1.0, assertDoesNotThrow(() -> InstinctAPI.resolveVeterancyRate(null)));
    }

    @Test
    void aVirtualMachineErrorIsRethrownRatherThanAbsorbed() {
        InstinctAPI.setVeterancyRateProvider(pet -> {
            throw new StackOverflowError("the JVM is gone, not the provider");
        });

        assertThrows(StackOverflowError.class, () -> InstinctAPI.resolveVeterancyRate(null));
    }

    @Test
    void aNonFiniteReturnIsClampedToTheDefaultRate() {
        InstinctAPI.setVeterancyRateProvider(pet -> Double.NaN);
        assertEquals(1.0, InstinctAPI.resolveVeterancyRate(null));

        InstinctAPI.setVeterancyRateProvider(pet -> Double.POSITIVE_INFINITY);
        assertEquals(1.0, InstinctAPI.resolveVeterancyRate(null));
    }

    @Test
    void aNullProviderIsIgnoredAndLeavesTheStandingOneInPlace() {
        InstinctAPI.setVeterancyRateProvider(pet -> 2.0);
        InstinctAPI.setVeterancyRateProvider(null);

        assertEquals(2.0, InstinctAPI.resolveVeterancyRate(null));
    }
}
