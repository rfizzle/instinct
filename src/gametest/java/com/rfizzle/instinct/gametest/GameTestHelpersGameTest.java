package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.gametest.util.PetSpawns;
import com.rfizzle.instinct.gametest.util.TestFloors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Faithfulness guard for the shared gametest helpers, the sibling of {@link MockPlayersGameTest}.
 * Every suite rests on {@link PetSpawns} and {@link TestFloors}, so a regression in either would
 * otherwise surface as unexplained flakes scattered across unrelated suites rather than as a
 * failure here.
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
        BlockPos rel = coldChunkPos(helper);
        ChunkPos chunk = new ChunkPos(helper.absolutePos(rel));

        PetSpawns.prepareSpawn(helper, rel);
        // Released here rather than left to batch teardown: a test whose whole subject is
        // cross-test chunk coupling should not itself leave forced chunks in the shared grid.
        try {
            helper.assertTrue(helper.getLevel().getForcedChunks().contains(chunk.toLong()),
                    "preparing a spawn should force the chunk it lands in");
            helper.assertBlockPresent(Blocks.SMOOTH_STONE, rel.below());
            helper.succeed();
        } finally {
            helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        }
    }

    /**
     * Regression guard: {@code GameTestHelper#spawn} marks a mob persistence-required, so the
     * shared primitive that replaced it at every call site has to as well. Without it vanilla
     * despawns a mob whose nearest player is beyond its category distance, and a test holding one
     * across ticks sees it vanish for no visible reason.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void spawnedMobsArePersistenceRequired(GameTestHelper helper) {
        TestFloors.buildFloor(helper);

        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(2, 2, 2));
        Creeper creeper = PetSpawns.spawnFuseOnlyCreeper(helper, new BlockPos(5, 2, 5));
        try {
            helper.assertTrue(wolf.isPersistenceRequired(),
                    "a shared-helper pet should be persistence-required, as helper.spawn made it");
            helper.assertTrue(creeper.isPersistenceRequired(),
                    "a shared-helper creeper should be persistence-required");
            helper.succeed();
        } finally {
            wolf.discard();
            creeper.discard();
        }
    }

    /**
     * A spawn whose check fails is already in the level but never reaches the caller, so the
     * helper is the only place left that can clean it up. Driven through the injectable check
     * rather than a genuinely cold chunk: the real check fails only for a chunk the server has
     * never loaded, and which chunks are still cold depends on what the rest of the batch touched
     * first, so that guard would go quiet on exactly the runs it is meant to catch.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void aSpawnFailingItsCheckIsDiscardedBeforeTheThrowReachesTheCaller(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        // The load hook is the only handle on an entity the caller never receives.
        AtomicReference<Wolf> escaped = new AtomicReference<>();
        boolean threw = false;

        try {
            PetSpawns.spawnAt(helper, EntityType.WOLF, new BlockPos(2, 2, 2), escaped::set,
                    (h, entity) -> {
                        throw new IllegalStateException("forced verification failure");
                    });
        } catch (IllegalStateException expected) {
            threw = true;
        }

        helper.assertTrue(threw, "a failed check should reach the caller rather than be swallowed");
        Wolf wolf = escaped.get();
        helper.assertTrue(wolf != null, "the load hook should have run before the check");
        helper.assertTrue(wolf.isRemoved(), "a spawn that fails its check should be discarded");
        helper.assertTrue(helper.getLevel().getEntity(wolf.getId()) == null,
                "a discarded spawn should be gone from the lookup it failed to reach");
        helper.succeed();
    }

    /**
     * A relative position in a chunk that is neither forced nor resident. Searched rather than
     * hardcoded: suites share one grid, so a fixed offset can land in a chunk a neighbouring slot
     * has already forced — the very coupling #59 was about. Residency is the stricter half of the
     * search and the one worth having: forcing is a bookkeeping entry a test can add and drop,
     * while whether the chunk is loaded is what a claim about a cold chunk actually turns on, and
     * a batch can leave a chunk loaded without ever having forced it.
     */
    private static BlockPos coldChunkPos(GameTestHelper helper) {
        for (int dx = 16; dx <= 4096; dx += 16) {
            BlockPos candidate = new BlockPos(dx, 2, 0);
            ChunkPos chunk = new ChunkPos(helper.absolutePos(candidate));
            if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())
                    && !helper.getLevel().getChunkSource().hasChunk(chunk.x, chunk.z)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "no cold chunk within 4096 blocks, so this test would prove nothing");
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
