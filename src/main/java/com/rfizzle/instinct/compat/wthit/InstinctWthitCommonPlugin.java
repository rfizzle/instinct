package com.rfizzle.instinct.compat.wthit;

import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.entity.animal.Animal;

/**
 * WTHIT common plugin — registers the server-data providers. Discovered through
 * {@code waila_plugins.json} (not a {@code fabric.mod.json} entrypoint), whose {@code required}
 * block means WTHIT never class-loads this without itself present.
 */
public final class InstinctWthitCommonPlugin implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.entityData(AnimalWthitProvider.INSTANCE, Animal.class);
        registrar.blockData(TroughWthitProvider.INSTANCE, FeedingTroughBlockEntity.class);
    }
}
