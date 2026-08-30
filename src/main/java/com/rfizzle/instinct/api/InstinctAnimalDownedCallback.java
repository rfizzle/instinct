package com.rfizzle.instinct.api;

import com.rfizzle.instinct.Instinct;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Animal;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fired server-side the instant a tamed covered animal enters the downed state ({@code
 * design/SPEC.md} §7) — after its health is pinned to 1.0, its AI stopped, any rider ejected, and
 * its owner notified, but never for a death that was beyond saving (fire, lava, void,
 * {@code /kill}). Carries the animal and the lethal damage source that downed it.
 *
 * <p>Fires for every set the downed state covers — a pets-set pet and a mounts-set mount alike —
 * so consumers see one signal per down regardless of species. The parameter is {@link Animal}
 * rather than a narrower type because mounts-set membership resolves by entity-type id through
 * config and the {@code #instinct:mounts} tag, so a mount need not be an {@code AbstractHorse}.
 *
 * <p>Fires only when {@code enableDownedState} is on.
 *
 * <p>A listener that throws is caught, logged, and skipped; it can never break the down or the
 * listeners registered after it.
 */
@Stable
@FunctionalInterface
public interface InstinctAnimalDownedCallback {

    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<InstinctAnimalDownedCallback> EVENT = EventFactory.createArrayBacked(InstinctAnimalDownedCallback.class,
            listeners -> (animal, source) -> {
                for (InstinctAnimalDownedCallback listener : listeners) {
                    try {
                        listener.onAnimalDowned(animal, source);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is unrecoverable, not the listener misbehaving
                    } catch (Throwable t) {
                        // Throwable, not Exception: this is the boundary where untrusted listener code
                        // runs, and a consumer compiled against an older signature throws Error
                        // (AbstractMethodError, NoClassDefFoundError), which an Exception catch would
                        // let escape and kill the server tick.
                        // Once-only: a listener that throws once throws every time, and an ungated log
                        // would put stack-trace formatting on the server thread at the fire rate.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Instinct.LOGGER.warn("InstinctAnimalDownedCallback listener {} threw; skipping it",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    void onAnimalDowned(Animal animal, DamageSource source);
}
