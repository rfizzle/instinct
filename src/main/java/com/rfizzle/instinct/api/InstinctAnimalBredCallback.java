package com.rfizzle.instinct.api;

import com.rfizzle.instinct.Instinct;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.animal.Animal;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fired server-side after Instinct resolves a newborn's bloodline grade at breeding
 * ({@code design/SPEC.md} §3), before the child is added to the world. Carries both parents, the
 * child, and the child's final {@link Grade}. Fires for every covered breeding while
 * {@code enableGenetics} is on — natural pairs, trough-fed pairs, and pedigree-treat (born-prime)
 * pairs alike — and does not fire when genetics is disabled or the child is uncovered.
 *
 * <p>A listener that throws is caught, logged, and skipped; it can never break the breeding or the
 * listeners registered after it.
 */
@Stable
@FunctionalInterface
public interface InstinctAnimalBredCallback {

    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<InstinctAnimalBredCallback> EVENT = EventFactory.createArrayBacked(InstinctAnimalBredCallback.class,
            listeners -> (parentA, parentB, child, grade) -> {
                for (InstinctAnimalBredCallback listener : listeners) {
                    try {
                        listener.onAnimalBred(parentA, parentB, child, grade);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is unrecoverable, not the listener misbehaving
                    } catch (Throwable t) {
                        // Throwable, not Exception: this is the boundary where untrusted listener code
                        // runs, and a consumer compiled against an older signature throws Error
                        // (AbstractMethodError, NoClassDefFoundError), which an Exception catch would
                        // let escape and kill the server tick.
                        // Once-only: breeding fires this per newborn, so an ungated log would put
                        // stack-trace formatting and appender I/O on the server thread at that rate.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Instinct.LOGGER.warn("InstinctAnimalBredCallback listener {} threw; skipping it",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    void onAnimalBred(Animal parentA, Animal parentB, Animal child, Grade grade);
}
