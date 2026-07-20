package com.rfizzle.instinct.gametest.util;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Ground for a gametest structure. {@code EMPTY_STRUCTURE} is 8x8x8 of air with no floor baked
 * in, so any suite whose mobs outlive their spawn tick has to lay its own — otherwise they fall
 * out of the structure and the assertions chase an entity that is no longer where the test put
 * it. Two layers rather than one so a mob that clips a corner still lands on stone; mobs walk on
 * the y=2 surface.
 */
public final class TestFloors {

    /** The side length of {@code EMPTY_STRUCTURE}, and so the default floor extent. */
    public static final int DEFAULT_SIZE = 8;

    private TestFloors() {
    }

    /** A {@link #DEFAULT_SIZE} square floor, filling an unmodified {@code EMPTY_STRUCTURE}. */
    public static void buildFloor(GameTestHelper helper) {
        buildFloor(helper, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    /** A floor spanning {@code width} by {@code depth}, for suites on a larger template. */
    public static void buildFloor(GameTestHelper helper, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
    }
}
