package com.rfizzle.instinct.compat.wthit;

import com.rfizzle.instinct.compat.common.AnimalProbeTooltip;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Animal;

/**
 * WTHIT adapter for the covered-animal tooltip — pure delegation to {@link AnimalProbeTooltip}, so
 * the lines match Jade's by construction.
 */
public enum AnimalWthitProvider implements IDataProvider<Animal>, IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<Animal> accessor, IPluginConfig config) {
        AnimalProbeTooltip.writeServerData(data.raw(), accessor.getTarget());
    }

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        for (Component line : AnimalProbeTooltip.buildLines(accessor.getData().raw())) {
            tooltip.addLine(line);
        }
    }
}
