package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.gametest.util.PetSpawns;
import com.rfizzle.instinct.gametest.util.TestFloors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Faithfulness guard for the shared gametest helpers, the sibling of {@link MockPlayersGameTest}.
 * Every suite now rests on {@link PetSpawns} and {@link TestFloors}, so a regression in either
 * would otherwise surface as unexplained flakes scattered across unrelated suites rather than as
 * a failure here.
 */
public class GameTestHelpersGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void floorSpansTheRequestedExtentAndNoMore(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 4, 3);

        helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(0, 0, 0));
        helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(3, 0, 2));
        helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(3, 1, 2));
        // Both layers, so a mob clipping a corner still lands on stone.
        helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(0, 1, 0));
        // One past each requested edge stays air — a floor that silently over-ran would mask a
        // test spawning outside the extent it asked for.
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 0, 0));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 0, 3));
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void defaultFloorFillsTheEmptyStructure(GameTestHelper helper) {
        TestFloors.buildFloor(helper);

        helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(0, 0, 0));
        helper.assertBlockPresent(Blocks.SMOOTH_STONE,
                new BlockPos(TestFloors.DEFAULT_SIZE - 1, 1, TestFloors.DEFAULT_SIZE - 1));
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tamedSpawnIsOwnedForcedAndEnumerable(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        UUID owner = UUID.randomUUID();
        BlockPos rel = new BlockPos(2, 2, 2);

        Wolf wolf = PetSpawns.spawnTamedWolf(helper, rel, owner);
        try {
            helper.assertTrue(wolf.isTame(), "a shared-helper spawn should be tamed");
            helper.assertTrue(owner.equals(wolf.getOwnerUUID()), "the requested owner should be set");
            // The contract the locate census rests on, asserted directly rather than by proxy.
            helper.assertTrue(helper.getLevel().getEntity(wolf.getId()) != null,
                    "a shared-helper spawn should be in the level's entity lookup");
            ChunkPos chunk = new ChunkPos(helper.absolutePos(rel));
            helper.assertTrue(helper.getLevel().getForcedChunks().contains(chunk.toLong()),
                    "a shared-helper spawn should force its own chunk");
            helper.succeed();
        } finally {
            wolf.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fuseOnlyCreeperCannotDetonateOrExpireMidTest(GameTestHelper helper) {
        TestFloors.buildFloor(helper);

        Creeper creeper = PetSpawns.spawnFuseOnlyCreeper(helper, new BlockPos(2, 2, 2));
        try {
            helper.assertTrue(creeper.isNoAi(), "a fuse-only creeper should not path");
            // Both are private with no accessor, so read them back off the state the helper wrote.
            CompoundTag tag = creeper.saveWithoutId(new CompoundTag());
            byte radius = tag.getByte("ExplosionRadius");
            short fuse = tag.getShort("Fuse");
            helper.assertTrue(radius == 0,
                    "a fuse-only creeper should detonate harmlessly, found radius " + radius);
            // The fuse must outlive the default 100-tick test timeout, or a berth goal under test
            // would be stopped by the detonation rather than by the behavior being asserted.
            helper.assertTrue(fuse > 100,
                    "a fuse-only creeper's fuse should outlive the test timeout, found " + fuse);
            helper.succeed();
        } finally {
            creeper.discard();
        }
    }
}
