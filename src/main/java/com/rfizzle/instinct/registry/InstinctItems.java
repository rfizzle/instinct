package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.item.CommandWhistleItem;
import com.rfizzle.instinct.item.KeepsakeCollarItem;
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

    /** The command whistle — moves the whole pack; stack 1, no durability (never breaks) (§6). */
    public static final Item COMMAND_WHISTLE = new CommandWhistleItem(new Item.Properties().stacksTo(1));

    /**
     * The keepsake collar — a pet's memento on a beyond-saving loss; fire-resistant so the drop
     * survives the lava that took it, singular (a keepsake stacks to 1), zero gameplay power (§7).
     */
    public static final Item KEEPSAKE_COLLAR =
            new KeepsakeCollarItem(new Item.Properties().stacksTo(1).fireResistant());

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
        registerItem("command_whistle", COMMAND_WHISTLE);
        registerItem("keepsake_collar", KEEPSAKE_COLLAR);
        registerCreativeTab();
    }

    /**
     * Enrolls a block's companion item into the shared creative tab. {@link InstinctBlocks}
     * registers before this class builds the tab, so its trough block item is present when the tab
     * enumerates {@link #ITEMS}.
     */
    static void enroll(Item item) {
        ITEMS.add(item);
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
