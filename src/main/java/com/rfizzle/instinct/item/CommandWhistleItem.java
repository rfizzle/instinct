package com.rfizzle.instinct.item;

import com.rfizzle.instinct.whistle.WhistleActions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The command whistle ({@code design/SPEC.md} §6): a stack-1, never-breaking item that moves the
 * whole pack. Right-click ({@link #use}) raycasts for a target and orders an attack or a livestock
 * round-up; the Stay/Follow toggle rides the left-click path (payload + attack callbacks) in
 * {@code com.rfizzle.instinct.whistle.Whistle}. Everything resolves server-authoritatively in
 * {@link WhistleActions}; the item only routes the right-click gesture there, once per off-cooldown
 * click from the main hand.
 */
public class CommandWhistleItem extends Item {

    public CommandWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            WhistleActions.performCommand(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
