package com.rfizzle.instinct.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The kennel post ({@code design/SPEC.md} §9): a humble wooden post a pet can be assigned to as home.
 * It is a marker — no block entity, no stored state. A pet's home lives on the pet (a
 * {@link com.rfizzle.instinct.data.HomeData} attachment written by the whistle); a downed pet
 * recovering beside a post is found by a proximity scan (the {@code downed} engine). The block only
 * has to stand there and be recognizable, so it is a plain {@link Block} with a post outline. It has
 * no collision — a thin marker pets can path right up to (and through), so a pet recalled home never
 * snags on its own post's base.
 */
public class KennelPostBlock extends Block {

    public static final MapCodec<KennelPostBlock> CODEC = simpleCodec(KennelPostBlock::new);

    /** A foot, a post column, and a small top board — the outline of a placed marker. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 3, 12),
            Block.box(6, 3, 6, 10, 14, 10),
            Block.box(3, 14, 3, 13, 16, 13));

    public KennelPostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<KennelPostBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        // No collision: a recalled pet must be able to path to its post, and mobs shouldn't snag on it.
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        // Passable to pathfinding, matching the empty collision — a recalled pet paths right onto its
        // post rather than treating the marker as a wall it must stop short of.
        return true;
    }
}
