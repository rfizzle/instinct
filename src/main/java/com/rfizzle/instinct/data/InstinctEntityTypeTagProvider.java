package com.rfizzle.instinct.data;

import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

/**
 * The default Animal Coverage membership ({@code design/SPEC.md} §Animal Coverage), shipped as
 * ordinary entity-type tags so packs and animal mods override them like any tag. The horse family
 * is excluded from livestock — vanilla horses carry their own bred-stat inheritance, and grafting
 * grades onto it would double-dip.
 */
public class InstinctEntityTypeTagProvider extends FabricTagProvider.EntityTypeTagProvider {

    public InstinctEntityTypeTagProvider(FabricDataOutput output,
                                         CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(AnimalCoverage.PETS_TAG)
                .add(EntityType.WOLF)
                .add(EntityType.CAT)
                .add(EntityType.PARROT);

        getOrCreateTagBuilder(AnimalCoverage.LIVESTOCK_TAG)
                .add(EntityType.COW)
                .add(EntityType.SHEEP)
                .add(EntityType.PIG)
                .add(EntityType.CHICKEN)
                .add(EntityType.RABBIT)
                .add(EntityType.GOAT);

        getOrCreateTagBuilder(AnimalCoverage.LIVESTOCK_EXCLUDE_TAG)
                .add(EntityType.HORSE)
                .add(EntityType.DONKEY)
                .add(EntityType.MULE)
                .add(EntityType.CAMEL)
                .add(EntityType.LLAMA)
                .add(EntityType.TRADER_LLAMA)
                .add(EntityType.SKELETON_HORSE)
                .add(EntityType.ZOMBIE_HORSE);
    }
}
