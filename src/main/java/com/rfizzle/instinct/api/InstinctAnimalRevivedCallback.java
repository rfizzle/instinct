package com.rfizzle.instinct.api;

import com.rfizzle.instinct.Instinct;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fired server-side the instant a downed animal is back on its feet — after its health,
 * regeneration, and post-revive invulnerability are applied and any rank penalty taken. Carries the
 * animal, the player responsible if there was one, and the item spent if any.
 *
 * <p>Both paths out of the downed state fire it:
 *
 * <ul>
 *   <li><b>Item revival</b> ({@code design/SPEC.md} §7) — any player, not only the owner, uses an
 *       item from {@code #instinct:revive_items}. {@code reviver} is that player and {@code item}
 *       is the stack they used — the live in-hand stack, not yet decremented, so copy it if you
 *       need to retain it.
 *   <li><b>Kennel-post recovery</b> ({@code design/SPEC.md} §9) — a downed pets-set pet gets back
 *       up on its own beside a kennel post, with no player and no item involved. {@code reviver} is
 *       {@code null} and {@code item} is {@link ItemStack#EMPTY}. <b>Consumers must null-check
 *       {@code reviver}.</b>
 * </ul>
 *
 * <p>Fires for every set the downed state covers — a pets-set pet and a mounts-set mount alike. The
 * parameter is {@link Animal} rather than a narrower type because mounts-set membership resolves by
 * entity-type id through config and the {@code #instinct:mounts} tag, so a mount need not be an
 * {@code AbstractHorse}.
 *
 * <p>A listener that throws is caught, logged, and skipped; it can never break the revival or the
 * listeners registered after it.
 */
@Stable
@FunctionalInterface
public interface InstinctAnimalRevivedCallback {

    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<InstinctAnimalRevivedCallback> EVENT = EventFactory.createArrayBacked(InstinctAnimalRevivedCallback.class,
            listeners -> (animal, reviver, item) -> {
                for (InstinctAnimalRevivedCallback listener : listeners) {
                    try {
                        listener.onAnimalRevived(animal, reviver, item);
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
                            Instinct.LOGGER.warn("InstinctAnimalRevivedCallback listener {} threw; skipping it",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    void onAnimalRevived(Animal animal, @Nullable Player reviver, ItemStack item);
}
