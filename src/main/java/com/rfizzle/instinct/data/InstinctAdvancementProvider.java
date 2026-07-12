package com.rfizzle.instinct.data;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.advancement.BredGradeTrigger;
import com.rfizzle.instinct.advancement.PetRankTrigger;
import com.rfizzle.instinct.advancement.PetRevivedTrigger;
import com.rfizzle.instinct.advancement.WhistlePackTrigger;
import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.registry.InstinctBlocks;
import com.rfizzle.instinct.registry.InstinctCriteria;
import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.critereon.RecipeCraftedTrigger;
import net.minecraft.advancements.critereon.TameAnimalTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Instinct's advancement tab ({@code design/SPEC.md} §Advancements). The root (icon: command
 * whistle) grants on taming any animal or crafting any of the mod's items — the pedigree treat, vet
 * kit, command whistle, or feeding trough. {@code old_friend} hangs off it on the
 * {@code instinct:pet_rank} criterion at rank 3, {@code best_in_show} on the
 * {@code instinct:bred_grade} criterion at prime, {@code back_from_the_brink} on the
 * {@code instinct:pet_revived} criterion, and {@code pack_leader} on the
 * {@code instinct:whistle_pack} criterion at ten pets.
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
        InstinctBlocks.register();
        InstinctItems.register();

        ResourceLocation treatRecipe = BuiltInRegistries.ITEM.getKey(InstinctItems.PEDIGREE_TREAT);
        ResourceLocation vetKitRecipe = BuiltInRegistries.ITEM.getKey(InstinctItems.VET_KIT);
        ResourceLocation whistleRecipe = BuiltInRegistries.ITEM.getKey(InstinctItems.COMMAND_WHISTLE);
        ResourceLocation troughRecipe = BuiltInRegistries.ITEM.getKey(InstinctBlocks.FEEDING_TROUGH.asItem());
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(new ItemStack(InstinctItems.COMMAND_WHISTLE),
                        Component.translatable("advancements.instinct.root.title"),
                        Component.translatable("advancements.instinct.root.description"),
                        HUSBANDRY_BACKGROUND, AdvancementType.TASK, true, false, false)
                .addCriterion("tamed_an_animal", TameAnimalTrigger.TriggerInstance.tamedAnimal())
                .addCriterion("crafted_pedigree_treat",
                        RecipeCraftedTrigger.TriggerInstance.craftedItem(treatRecipe))
                .addCriterion("crafted_vet_kit",
                        RecipeCraftedTrigger.TriggerInstance.craftedItem(vetKitRecipe))
                .addCriterion("crafted_command_whistle",
                        RecipeCraftedTrigger.TriggerInstance.craftedItem(whistleRecipe))
                .addCriterion("crafted_feeding_trough",
                        RecipeCraftedTrigger.TriggerInstance.craftedItem(troughRecipe))
                .requirements(net.minecraft.advancements.AdvancementRequirements.Strategy.OR)
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

        Advancement.Builder.advancement()
                .parent(root)
                .display(new ItemStack(InstinctItems.PEDIGREE_TREAT),
                        Component.translatable("advancements.instinct.best_in_show.title"),
                        Component.translatable("advancements.instinct.best_in_show.description"),
                        null, AdvancementType.GOAL, true, true, false)
                .addCriterion("bred_a_prime_animal", InstinctCriteria.BRED_GRADE.createCriterion(
                        BredGradeTrigger.TriggerInstance.forGrade(Grade.PRIME.level())))
                .save(consumer, Instinct.id("best_in_show").toString());

        Advancement.Builder.advancement()
                .parent(root)
                .display(new ItemStack(InstinctItems.VET_KIT),
                        Component.translatable("advancements.instinct.back_from_the_brink.title"),
                        Component.translatable("advancements.instinct.back_from_the_brink.description"),
                        null, AdvancementType.GOAL, true, true, false)
                .addCriterion("revived_a_pet", InstinctCriteria.PET_REVIVED.createCriterion(
                        PetRevivedTrigger.TriggerInstance.instance()))
                .save(consumer, Instinct.id("back_from_the_brink").toString());

        Advancement.Builder.advancement()
                .parent(root)
                .display(new ItemStack(InstinctItems.COMMAND_WHISTLE),
                        Component.translatable("advancements.instinct.pack_leader.title"),
                        Component.translatable("advancements.instinct.pack_leader.description"),
                        null, AdvancementType.GOAL, true, true, false)
                .addCriterion("commanded_ten_pets", InstinctCriteria.WHISTLE_PACK.createCriterion(
                        WhistlePackTrigger.TriggerInstance.forCount(10)))
                .save(consumer, Instinct.id("pack_leader").toString());
    }
}
