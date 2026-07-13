package com.rfizzle.instinct.downed;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Carrying a downed pet to safety ({@code design/SPEC.md} §7). A downed, pets-set animal small
 * enough to lift — a baby (a pup) or a type in {@code #instinct:carryable} (cat, parrot) — is
 * scooped up by sneak + empty-hand use, becoming a <b>passenger of the rescuer</b>. Vanilla carries
 * the render, position sync, portal travel, and auto-eject on logout/death for free; the only added
 * state is a modest movement slowdown, a <b>transient</b> {@code MOVEMENT_SPEED} modifier that never
 * touches disk, so a logout can never strand a slowed player. Sneak + empty-hand on a block sets the
 * pet down again (still downed); reviving a carried pet (a co-op partner can, or after setting it
 * down) releases it through {@link #releaseIfCarried}.
 *
 * <p>The pet stays downed the whole time — invulnerable, no AI, still whimpering — so lifting it is
 * safe and carrying only relocates it. Full-size pets and every mount are never carryable; they stay
 * where they fall.
 *
 * <p>{@code CARRIERS} tracks who currently holds the slowdown modifier — the one bit of server-side
 * state beyond vanilla's own passenger/vehicle fields — so a per-second sweep can strip a stale
 * modifier if a carried pet leaves by an untracked path ({@code /kill} or the void on a carried
 * pet). It is server-thread-confined and non-persisted: entries drop on disconnect and everything
 * clears on {@code SERVER_STOPPED}.
 */
public final class CarryHandler {

    /** Fixed id for the transient carry slowdown modifier on the carrier's movement speed. */
    public static final ResourceLocation CARRY_SLOW_ID = Instinct.id("carry_slowdown");
    /** Cadence of the stale-modifier reconcile sweep (SPEC §7). One second is ample for a rare edge. */
    static final int RECONCILE_INTERVAL_TICKS = 20;

    /** UUIDs of players currently holding the carry slowdown modifier. Non-persisted, server-thread. */
    private static final Set<UUID> CARRIERS = new LinkedHashSet<>();

    private static int tickCounter;

    private CarryHandler() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register(CarryHandler::onUseEntity);
        UseBlockCallback.EVENT.register(CarryHandler::onUseBlock);
        ServerTickEvents.END_SERVER_TICK.register(CarryHandler::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CARRIERS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CARRIERS.clear();
            tickCounter = 0;
        });
    }

    // --- Pick up ----------------------------------------------------------------------------

    /**
     * The sneak + empty-hand pick-up gesture. Registered ahead of {@link DownedHandler#onUseEntity}
     * so it claims the empty-hand interaction on a carryable downed pet before the revival handler
     * suppresses it; every other case returns {@code PASS} and falls through to revival.
     */
    private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
                                                 Entity entity, @Nullable EntityHitResult hitResult) {
        if (!(entity instanceof Animal animal) || hand != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        try {
            return tryPickUp(player, level, animal);
        } catch (Exception e) {
            Instinct.LOGGER.error("Carry pick-up failed for {}", animal.getType(), e);
            return InteractionResult.PASS;
        }
    }

    private static InteractionResult tryPickUp(Player player, Level level, Animal animal) {
        if (!InstinctConfig.get().enableCarryDowned || !carryable(animal) || animal.isPassenger()) {
            return InteractionResult.PASS;
        }
        // Hands full: a rescuer carries one pet at a time. Say so rather than silently no-op.
        if (!player.getPassengers().isEmpty()) {
            if (!level.isClientSide && player instanceof ServerPlayer server) {
                server.displayClientMessage(Component.translatable("notification.instinct.hands_full"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return InteractionResult.CONSUME;
        }
        animal.startRiding(player, true);
        applySlowdown(player);
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(
                    Component.translatable("notification.instinct.pet_carried", animal.getName()), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Whether a downed animal is small enough to carry: pets-set, and a baby or a tagged small pet. */
    private static boolean carryable(Animal animal) {
        // Cheap downed check first, so a vanilla sneak-empty-hand click (a sit toggle) on a healthy
        // animal never pays for the config/tag membership resolve.
        if (!InstinctAPI.isDowned(animal)) {
            return false;
        }
        boolean pet = AnimalCoverage.membershipOf(animal).pet();
        return Carry.carryable(true, pet, animal.isBaby(),
                animal.getType().is(AnimalCoverage.CARRYABLE_TAG));
    }

    // --- Set down ---------------------------------------------------------------------------

    /**
     * The sneak + empty-hand set-down gesture. Not gated on the feature toggle — a carry already in
     * progress can always be put down, even if an admin disables carrying mid-rescue.
     */
    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
                                                BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(player.getFirstPassenger() instanceof Animal animal) || !InstinctAPI.isDowned(animal)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        try {
            animal.stopRiding();
            removeSlowdown(player);
            if (player instanceof ServerPlayer server) {
                server.displayClientMessage(
                        Component.translatable("notification.instinct.pet_set_down", animal.getName()), true);
            }
        } catch (Exception e) {
            Instinct.LOGGER.error("Carry set-down failed for {}", animal.getType(), e);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Releases a carried animal from whoever is carrying it, clearing their slowdown — the revival
     * path calls this so a pet revived in someone's arms cleanly dismounts. A no-op when the animal
     * is not riding a player.
     */
    public static void releaseIfCarried(Animal animal) {
        if (animal.getVehicle() instanceof Player carrier) {
            animal.stopRiding();
            removeSlowdown(carrier);
        }
    }

    // --- Slowdown modifier ------------------------------------------------------------------

    private static void applySlowdown(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (speed.getModifier(CARRY_SLOW_ID) == null) {
            // Transient: never serialized, so a logout that ejects the pet also drops this for free.
            speed.addTransientModifier(new AttributeModifier(CARRY_SLOW_ID,
                    Carry.slowdownAmount(InstinctConfig.get().carrySlowdownFraction),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        // Track only once the modifier is actually on the player, keeping CARRIERS in step with it.
        CARRIERS.add(player.getUUID());
    }

    private static void removeSlowdown(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(CARRY_SLOW_ID) != null) {
            speed.removeModifier(CARRY_SLOW_ID);
        }
        CARRIERS.remove(player.getUUID());
    }

    // --- Reconcile sweep --------------------------------------------------------------------

    private static void onServerTick(MinecraftServer server) {
        if (++tickCounter < RECONCILE_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (CARRIERS.isEmpty()) {
            return;
        }
        try {
            reconcile(server);
        } catch (Exception e) {
            Instinct.LOGGER.error("Carry slowdown reconcile failed", e);
        }
    }

    /**
     * Strips the slowdown from any tracked carrier no longer carrying a downed pet — the backstop for
     * a carried pet lost to an untracked path ({@code /kill}, the void). A copy guards the removal.
     */
    private static void reconcile(MinecraftServer server) {
        for (UUID uuid : new ArrayList<>(CARRIERS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                CARRIERS.remove(uuid);
            } else if (!isCarrying(player)) {
                removeSlowdown(player);
            }
        }
    }

    private static boolean isCarrying(Player player) {
        return player.getFirstPassenger() instanceof Animal animal && InstinctAPI.isDowned(animal);
    }
}
