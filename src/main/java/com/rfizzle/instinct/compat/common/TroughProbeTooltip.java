package com.rfizzle.instinct.compat.common;

import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import com.rfizzle.instinct.config.InstinctConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The viewer-agnostic core for the feeding-trough probe tooltip (Jade / WTHIT), per SPEC §Compat:
 * the stored item and count, and the livestock population in range against the breeding cap. The
 * population re-scans at request time (the trough keeps no stored count) through the same
 * {@link FeedingTroughBlockEntity#livestockInRange} filter the feeding loop uses, so the number a
 * player reads is exactly what the cap is measured against. No Jade or WTHIT type is imported.
 */
public final class TroughProbeTooltip {

    public static final String KEY_PRESENT = "instinct:trough_present";
    public static final String KEY_ITEM = "instinct:trough_item";
    public static final String KEY_COUNT = "instinct:trough_count";
    public static final String KEY_POPULATION = "instinct:trough_population";
    public static final String KEY_CAP = "instinct:trough_cap";

    private TroughProbeTooltip() {
    }

    /** Server side. Gated once on {@code enableTrough}; a gated-off write leaves no presence flag. */
    public static void writeServerData(CompoundTag tag, ServerLevel level, BlockPos pos, FeedingTroughBlockEntity trough) {
        if (!InstinctConfig.get().enableTrough) {
            return;
        }
        tag.putBoolean(KEY_PRESENT, true);
        ItemStack stored = trough.getStored();
        if (!stored.isEmpty()) {
            tag.putString(KEY_ITEM, BuiltInRegistries.ITEM.getKey(stored.getItem()).toString());
            tag.putInt(KEY_COUNT, stored.getCount());
        }
        tag.putInt(KEY_POPULATION, FeedingTroughBlockEntity.livestockInRange(level, pos).size());
        tag.putInt(KEY_CAP, InstinctConfig.get().troughPopulationCap);
    }

    /** Client side. Pure tag → lines; unknown item ids fall back to their raw string. */
    public static List<Component> buildLines(CompoundTag tag) {
        List<Component> lines = new ArrayList<>();
        if (!tag.getBoolean(KEY_PRESENT)) {
            return lines;
        }
        if (tag.contains(KEY_ITEM)) {
            lines.add(Component.translatable("tooltip.instinct.trough.stored",
                    storedName(tag.getString(KEY_ITEM)), tag.getInt(KEY_COUNT)));
        } else {
            lines.add(Component.translatable("tooltip.instinct.trough.empty"));
        }
        lines.add(Component.translatable("tooltip.instinct.trough.population",
                tag.getInt(KEY_POPULATION), tag.getInt(KEY_CAP)));
        return lines;
    }

    /** The stored item's display name, falling back to the raw id text for an unknown item. */
    private static Component storedName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
            Item item = BuiltInRegistries.ITEM.get(key);
            return new ItemStack(item).getHoverName();
        }
        return Component.literal(id);
    }
}
