package com.rfizzle.instinct.data;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * Instinct's item tags ({@code design/SPEC.md} §Animal Coverage — item tags): what the trough
 * accepts, what the mirror fallback may duplicate, and what revives a downed pet. Animal mods and
 * packs extend them with ordinary tag entries; the revive tag is open by convention for siblings
 * (e.g. Distillation's brewed remedy) to add their own entries.
 */
public class InstinctItemTagProvider extends FabricTagProvider.ItemTagProvider {

    public static final TagKey<Item> TROUGH_FOOD = tag("trough_food");
    public static final TagKey<Item> MIRROR_PRODUCTS = tag("mirror_products");
    public static final TagKey<Item> REVIVE_ITEMS = tag("revive_items");

    public InstinctItemTagProvider(FabricDataOutput output,
                                   CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        getOrCreateTagBuilder(TROUGH_FOOD)
                .add(Items.WHEAT)
                .add(Items.CARROT)
                .add(Items.POTATO)
                .add(Items.BEETROOT)
                .add(Items.WHEAT_SEEDS)
                .add(Items.BEETROOT_SEEDS)
                .add(Items.MELON_SEEDS)
                .add(Items.PUMPKIN_SEEDS)
                .add(Items.TORCHFLOWER_SEEDS)
                .add(Items.PITCHER_POD);

        getOrCreateTagBuilder(MIRROR_PRODUCTS)
                .add(Items.LEATHER)
                .add(Items.FEATHER)
                .add(Items.RABBIT_HIDE)
                .forceAddTag(ItemTags.WOOL);

        getOrCreateTagBuilder(REVIVE_ITEMS)
                .add(Items.GOLDEN_APPLE)
                .add(Items.ENCHANTED_GOLDEN_APPLE)
                .add(InstinctItems.VET_KIT);
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Instinct.id(path));
    }
}
