package com.rfizzle.instinct.kennel;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.registry.InstinctBlocks;
import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.whistle.WhistleActions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Kennel-post homing ({@code design/SPEC.md} §9). Owns the entity-load wiring (a {@link HomeGoal} on
 * every tamable animal, inert until the pet is recalled), the whistle-at-post assignment gesture, and
 * the transient <em>recall</em> state the goal reads. The home itself is a persistent
 * {@link com.rfizzle.instinct.data.HomeData} attachment written by the whistle; the recall — "walk to
 * your post now and settle" — is a momentary order held only in memory, so a reload sits the pet where
 * it is rather than resuming a walk.
 *
 * <p>Like guard and predator watch, the goal is added to every tamable on load but does nothing until
 * the pet is recalled, so an un-homed pet is exactly vanilla and the world pays nothing for it. The
 * recall map is server-thread-confined and cleared on server stop; a pet that unloads drops out of it.
 */
public final class KennelHandler {

    /** A recall runs at most this many ticks before the pet gives up and settles where it stands —
     *  a post it can't reach (walled off, too far, another dimension) never leaves a pet pathing forever. */
    static final int RECALL_DEADLINE_TICKS = 600;

    /** Pets currently walking home → the game time their recall gives up. Weak keys so a discarded pet
     *  can't pin memory; still cleared on unload and on server stop. */
    private static final Map<TamableAnimal, Long> RECALLING = new WeakHashMap<>();

    private KennelHandler() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof TamableAnimal pet)) {
                return;
            }
            try {
                addHomeGoal(pet);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to install home goal on {}", entity.getType(), e);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof TamableAnimal pet) {
                RECALLING.remove(pet);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RECALLING.clear());
        UseBlockCallback.EVENT.register(KennelHandler::onUseBlock);
    }

    /** Adds the home goal to a pet once. Inert until the pet is recalled, so it is safe to add to every
     *  tamable animal on load. */
    private static void addHomeGoal(TamableAnimal pet) {
        boolean present = pet.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof HomeGoal);
        if (!present) {
            pet.goalSelector.addGoal(HomeGoal.PRIORITY, new HomeGoal(pet));
        }
    }

    // ── Recall state (read by HomeGoal, written by the whistle) ──────────────────────────────────

    /** Begin a recall: the pet stands, drops any guard order, and walks to its home post until it
     *  arrives or the deadline passes. */
    public static void recall(TamableAnimal pet, long now) {
        pet.removeAttached(InstinctAttachments.GUARD);
        pet.setOrderedToSit(false);
        pet.getNavigation().stop();
        RECALLING.put(pet, now + RECALL_DEADLINE_TICKS);
    }

    /** End a recall (arrived, gave up, or a new order replaced it). */
    public static void stopRecall(TamableAnimal pet) {
        RECALLING.remove(pet);
    }

    public static boolean isRecalling(TamableAnimal pet) {
        return RECALLING.containsKey(pet);
    }

    /** The game time this pet's recall gives up; {@code Long.MIN_VALUE} (already past) when not recalling. */
    public static long recallDeadline(TamableAnimal pet) {
        Long deadline = RECALLING.get(pet);
        return deadline != null ? deadline : Long.MIN_VALUE;
    }

    // ── The assign-home gesture: right-click a kennel post with the whistle ──────────────────────

    /**
     * Point the whistle at a kennel post and right-click (no sneak — sneak + right-click is the guard
     * order): every commandable pet in range adopts that post as home and walks there now. Fires
     * before vanilla block use, purely server-authoritative; the block itself has no interaction.
     */
    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
                                                BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()
                || !player.getMainHandItem().is(InstinctItems.COMMAND_WHISTLE)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hitResult.getBlockPos();
        if (!level.getBlockState(pos).is(InstinctBlocks.KENNEL_POST)
                || !InstinctConfig.get().enableKennelPost
                || player.getCooldowns().isOnCooldown(InstinctItems.COMMAND_WHISTLE)) {
            // Feature off, wrong block, or on cooldown: leave the click to vanilla (the post is inert).
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                WhistleActions.performAssignHome(serverPlayer, pos);
            } catch (Exception e) {
                Instinct.LOGGER.error("Kennel assign-home failed for {}", serverPlayer.getGameProfile().getName(), e);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
