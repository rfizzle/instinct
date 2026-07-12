package com.rfizzle.instinct.data;

import com.rfizzle.instinct.registry.InstinctBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;

import java.util.concurrent.CompletableFuture;

/**
 * Instinct's block loot tables ({@code design/SPEC.md} §5). The feeding trough drops itself when
 * broken; its stored stack is dropped separately by the block's {@code onRemove}, not the table.
 */
public class InstinctBlockLootTableProvider extends FabricBlockLootTableProvider {

    public InstinctBlockLootTableProvider(FabricDataOutput output,
                                          CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generate() {
        dropSelf(InstinctBlocks.FEEDING_TROUGH);
    }
}
