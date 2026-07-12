package com.rfizzle.instinct.compat.wthit;

import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import com.rfizzle.instinct.compat.common.TroughProbeTooltip;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;

/**
 * WTHIT adapter for the feeding-trough tooltip — pure delegation to {@link TroughProbeTooltip}.
 */
public enum TroughWthitProvider implements IDataProvider<FeedingTroughBlockEntity>, IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<FeedingTroughBlockEntity> accessor, IPluginConfig config) {
        FeedingTroughBlockEntity trough = accessor.getTarget();
        TroughProbeTooltip.writeServerData(data.raw(), accessor.getLevel(), trough.getBlockPos(), trough);
    }

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        for (Component line : TroughProbeTooltip.buildLines(accessor.getData().raw())) {
            tooltip.addLine(line);
        }
    }
}
