package com.rfizzle.instinct.data;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.advancement.PetRankTrigger;
import com.rfizzle.instinct.registry.InstinctCriteria;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.TameAnimalTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Instinct's advancement tab ({@code design/SPEC.md} §Advancements). The root grants on taming
 * any animal — the moment Instinct starts mattering; once the mod's own craftable items ship
 * (whistle, vet kit, treat, trough), their crafting criteria join the root per the SPEC and the
 * root icon becomes the command whistle. {@code old_friend} hangs off it on the
 * {@code instinct:pet_rank} criterion at rank 3.
 */
public class InstinctAdvancementProvider extends FabricAdvancementProvider {

    private static final ResourceLocation HUSBANDRY_BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/husbandry.png");

    protected InstinctAdvancementProvider(FabricDataOutput output,
                                          CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registryLookup,
                                    Consumer<AdvancementHolder> consumer) {
        InstinctCriteria.register();

        AdvancementHolder root = Advancement.Builder.advancement()
                .display(new ItemStack(Items.LEAD),
                        Component.translatable("advancements.instinct.root.title"),
                        Component.translatable("advancements.instinct.root.description"),
                        HUSBANDRY_BACKGROUND, AdvancementType.TASK, true, false, false)
                .addCriterion("tamed_an_animal", TameAnimalTrigger.TriggerInstance.tamedAnimal())
                .save(consumer, Instinct.id("root").toString());

        Advancement.Builder.advancement()
                .parent(root)
                .display(new ItemStack(Items.BONE),
                        Component.translatable("advancements.instinct.old_friend.title"),
                        Component.translatable("advancements.instinct.old_friend.description"),
                        null, AdvancementType.GOAL, true, true, false)
                .addCriterion("pet_reached_rank_3", InstinctCriteria.PET_RANK.createCriterion(
                        PetRankTrigger.TriggerInstance.forRank(3)))
                .save(consumer, Instinct.id("old_friend").toString());
    }
}
