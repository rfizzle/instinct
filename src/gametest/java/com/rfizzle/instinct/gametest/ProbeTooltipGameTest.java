package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import com.rfizzle.instinct.compat.common.AnimalProbeTooltip;
import com.rfizzle.instinct.compat.common.TroughProbeTooltip;
import com.rfizzle.instinct.data.DownedData;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.registry.InstinctBlocks;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * SPEC §Compat, Jade/WTHIT: the shared server-data writer wiring. The Jade/WTHIT adapters are pure
 * delegation, so these drive the writer directly on live game objects (the way the skill's testing
 * section prescribes) and assert through the same formatter both viewers use — no viewer jar needed.
 */
public class ProbeTooltipGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void animalWriterShowsGradeAndPerk(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        cow.setAttached(InstinctAttachments.GENETICS, new GeneticsData(Grade.PRIME.level(), Perk.HARDY, false, 0L));

        CompoundTag tag = new CompoundTag();
        AnimalProbeTooltip.writeServerData(tag, cow);
        helper.assertTrue(tag.getBoolean(AnimalProbeTooltip.KEY_PRESENT), "a graded livestock animal writes a tooltip");
        helper.assertValueEqual(tag.getString(AnimalProbeTooltip.KEY_GRADE), "prime", "the grade is written");
        helper.assertValueEqual(tag.getString(AnimalProbeTooltip.KEY_PERK), "hardy", "the perk is written");
        helper.assertTrue(!AnimalProbeTooltip.buildLines(tag).isEmpty(), "the formatter renders a line");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void animalWriterShowsVeterancy(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 2, 2));
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        VeterancyHandler.setAccruedDays(wolf, 30.0); // rank 2 at default thresholds (10/30/60)

        CompoundTag tag = new CompoundTag();
        AnimalProbeTooltip.writeServerData(tag, wolf);
        helper.assertTrue(tag.getBoolean(AnimalProbeTooltip.KEY_PRESENT), "a tamed pet writes a tooltip");
        helper.assertValueEqual(tag.getLong(AnimalProbeTooltip.KEY_VET_DAYS), 30L, "accrued days are written");
        helper.assertTrue(tag.getInt(AnimalProbeTooltip.KEY_VET_RANK) >= 2, "the derived rank is written");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void animalWriterShowsDowned(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 2, 2));
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        wolf.setAttached(InstinctAttachments.DOWNED, new DownedData());

        CompoundTag tag = new CompoundTag();
        AnimalProbeTooltip.writeServerData(tag, wolf);
        helper.assertTrue(tag.getBoolean(AnimalProbeTooltip.KEY_DOWNED), "a downed pet writes the downed flag");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void uncoveredOrdinaryAnimalWritesNothing(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));

        CompoundTag tag = new CompoundTag();
        AnimalProbeTooltip.writeServerData(tag, cow);
        helper.assertFalse(tag.getBoolean(AnimalProbeTooltip.KEY_PRESENT),
                "an ordinary, untamed animal writes no presence flag");
        helper.assertTrue(AnimalProbeTooltip.buildLines(tag).isEmpty(), "and the formatter is empty");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void troughWriterShowsStoredAndPopulation(GameTestHelper helper) {
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        BlockPos rel = new BlockPos(3, 2, 3);
        helper.setBlock(rel, InstinctBlocks.FEEDING_TROUGH.defaultBlockState());
        FeedingTroughBlockEntity trough = (FeedingTroughBlockEntity) helper.getBlockEntity(rel);
        trough.insertFood(new ItemStack(Items.WHEAT, 20));
        helper.spawn(EntityType.COW, new BlockPos(2, 2, 3));
        helper.spawn(EntityType.COW, new BlockPos(4, 2, 3));

        CompoundTag tag = new CompoundTag();
        TroughProbeTooltip.writeServerData(tag, helper.getLevel(), helper.absolutePos(rel), trough);
        helper.assertTrue(tag.getBoolean(TroughProbeTooltip.KEY_PRESENT), "the trough writes a tooltip");
        helper.assertValueEqual(tag.getString(TroughProbeTooltip.KEY_ITEM), "minecraft:wheat", "the stored item is written");
        helper.assertValueEqual(tag.getInt(TroughProbeTooltip.KEY_COUNT), 20, "the stored count is written");
        helper.assertValueEqual(tag.getInt(TroughProbeTooltip.KEY_POPULATION), 2, "both nearby cows count toward the population");
        helper.assertValueEqual(TroughProbeTooltip.buildLines(tag).size(), 2, "stored line plus population line");
        helper.succeed();
    }
}
