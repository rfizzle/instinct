package com.rfizzle.instinct.data;

import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;

/**
 * Instinct's item and block models ({@code design/SPEC.md} §3). The pedigree treat is a flat 2D
 * item sprite; its {@code .glyph} master lives under {@code art/glyphs/}.
 */
public class InstinctModelProvider extends FabricModelProvider {

    public InstinctModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
        generators.generateFlatItem(InstinctItems.PEDIGREE_TREAT, ModelTemplates.FLAT_ITEM);
    }
}
