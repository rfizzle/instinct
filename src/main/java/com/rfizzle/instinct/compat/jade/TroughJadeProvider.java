package com.rfizzle.instinct.compat.jade;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import com.rfizzle.instinct.compat.common.TroughProbeTooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade adapter for the feeding-trough tooltip — pure delegation to {@link TroughProbeTooltip}.
 */
public enum TroughJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getLevel() instanceof ServerLevel level
                && accessor.getBlockEntity() instanceof FeedingTroughBlockEntity trough) {
            TroughProbeTooltip.writeServerData(tag, level, accessor.getPosition(), trough);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        for (Component line : TroughProbeTooltip.buildLines(accessor.getServerData())) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Instinct.id("feeding_trough");
    }
}
