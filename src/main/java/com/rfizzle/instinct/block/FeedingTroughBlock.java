package com.rfizzle.instinct.block;

import com.mojang.serialization.MapCodec;
import com.rfizzle.instinct.registry.InstinctBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The feeding trough ({@code design/SPEC.md} §5): a wooden container block that holds one stack of a
 * single {@code #instinct:trough_food} type. Right-click inserts an accepted stack (a hay bale
 * converts to {@value com.rfizzle.instinct.trough.Trough#HAY_WHEAT_YIELD} wheat) or, empty-handed,
 * withdraws the whole stock; a mismatched item is refused with no swing. All storage, hopper, and
 * comparator behavior lives on {@link FeedingTroughBlockEntity}, which also runs the passive feeding
 * loop; the block is the interaction and redstone shell.
 */
public class FeedingTroughBlock extends BaseEntityBlock {

    public static final MapCodec<FeedingTroughBlock> CODEC = simpleCodec(FeedingTroughBlock::new);

    /** The open-top box outline — a base slab and four walls — matching the baked model. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(0, 4, 0, 16, 16, 2),
            Block.box(0, 4, 14, 16, 16, 16),
            Block.box(0, 4, 2, 2, 16, 14),
            Block.box(14, 4, 2, 16, 16, 14));

    public FeedingTroughBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        // Full-cube collision keeps water flow and pathing simple; the open top is visual only.
        return Shapes.block();
    }

    @Override
    protected MapCodec<FeedingTroughBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeedingTroughBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // a static baked model, not a block-entity renderer
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, InstinctBlockEntities.FEEDING_TROUGH,
                        FeedingTroughBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof FeedingTroughBlockEntity trough)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean hay = stack.is(Items.HAY_BLOCK);
        if (level.isClientSide) {
            // The stored stack is synced to the client, so the no-swing refusal is accurate to the
            // real contents — a same-tag item of the wrong stored type predicts a refusal too.
            boolean accept = hay ? trough.canAcceptHay() : trough.canAccept(stack);
            return accept ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
        }
        int moved = hay ? trough.insertHay() : trough.insertFood(stack);
        if (moved <= 0) {
            return ItemInteractionResult.FAIL; // mismatched or full — no swing, no consume
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(hay ? 1 : moved);
        }
        level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FeedingTroughBlockEntity trough) || trough.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack out = trough.withdrawAll();
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof FeedingTroughBlockEntity trough) {
                Containers.dropContents(level, pos, trough);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
