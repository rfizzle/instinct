package com.rfizzle.instinct.data;

import com.rfizzle.instinct.registry.InstinctBlocks;
import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * Instinct's crafting recipes ({@code design/SPEC.md} §3, §7). Both are shapeless and both use a
 * honey bottle, whose own glass-bottle craft remainder returns the bottle — so neither needs a
 * custom serializer: the pedigree treat (golden carrot, hay bale, honey bottle) and the vet kit
 * (paper, string, honey bottle).
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

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, InstinctItems.VET_KIT)
                .requires(Items.PAPER)
                .requires(Items.STRING)
                .requires(Items.HONEY_BOTTLE)
                .unlockedBy("has_honey_bottle", has(Items.HONEY_BOTTLE))
                .save(output);

        // The feeding trough — a plank frame around a fence, open at the top (§5).
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, InstinctBlocks.FEEDING_TROUGH)
                .pattern("P P")
                .pattern("PFP")
                .define('P', ItemTags.PLANKS)
                .define('F', ConventionalItemTags.WOODEN_FENCES)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(output);
    }

    @Override
    public String getName() {
        return "Instinct Recipes";
    }
}
