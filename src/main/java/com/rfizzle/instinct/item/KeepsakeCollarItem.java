package com.rfizzle.instinct.item;

import com.rfizzle.instinct.registry.InstinctDataComponents;
import com.rfizzle.instinct.veterancy.Veterancy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The keepsake collar ({@code design/SPEC.md} §7): the memento a tamed pet leaves when it is lost
 * beyond saving — to fire, lava, or the void. A pure keepsake with zero gameplay power: it is in no
 * tag, revives nothing, crafts into nothing. Its tooltip renders the {@link KeepsakeEngraving}
 * frozen onto the stack — the pet's name, and its veterancy standing at the moment of loss. A bare,
 * unengraved collar (the creative-tab item) shows a single flavour line instead.
 */
public class KeepsakeCollarItem extends Item {

    public KeepsakeCollarItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        KeepsakeEngraving engraving = stack.get(InstinctDataComponents.KEEPSAKE_ENGRAVING);
        if (engraving == null) {
            tooltip.add(Component.translatable("tooltip.instinct.keepsake_collar.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(engraving.petName().copy().withStyle(ChatFormatting.ITALIC));
        MutableComponent detail = engraving.rank() > 0
                ? Component.translatable("tooltip.instinct.keepsake_collar.ranked",
                        Component.translatable(Veterancy.rankKey(engraving.rank())), engraving.daysSeen())
                : Component.translatable("tooltip.instinct.keepsake_collar.days", engraving.daysSeen());
        tooltip.add(detail.withStyle(ChatFormatting.GRAY));
    }
}
