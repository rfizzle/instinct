package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.gametest.util.PetSpawns;
import com.rfizzle.instinct.gametest.util.TestFloors;
import com.rfizzle.instinct.selfpreservation.CreeperBerthGoal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * SPEC §1 Pet Self-Preservation: hazard-aware pathing (enter blocked, escape allowed), the
 * creeper berth with re-sit and attack break-off, teleport refusal, load idempotency, and the
 * config-disabled vanilla path. Structure region is Fabric's 8x8x8 empty template; every test
 * lays its own two-layer stone floor (y=0..1) and works on the y=2 surface. The config-mutating
 * test gets its own {@code batch} and restores the flag in {@code finally}, mirroring
 * {@link CoverageGameTest}.
 */
public class SelfPreservationGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void tamedWolfPathsAroundLava(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        // Lava strip across z=3 at x=0..5, carved into the walking layer; the x=6..7 gap is the
        // only safe route between the two sides.
        for (int x = 0; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.LAVA.defaultBlockState());
        }
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(1, 2, 1));
        // NoAI keeps the wolf at its spawn cell so the computed path, not wandering, is what the
        // test observes. NoAI also skips travel(), so gravity never raises the onGround flag that
        // GroundPathNavigation#canUpdatePath requires — force it; nothing recomputes it while AI
        // is off.
        wolf.setNoAi(true);
        wolf.setOnGround(true);
        BlockPos target = helper.absolutePos(new BlockPos(1, 2, 6));
        helper.succeedWhen(() -> {
            Path path = wolf.getNavigation().createPath(target, 0);
            helper.assertTrue(path != null && path.canReach(),
                    "wolf should find a safe route around the lava strip");
            for (int i = 0; i < path.getNodeCount(); i++) {
                var node = path.getNode(i);
                BlockPos rel = new BlockPos(node.x, node.y, node.z).subtract(helper.absolutePos(BlockPos.ZERO));
                helper.assertFalse(rel.getZ() == 3 && rel.getX() >= 0 && rel.getX() <= 5,
                        "path must not cross the lava strip (node at relative " + rel + ")");
            }
            wolf.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tamedWolfPathsAroundFire(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        // Fire strip on the walking surface at z=3, x=0..5; the x=6..7 gap is the only safe
        // route. Mirrors tamedWolfPathsAroundLava for the DAMAGE_FIRE malus.
        for (int x = 0; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.FIRE.defaultBlockState());
        }
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(1, 2, 1));
        wolf.setNoAi(true);
        wolf.setOnGround(true);
        BlockPos target = helper.absolutePos(new BlockPos(1, 2, 6));
        helper.succeedWhen(() -> {
            Path path = wolf.getNavigation().createPath(target, 0);
            helper.assertTrue(path != null && path.canReach(),
                    "wolf should find a safe route around the fire strip");
            for (int i = 0; i < path.getNodeCount(); i++) {
                var node = path.getNode(i);
                BlockPos rel = new BlockPos(node.x, node.y, node.z).subtract(helper.absolutePos(BlockPos.ZERO));
                helper.assertFalse(rel.getZ() == 3 && rel.getX() >= 0 && rel.getX() <= 5,
                        "path must not cross the fire strip (node at relative " + rel + ")");
            }
            wolf.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void wolfStandingInHazardMayEscape(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.FIRE.defaultBlockState());
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        // NoAI: the wolf must still be standing in the fire when the path is computed —
        // panicking out of it would test nothing. NoAI also skips travel(), so force the
        // onGround flag that GroundPathNavigation#canUpdatePath requires.
        wolf.setNoAi(true);
        wolf.setOnGround(true);
        BlockPos firePos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos target = helper.absolutePos(new BlockPos(6, 2, 6));
        helper.succeedWhen(() -> {
            helper.assertTrue(wolf.blockPosition().equals(firePos), "wolf should still stand in the fire");
            Path path = wolf.getNavigation().createPath(target, 0);
            helper.assertTrue(path != null && path.canReach(),
                    "escaping the hazard node the wolf stands in must be permitted");
            wolf.discard();
        });
    }

    // Own batch: vanilla runs a batch's tests concurrently in adjacent structures, and a live
    // fuse must never be within another test's berth awareness radius.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctBerth1")
    public void sittingWolfStepsClearOfSwellingCreeperAndResits(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(2, 2, 2));
        wolf.setOrderedToSit(true);
        Creeper creeper = PetSpawns.spawnFuseOnlyCreeper(helper, new BlockPos(2, 2, 5));
        Vec3 creeperPos = creeper.position();
        creeper.ignite();
        helper.succeedWhen(() -> {
            helper.assertTrue(wolf.position().distanceTo(creeperPos) >= 4.0,
                    "wolf should be at least creeperBerthBlocks clear of the fuse");
            helper.assertTrue(wolf.isOrderedToSit(), "the stay order should be restored");
            helper.assertTrue(wolf.isInSittingPose(), "wolf should re-sit at its new position");
            wolf.discard();
            creeper.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctBerth2")
    public void attackingWolfBreaksOffDuringFuse(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(1, 2, 1));
        Creeper creeper = PetSpawns.spawnFuseOnlyCreeper(helper, new BlockPos(5, 2, 5));
        wolf.setTarget(creeper);
        creeper.ignite();
        helper.succeedWhen(() -> {
            // Only a break-off while the fuse is live counts — a cleared target after the
            // creeper is gone would be vanilla target invalidation, not this feature.
            helper.assertTrue(creeper.isAlive(), "fuse should still be live");
            helper.assertTrue(wolf.getTarget() == null, "wolf should break off its attack during the fuse");
            wolf.discard();
            creeper.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void teleportRefusedWhileOwnerUnsafe(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3));
            wolf.setOwnerUUID(owner.getUUID());
            // The mock owner spawns at world spawn, far outside the structure — beyond the
            // vanilla 12-block teleport threshold, so the vanilla predicate wants to teleport.
            helper.assertTrue(wolf.distanceToSqr(owner) >= 144.0,
                    "precondition: owner beyond vanilla teleport range");
            helper.assertTrue(wolf.shouldTryTeleportToOwner(),
                    "sanity: vanilla teleports to a safe distant owner");

            owner.fallDistance = 5.0f;
            helper.assertFalse(wolf.shouldTryTeleportToOwner(), "no teleport while the owner is falling");
            owner.fallDistance = 0.0f;
            helper.assertTrue(wolf.shouldTryTeleportToOwner(), "teleport resumes the tick the owner lands");

            owner.startFallFlying();
            helper.assertFalse(wolf.shouldTryTeleportToOwner(), "no teleport while the owner glides");
            owner.stopFallFlying();
            helper.assertTrue(wolf.shouldTryTeleportToOwner(), "teleport resumes when gliding ends");

            wolf.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reloadNeverStacksASecondBerthGoal(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        helper.assertValueEqual(countBerthGoals(wolf), 1L, "berth goals after first load");
        helper.assertTrue(wolf.getPathfindingMalus(PathType.LAVA) < 0.0f, "lava malus applied");
        helper.assertTrue(wolf.getPathfindingMalus(PathType.DAMAGE_FIRE) < 0.0f, "fire malus applied");
        helper.assertTrue(wolf.getPathfindingMalus(PathType.DAMAGE_OTHER) < 0.0f, "cactus malus applied");

        // A chunk re-load re-fires ENTITY_LOAD on the same entity; simulate it directly.
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(wolf, helper.getLevel());
        helper.assertValueEqual(countBerthGoals(wolf), 1L, "berth goals after a simulated re-load");
        wolf.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctSelfPreservation1")
    public void disabledConfigIsExactVanillaBehavior(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableSelfPreservation;
        ServerPlayer owner = null;
        try {
            InstinctConfig.get().enableSelfPreservation = false;
            TestFloors.buildFloor(helper);
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3));
            helper.assertValueEqual(countBerthGoals(wolf), 0L, "no berth goal when disabled");

            owner = MockPlayers.serverPlayerInLevel(helper);
            wolf.setOwnerUUID(owner.getUUID());
            owner.fallDistance = 50.0f;
            helper.assertTrue(wolf.shouldTryTeleportToOwner(),
                    "disabled config keeps vanilla teleport rules even with an unsafe owner");
            wolf.discard();
            helper.succeed();
        } finally {
            InstinctConfig.get().enableSelfPreservation = saved;
            if (owner != null) {
                owner.discard();
            }
        }
    }

    private static long countBerthGoals(Wolf wolf) {
        return wolf.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal() instanceof CreeperBerthGoal)
                .count();
    }
}
