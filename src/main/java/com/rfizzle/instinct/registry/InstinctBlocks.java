package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.block.FeedingTroughBlock;
import com.rfizzle.instinct.block.KennelPostBlock;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Instinct's registered blocks ({@code design/SPEC.md} §5). The feeding trough is the mod's first
 * block; each block registers with a companion {@link BlockItem} that joins the shared creative tab
 * ({@link InstinctItems}). Registered once from {@code onInitialize}, before the block-entity types
 * and the creative tab that enumerates the items.
 */
public final class InstinctBlocks {

    /** The feeding trough — worn planks, hay-lined, axe-mineable, flammable like planks (§5). */
    public static final Block FEEDING_TROUGH = new FeedingTroughBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .strength(2.0F)
            .noOcclusion());

    /** The kennel post — a wooden home marker for the pack, axe-mineable, flammable like planks (§9). */
    public static final Block KENNEL_POST = new KennelPostBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .strength(2.0F)
            .noOcclusion());

    private static boolean registered = false;

    private InstinctBlocks() {
    }

    /** Idempotent: datagen bootstrap and test setup may reach this beside {@code onInitialize}. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        registerBlock("feeding_trough", FEEDING_TROUGH);
        registerBlock("kennel_post", KENNEL_POST);
        // Flammable like the planks they are built from (vanilla planks are 5 / 20).
        FlammableBlockRegistry.getDefaultInstance().add(FEEDING_TROUGH, 5, 20);
        FlammableBlockRegistry.getDefaultInstance().add(KENNEL_POST, 5, 20);
    }

    private static void registerBlock(String name, Block block) {
        Registry.register(BuiltInRegistries.BLOCK, Instinct.id(name), block);
        Item blockItem = new BlockItem(block, new Item.Properties());
        Registry.register(BuiltInRegistries.ITEM, Instinct.id(name), blockItem);
        InstinctItems.enroll(blockItem);
    }
}
