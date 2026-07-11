package com.rfizzle.instinct.data;

import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * Instinct's crafting recipes ({@code design/SPEC.md} §3). The pedigree treat is shapeless — a
 * golden carrot, a hay bale, and a honey bottle — and the honey bottle's own glass-bottle craft
 * remainder returns the bottle, so no custom serializer is needed.
 */
public class InstinctRecipeProvider extends FabricRecipeProvider {

    public InstinctRecipeProvider(FabricDataOutput output,
                                  CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void buildRecipes(RecipeOutput output) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, InstinctItems.PEDIGREE_TREAT)
                .requires(Items.GOLDEN_CARROT)
                .requires(Items.HAY_BLOCK)
                .requires(Items.HONEY_BOTTLE)
                .unlockedBy("has_hay_block", has(Items.HAY_BLOCK))
                .save(output);
    }

    @Override
    public String getName() {
        return "Instinct Recipes";
    }
}
