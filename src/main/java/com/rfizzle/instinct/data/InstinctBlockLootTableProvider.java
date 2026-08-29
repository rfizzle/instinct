package com.rfizzle.instinct.data;

import com.rfizzle.instinct.registry.InstinctBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;

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
        dropSelfWithSequence(InstinctBlocks.FEEDING_TROUGH);
        dropSelfWithSequence(InstinctBlocks.KENNEL_POST);
    }

    /**
     * {@link #dropSelf(Block)} with the table's random sequence restored.
     *
     * <p>Vanilla's own {@code LootTableProvider} stamps every table with
     * {@code random_sequence = <its own id>} before setting the param set;
     * {@code FabricLootTableProviderImpl.run} only sets the param set, so a bare
     * {@code dropSelf} silently omits the key. It selects the per-table RNG stream —
     * seeded off the world seed and persisted in the level's {@code random_sequences}
     * data — that the {@code survives_explosion} condition rolls against, so a table
     * without it sits outside the sequence state vanilla puts every table into.
     * See the {@code mc-datagen} skill.
     */
    private void dropSelfWithSequence(Block block) {
        add(block, createSingleItemTable(block).setRandomSequence(block.getLootTable().location()));
    }
}
