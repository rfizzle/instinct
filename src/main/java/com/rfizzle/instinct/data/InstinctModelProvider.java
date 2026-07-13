package com.rfizzle.instinct.data;

import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;

/**
 * Instinct's item and block models ({@code design/SPEC.md} §3, §7). The pedigree treat and the vet
 * kit are flat 2D item sprites; their {@code .glyph} masters live under {@code art/glyphs/}.
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
        generators.generateFlatItem(InstinctItems.VET_KIT, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(InstinctItems.COMMAND_WHISTLE, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(InstinctItems.KEEPSAKE_COLLAR, ModelTemplates.FLAT_ITEM);
    }
}
