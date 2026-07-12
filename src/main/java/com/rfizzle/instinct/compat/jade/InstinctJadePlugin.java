package com.rfizzle.instinct.compat.jade;

import com.rfizzle.instinct.block.FeedingTroughBlock;
import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import net.minecraft.world.entity.animal.Animal;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade plugin discovery, wired through the {@code "jade"} entrypoint in {@code fabric.mod.json}.
 * Jade only class-loads this when it is present, so nothing here resolves without the viewer. Data
 * providers key on the block-entity / entity class; component providers on the block / entity class
 * — Jade walks the hierarchy, so registering on {@code Animal} covers every covered species.
 */
@WailaPlugin
public class InstinctJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(AnimalJadeProvider.INSTANCE, Animal.class);
        registration.registerBlockDataProvider(TroughJadeProvider.INSTANCE, FeedingTroughBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(AnimalJadeProvider.INSTANCE, Animal.class);
        registration.registerBlockComponent(TroughJadeProvider.INSTANCE, FeedingTroughBlock.class);
    }
}
