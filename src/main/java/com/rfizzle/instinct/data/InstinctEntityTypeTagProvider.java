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
 * grades onto it would double-dip — while joining the mounts set, which confers self-preservation
 * (§1) and downed/revival (§7). The undead horses stay out of both — they are not husbandry
 * animals — but a pack may add them to {@code #instinct:mounts} like any tag entry.
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

        // §8 Predator Watch: the wild hunters a stationed guardian turns from the pasture. Foxes
        // hunt chickens and rabbits, wolves hunt sheep and rabbits — the two vanilla neutral
        // predators of livestock. Only wild instances count; a tamed wolf pet is never a predator.
        // A pack or animal mod adds its own via this tag or the predatorsInclude config.
        getOrCreateTagBuilder(AnimalCoverage.PREDATORS_TAG)
                .add(EntityType.FOX)
                .add(EntityType.WOLF);

        getOrCreateTagBuilder(AnimalCoverage.LIVESTOCK_EXCLUDE_TAG)
                .add(EntityType.HORSE)
                .add(EntityType.DONKEY)
                .add(EntityType.MULE)
                .add(EntityType.CAMEL)
                .add(EntityType.LLAMA)
                .add(EntityType.TRADER_LLAMA)
                .add(EntityType.SKELETON_HORSE)
                .add(EntityType.ZOMBIE_HORSE);

        getOrCreateTagBuilder(AnimalCoverage.MOUNTS_TAG)
                .add(EntityType.HORSE)
                .add(EntityType.DONKEY)
                .add(EntityType.MULE)
                .add(EntityType.CAMEL)
                .add(EntityType.LLAMA)
                .add(EntityType.TRADER_LLAMA);

        // The undead horses are AbstractHorse subclasses, so the mount heuristic would otherwise
        // claim them (and a skeleton horse can be tamed, so the tame gate would not); exclude them
        // explicitly — they are not husbandry animals. A pack that wants them can still opt in via
        // the mountsInclude config (config beats tags) or by overriding this exclude tag.
        getOrCreateTagBuilder(AnimalCoverage.MOUNTS_EXCLUDE_TAG)
                .add(EntityType.SKELETON_HORSE)
                .add(EntityType.ZOMBIE_HORSE);
    }
}
