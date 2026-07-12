package com.rfizzle.instinct.whistle;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The command whistle's server wiring ({@code design/SPEC.md} §6). Registers the
 * {@code instinct:whistle_toggle} payload and its receiver (the left-click-on-air path) and the two
 * attack callbacks (the left-click-on-block/entity path), all of which route to the same Stay/Follow
 * toggle in {@link WhistleActions}. A left-click holding the whistle is always cancelled, so the
 * whistle never breaks a block or strikes an entity — it only commands.
 */
public final class Whistle {

    private Whistle() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(WhistleTogglePayload.TYPE, WhistleTogglePayload.CODEC);
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
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> onLeftClick(player, hand, level));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> onLeftClick(player, hand, level));
    }

    /** Cancels a whistle left-click (so it never punches) and runs the toggle server-side. */
    private static InteractionResult onLeftClick(Player player, InteractionHand hand, Level level) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(InstinctItems.COMMAND_WHISTLE)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            handleToggle(serverPlayer);
        }
        // Cancel on both sides (swing the arm, break nothing).
        return InteractionResult.SUCCESS;
    }

    /** The single server gate for a toggle: main-hand whistle, off cooldown, then the action. */
    private static void handleToggle(ServerPlayer player) {
        if (!player.getMainHandItem().is(InstinctItems.COMMAND_WHISTLE)
                || player.getCooldowns().isOnCooldown(InstinctItems.COMMAND_WHISTLE)) {
            return;
        }
        WhistleActions.performToggle(player);
    }
}
