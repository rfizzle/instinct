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
    public void tamedSpawnIsTamedAndOwned(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        UUID owner = UUID.randomUUID();

        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(2, 2, 2), owner);
        try {
            helper.assertTrue(wolf.isTame(), "a shared-helper spawn should be tamed");
            helper.assertTrue(owner.equals(wolf.getOwnerUUID()), "the requested owner should be set");
            helper.succeed();
        } finally {
            wolf.discard();
        }
    }

    /**
     * The chunk forcing is only observable away from the structure: a spawn inside it sits in a
     * chunk the framework already forced for the batch, so asserting against one would hold
     * whether or not the helper did anything. This pins the load-bearing step itself — dropping
     * the {@code setChunkForced} call fails here and nowhere else.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void prepareSpawnForcesAColdChunk(GameTestHelper helper) {
        // Searched rather than hardcoded: suites share one grid, so a fixed offset can land in a
        // chunk a neighbouring slot has already forced — the very coupling #59 was about. Walking
        // out until the chunk is genuinely unforced is what keeps the assertion below meaningful.
        BlockPos rel = null;
        ChunkPos chunk = null;
        for (int dx = 16; dx <= 4096 && rel == null; dx += 16) {
            BlockPos candidate = new BlockPos(dx, 2, 0);
            ChunkPos candidateChunk = new ChunkPos(helper.absolutePos(candidate));
            if (!helper.getLevel().getForcedChunks().contains(candidateChunk.toLong())) {
                rel = candidate;
                chunk = candidateChunk;
            }
        }
        helper.assertTrue(rel != null,
                "precondition: no unforced chunk within 4096 blocks, so this test would prove nothing");

        PetSpawns.prepareSpawn(helper, rel);

        helper.assertTrue(helper.getLevel().getForcedChunks().contains(chunk.toLong()),
                "preparing a spawn should force the chunk it lands in");
        helper.assertBlockPresent(Blocks.SMOOTH_STONE, rel.below());
        helper.succeed();
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
