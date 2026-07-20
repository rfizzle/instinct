package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.TestFloors;
import com.rfizzle.instinct.registry.InstinctBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * SPEC §5 Feeding Trough: the block's storage/hopper/comparator behavior and the passive feeding
 * loop. The loop is exercised through the real block-entity server ticker; cows are pinned with
 * {@code setNoAi} beside the trough so feeding is deterministic (they are already within the arrival
 * radius). Config-mutating tests run in their own batch and restore in a finally.
 */
public class FeedingTroughGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void troughFeedsAdultsIntoLoveAndTheyBreed(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        FeedingTroughBlockEntity trough = placeFilledTrough(helper, new BlockPos(3, 2, 3),
                new ItemStack(Items.WHEAT, 64));
        Cow a = pinnedCow(helper, new BlockPos(2, 2, 3));
        Cow b = pinnedCow(helper, new BlockPos(4, 2, 3));

        helper.runAfterDelay(180, () -> {
            helper.assertTrue(a.isInLove() && b.isInLove(),
                    "the trough fed both adult cows into love within the interval budget");
            helper.assertTrue(trough.getStored().getCount() <= 62, "it consumed one item per cow");
            var calf = GeneticsGameTest.breed(helper, a, b);
            helper.assertTrue(calf != null, "the two in-love cows breed a calf");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctTroughCap")
    public void populationCapBlocksNewLoveStates(GameTestHelper helper) {
        // The config must hold across the feeding window, so it is restored inside the delayed
        // callback — not in a finally, which would run the instant the callback is scheduled.
        int saved = InstinctConfig.get().troughPopulationCap;
        InstinctConfig.get().troughPopulationCap = 2;
        TestFloors.buildFloor(helper);
        placeFilledTrough(helper, new BlockPos(3, 2, 3), new ItemStack(Items.WHEAT, 64));
        Cow a = pinnedCow(helper, new BlockPos(2, 2, 3));
        Cow b = pinnedCow(helper, new BlockPos(4, 2, 3));
        Cow c = pinnedCow(helper, new BlockPos(3, 2, 2));
        helper.runAfterDelay(150, () -> {
            InstinctConfig.get().troughPopulationCap = saved;
            helper.assertFalse(a.isInLove() || b.isInLove() || c.isInLove(),
                    "3 cows over a cap of 2 → the trough starts no new love states");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void hopperFillsTheTroughButCannotExtract(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        FeedingTroughBlockEntity trough = placeFilledTrough(helper, new BlockPos(3, 2, 3),
                new ItemStack(Items.WHEAT, 10));

        // A hopper below the trough must never pull the stock out.
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.HOPPER.defaultBlockState());
        // A hopper above, holding wheat, pushes down into the trough.
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.HOPPER.defaultBlockState());
        HopperBlockEntity above = (HopperBlockEntity) helper.getBlockEntity(new BlockPos(3, 3, 3));
        above.setItem(0, new ItemStack(Items.WHEAT, 5));

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(trough.getStored().getCount() > 10,
                    "the hopper above fed wheat into the trough");
            HopperBlockEntity below = (HopperBlockEntity) helper.getBlockEntity(new BlockPos(3, 1, 3));
            helper.assertTrue(below.isEmpty(), "the hopper below never extracted from the trough");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void storageAcceptsWithdrawsAndConvertsHay(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        FeedingTroughBlockEntity trough = placeFilledTrough(helper, new BlockPos(3, 2, 3), ItemStack.EMPTY);

        helper.assertValueEqual(trough.insertHay(), 9, "an empty trough converts a hay bale to 9 wheat");
        helper.assertTrue(trough.getStored().is(Items.WHEAT) && trough.getStored().getCount() == 9,
                "the trough now holds 9 wheat");
        helper.assertValueEqual(trough.insertFood(new ItemStack(Items.CARROT, 4)), 0,
                "a mismatched food type is refused");
        helper.assertValueEqual(trough.insertFood(new ItemStack(Items.WHEAT, 8)), 8,
                "more wheat is accepted");

        ItemStack out = trough.withdrawAll();
        helper.assertTrue(out.is(Items.WHEAT) && out.getCount() == 17, "withdraw returns the whole stock");
        helper.assertTrue(trough.isEmpty(), "the trough is emptied");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void storedStackSyncsToClients(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        BlockPos rel = new BlockPos(3, 2, 3);
        FeedingTroughBlockEntity trough = placeFilledTrough(helper, rel, new ItemStack(Items.WHEAT, 20));

        helper.assertTrue(trough.getUpdatePacket() != null, "the trough sends a block-entity update packet");
        var registries = helper.getLevel().registryAccess();
        // A fresh block entity fed the update tag is the client's view: it must carry the stored stack.
        FeedingTroughBlockEntity clientView =
                new FeedingTroughBlockEntity(rel, InstinctBlocks.FEEDING_TROUGH.defaultBlockState());
        clientView.loadWithComponents(trough.getUpdateTag(registries), registries);
        helper.assertTrue(clientView.getStored().is(Items.WHEAT) && clientView.getStored().getCount() == 20,
                "the synced update tag carries the stored stack to the client");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void comparatorReadsFillLevel(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        BlockPos rel = new BlockPos(3, 2, 3);
        FeedingTroughBlockEntity trough = placeFilledTrough(helper, rel, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(rel);

        helper.assertValueEqual(signal(helper, abs), 0, "an empty trough reads 0");
        trough.insertFood(new ItemStack(Items.WHEAT, 64));
        helper.assertValueEqual(signal(helper, abs), 15, "a full trough reads 15");
        trough.withdrawAll();
        trough.insertFood(new ItemStack(Items.WHEAT, 32));
        helper.assertValueEqual(signal(helper, abs), 8, "a half-full trough reads mid-range");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void breakingDropsTheStoredStack(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        BlockPos rel = new BlockPos(3, 2, 3);
        placeFilledTrough(helper, rel, new ItemStack(Items.WHEAT, 40));
        BlockPos abs = helper.absolutePos(rel);
        helper.getLevel().destroyBlock(abs, true);

        int wheat = 0;
        boolean trough = false;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(4.0))) {
            if (item.getItem().is(Items.WHEAT)) wheat += item.getItem().getCount();
            if (item.getItem().is(InstinctBlocks.FEEDING_TROUGH.asItem())) trough = true;
        }
        helper.assertValueEqual(wheat, 40, "the stored stack drops on break");
        helper.assertTrue(trough, "the trough itself drops on break");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blockIsNotWaterloggableRenderedAndFlammable(GameTestHelper helper) {
        BlockState state = InstinctBlocks.FEEDING_TROUGH.defaultBlockState();
        helper.assertFalse(state.hasProperty(BlockStateProperties.WATERLOGGED), "the trough is not waterloggable");
        helper.assertTrue(state.getRenderShape() == RenderShape.MODEL, "it uses a static baked model");
        FlammableBlockRegistry.Entry entry = FlammableBlockRegistry.getDefaultInstance()
                .get(InstinctBlocks.FEEDING_TROUGH);
        helper.assertTrue(entry.getBurnChance() > 0, "the trough burns like planks");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctTroughOff")
    public void disabledTroughKeepsStorageButStopsFeeding(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableTrough;
        InstinctConfig.get().enableTrough = false;
        TestFloors.buildFloor(helper);
        BlockPos rel = new BlockPos(3, 2, 3);
        FeedingTroughBlockEntity trough = placeFilledTrough(helper, rel, new ItemStack(Items.WHEAT, 64));
        Cow a = pinnedCow(helper, new BlockPos(2, 2, 3));

        // Storage and comparator still work with the loop disabled.
        helper.assertValueEqual(signal(helper, helper.absolutePos(rel)), 15, "comparator still reads fill");
        helper.runAfterDelay(150, () -> {
            InstinctConfig.get().enableTrough = saved;
            helper.assertFalse(a.isInLove(), "no feeding while the trough is disabled");
            helper.assertValueEqual(trough.getStored().getCount(), 64, "and no food is consumed");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void troughGrowsABabyWithoutBreedingIt(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        placeFilledTrough(helper, new BlockPos(3, 2, 3), new ItemStack(Items.WHEAT, 64));
        Cow calf = pinnedCow(helper, new BlockPos(2, 2, 3));
        calf.setBaby(true);
        int startAge = calf.getAge();

        helper.runAfterDelay(180, () -> {
            helper.assertTrue(calf.getAge() > startAge, "the trough accelerated the baby's growth");
            helper.assertFalse(calf.isInLove(), "a baby never enters love");
            helper.succeed();
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private static int signal(GameTestHelper helper, BlockPos abs) {
        return helper.getLevel().getBlockState(abs).getAnalogOutputSignal(helper.getLevel(), abs);
    }

    private static FeedingTroughBlockEntity placeFilledTrough(GameTestHelper helper, BlockPos rel, ItemStack fill) {
        helper.setBlock(rel, InstinctBlocks.FEEDING_TROUGH.defaultBlockState());
        FeedingTroughBlockEntity trough = (FeedingTroughBlockEntity) helper.getBlockEntity(rel);
        if (!fill.isEmpty()) {
            trough.insertFood(fill);
        }
        return trough;
    }

    /** A tamed-adult-agnostic cow pinned in place beside the trough so feeding is deterministic. */
    private static Cow pinnedCow(GameTestHelper helper, BlockPos rel) {
        Cow cow = helper.spawn(EntityType.COW, rel);
        cow.setNoAi(true);
        cow.setOnGround(true);
        return cow;
    }
}
