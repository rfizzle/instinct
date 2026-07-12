package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.item.PedigreeTreatItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Instinct's registered items and its creative tab ({@code design/SPEC.md} §3, §7). The pedigree
 * treat and the vet kit are the mod's first items; the whistle and trough join this class and the
 * tab as their features land. Registered once from {@code onInitialize}, before the creative tab
 * enumerates them.
 */
public final class InstinctItems {

    /** Insertion-ordered so the creative tab lists items in registration order. */
    private static final List<Item> ITEMS = new ArrayList<>();

    /** The pedigree treat — flags an animal's next offspring to be born prime, stacks to 16 (§3). */
    public static final Item PEDIGREE_TREAT = new PedigreeTreatItem(new Item.Properties().stacksTo(16));

    /** The vet kit — a plain revival remedy in {@code #instinct:revive_items}, stacks to 16 (§7). */
    public static final Item VET_KIT = new Item(new Item.Properties().stacksTo(16));

    private static boolean registered = false;

    private InstinctItems() {
    }

    /** Idempotent: datagen bootstrap and test setup may reach this beside {@code onInitialize}. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        registerItem("pedigree_treat", PEDIGREE_TREAT);
        registerItem("vet_kit", VET_KIT);
        registerCreativeTab();
    }

    private static void registerItem(String name, Item item) {
        Registry.register(BuiltInRegistries.ITEM, Instinct.id(name), item);
        ITEMS.add(item);
    }

    private static void registerCreativeTab() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Instinct.id("instinct"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.instinct"))
                        .icon(() -> new ItemStack(PEDIGREE_TREAT))
                        .displayItems((params, output) -> ITEMS.forEach(output::accept))
                        .build());
    }
}
