package com.rfizzle.instinct.data;

import com.rfizzle.instinct.registry.InstinctBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;

import java.util.concurrent.CompletableFuture;

/**
 * Instinct's block tags ({@code design/SPEC.md} §5). The feeding trough is axe-mineable; it is not
 * placed in any {@code needs_*_tool} tag, so it drops when broken by hand or any tool.
 */
public class InstinctBlockTagProvider extends FabricTagProvider.BlockTagProvider {

    public InstinctBlockTagProvider(FabricDataOutput output,
                                    CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(BlockTags.MINEABLE_WITH_AXE)
                .add(InstinctBlocks.FEEDING_TROUGH);
    }
}
