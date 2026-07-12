package com.rfizzle.instinct.compat.wthit;

import com.rfizzle.instinct.block.FeedingTroughBlock;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.world.entity.animal.Animal;

/**
 * WTHIT client plugin — registers the body (line) components. WTHIT matches against the target's
 * class hierarchy, so {@code Animal} covers every covered species and the block component keys on
 * the trough block, mirroring the common plugin's data registrations.
 */
public final class InstinctWthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(AnimalWthitProvider.INSTANCE, Animal.class);
        registrar.body(TroughWthitProvider.INSTANCE, FeedingTroughBlock.class);
    }
}
