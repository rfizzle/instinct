package com.rfizzle.instinct.whistle;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The command whistle's server wiring ({@code design/SPEC.md} §6). Registers the receivers for the
 * {@code instinct:whistle_toggle} and {@code instinct:whistle_locate} payloads (the
 * left-click-on-air path; the payload types themselves are declared in
 * {@link com.rfizzle.instinct.network.InstinctNetworking}) and the two attack callbacks (the
 * left-click-on-block/entity path), all of which route to the same Stay/Follow toggle in
 * {@link WhistleActions}. A left-click holding the whistle is always cancelled, so the whistle
 * never breaks a block or strikes an entity — it only commands.
 */
public final class Whistle {

    private Whistle() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(WhistleTogglePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> {
                try {
                    handleToggle(player);
                } catch (Exception e) {
                    Instinct.LOGGER.error("Whistle toggle failed for {}", player.getGameProfile().getName(), e);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(WhistleLocatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> {
                try {
                    handleLocate(player);
                } catch (Exception e) {
                    Instinct.LOGGER.error("Whistle locate failed for {}", player.getGameProfile().getName(), e);
                }
            });
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> onLeftClick(player, hand, level));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> onLeftClick(player, hand, level));
    }

    /**
     * Cancels a whistle left-click (so it never punches) and runs the server-side gesture: a plain
     * left-click toggles Stay/Follow, a sneak + left-click reports the lost-pet locator — matching the
     * air-click gestures the client swing hook sends, so a block or entity under the crosshair answers
     * the same way.
     */
    private static InteractionResult onLeftClick(Player player, InteractionHand hand, Level level) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(InstinctItems.COMMAND_WHISTLE)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isShiftKeyDown()) {
                handleLocate(serverPlayer);
            } else {
                handleToggle(serverPlayer);
            }
        }
        // Cancel on both sides (swing the arm, break nothing).
        return InteractionResult.SUCCESS;
    }

    /** The single server gate for a toggle: main-hand whistle, off cooldown, then the action. */
    private static void handleToggle(ServerPlayer player) {
        if (isReady(player)) {
            WhistleActions.performToggle(player);
        }
    }

    /** The single server gate for a locate: main-hand whistle, off cooldown, then the census. */
    private static void handleLocate(ServerPlayer player) {
        if (isReady(player)) {
            WhistleActions.performLocate(player);
        }
    }

    /** Whether a whistle gesture may fire: the main hand holds the whistle and it is off cooldown. */
    private static boolean isReady(ServerPlayer player) {
        return player.getMainHandItem().is(InstinctItems.COMMAND_WHISTLE)
                && !player.getCooldowns().isOnCooldown(InstinctItems.COMMAND_WHISTLE);
    }
}
