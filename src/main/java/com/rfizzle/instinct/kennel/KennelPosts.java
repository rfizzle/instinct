package com.rfizzle.instinct.kennel;

import com.rfizzle.instinct.registry.InstinctBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Block-level queries for kennel posts ({@code design/SPEC.md} §9). A kennel post carries no block
 * entity — it is just a marker — so these are live block-state reads rather than a tracked registry
 * (a position registry can't be rebuilt reliably across a restart, since {@code onPlace} never
 * re-fires on chunk load). The recovery scan runs only over the rare set of loaded downed pets and
 * on a coarse cadence, and the radius is clamped tight in config, so the box read stays cheap.
 */
public final class KennelPosts {

    private KennelPosts() {
    }

    /** Whether the block at {@code pos} is a standing kennel post. Loaded chunks only — an unloaded
     *  position reads as no post rather than forcing a load. */
    public static boolean isPostAt(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(InstinctBlocks.KENNEL_POST);
    }

    /**
     * Whether any kennel post stands within {@code radius} blocks (Chebyshev box, Euclidean gate) of
     * {@code center}. Early-exits on the first post found. Scans only loaded positions.
     */
    public static boolean hasPostWithin(Level level, BlockPos center, int radius) {
        int radiusSq = radius * radius;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (center.distSqr(pos) <= radiusSq && isPostAt(level, pos)) {
                return true;
            }
        }
        return false;
    }
}
