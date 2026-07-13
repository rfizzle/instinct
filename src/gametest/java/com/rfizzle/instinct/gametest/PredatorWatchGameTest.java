package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.predatorwatch.PredatorWatchGoal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * SPEC §8 Predator Watch: a tamed pet on Stay near livestock deters a hunting predator (clearing its
 * livestock target) and stands to intercept it, re-sitting once it is gone — plus the inert paths
 * (feature off, no pasture to guard) and load idempotency. Every test lays its own two-layer stone
 * floor (y=0..1) and works on the y=2 surface. The wild predator is spawned with {@code NoAI} so its
 * target selector never re-acquires the prey mid-assertion — the guardian's clear is what the test
 * observes, not a race with vanilla re-targeting.
 */
public class PredatorWatchGameTest implements FabricGameTest {

    private static final int SIZE = 8;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctPredatorWatch")
    public void guardianClearsAHuntingPredatorsTarget(GameTestHelper helper) {
        buildFloor(helper);
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(3, 2, 2));
        Wolf predator = spawnWildWolf(helper, new BlockPos(5, 2, 5));
        predator.setTarget(sheep);
        Wolf guardian = spawnGuardianWolf(helper, new BlockPos(2, 2, 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(guardian.getOwnerUUID() != null, "precondition: guardian is tamed");
            helper.assertTrue(predator.getTarget() == null,
                    "the guardian should clear the predator's hunt on the sheep");
            predator.discard();
            guardian.discard();
            sheep.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctPredatorWatch")
    public void guardianStandsToInterceptThenReSits(GameTestHelper helper) {
        buildFloor(helper);
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(3, 2, 2));
        Wolf predator = spawnWildWolf(helper, new BlockPos(5, 2, 5));
        predator.setTarget(sheep);
        Wolf guardian = spawnGuardianWolf(helper, new BlockPos(2, 2, 2));
        // Give the watch a moment to engage, confirm the pet left its seat to guard while the Stay
        // order itself is never cleared, then remove the threat and confirm it re-sits on its own —
        // stay means stay, minus the predator.
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(guardian.isInSittingPose(), "guardian stands to intercept the predator");
            helper.assertTrue(guardian.isOrderedToSit(), "the watch never clears the Stay order");
            predator.discard();
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(guardian.isInSittingPose(), "guardian re-sits once the predator is gone");
                helper.assertTrue(guardian.isOrderedToSit(), "the Stay order still holds");
                guardian.discard();
                sheep.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctPredatorWatch")
    public void followCommandEndsTheWatch(GameTestHelper helper) {
        buildFloor(helper);
        // A real online owner: vanilla SitWhenOrderedToGoal auto-sits a tamed pet whose owner is
        // absent (getOwner() == null), so an ownerless pet would re-sit regardless of the order —
        // the meaningful test needs the pet's owner present, as it is when a player whistles Follow.
        // The owner is discarded inside the deferred callback, not a synchronous finally: it must
        // stay present across the delay (an absent owner would auto-sit the pet). A synchronous setup
        // failure still discards it via the catch.
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(3, 2, 2));
            Wolf predator = spawnWildWolf(helper, new BlockPos(5, 2, 5));
            predator.setTarget(sheep);
            Wolf guardian = spawnGuardianWolf(helper, new BlockPos(2, 2, 2));
            guardian.setOwnerUUID(owner.getUUID());
            // Let the watch engage (the pet leaves its seat), then whistle the pet to Follow mid-guard.
            // The command must be honored even with the predator still there — the pet stops guarding
            // and is never forced back into its seat.
            helper.runAfterDelay(60, () -> {
                helper.assertFalse(guardian.isInSittingPose(), "precondition: guardian engaged and stood");
                guardian.setOrderedToSit(false); // the whistle's Follow order
                helper.runAfterDelay(60, () -> {
                    helper.assertFalse(guardian.isOrderedToSit(), "the Follow command is honored, not swallowed");
                    helper.assertFalse(guardian.isInSittingPose(), "a followed pet is never re-sat against the command");
                    predator.discard();
                    guardian.discard();
                    sheep.discard();
                    owner.discard();
                    helper.succeed();
                });
            });
        } catch (RuntimeException e) {
            owner.discard();
            throw e;
        }
    }

    // Own batch: a live fuse must never sit within another test's berth awareness radius.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctPredatorWatchCreeper")
    public void swellingCreeperBeatsTheWatch(GameTestHelper helper) {
        buildFloor(helper);
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(3, 2, 2));
        Wolf predator = spawnWildWolf(helper, new BlockPos(5, 2, 5));
        predator.setTarget(sheep);
        Wolf guardian = spawnGuardianWolf(helper, new BlockPos(2, 2, 2));
        Creeper creeper = spawnFuseOnlyCreeper(helper, new BlockPos(2, 2, 5));
        var creeperPos = creeper.position();
        creeper.ignite();
        // Self-preservation trumps the watch: with a predator right there, the guardian still breaks
        // for the fuse instead of guarding, and clears the berth distance.
        helper.succeedWhen(() -> {
            helper.assertTrue(guardian.position().distanceTo(creeperPos) >= 4.0,
                    "guardian flees the fuse rather than holding the watch");
            guardian.discard();
            predator.discard();
            sheep.discard();
            creeper.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "instinctPredatorWatchOff")
    public void disabledConfigLeavesThePredatorHunting(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enablePredatorWatch;
        InstinctConfig.get().enablePredatorWatch = false;
        try {
            buildFloor(helper);
            Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(3, 2, 2));
            Wolf predator = spawnWildWolf(helper, new BlockPos(5, 2, 5));
            predator.setTarget(sheep);
            Wolf guardian = spawnGuardianWolf(helper, new BlockPos(2, 2, 2));
            helper.runAfterDelay(60, () -> {
                try {
                    helper.assertTrue(guardian.isInSittingPose(), "a disabled guardian never leaves its seat");
                    helper.assertTrue(predator.getTarget() == sheep, "a disabled guardian never clears the hunt");
                } finally {
                    InstinctConfig.get().enablePredatorWatch = saved;
                }
                predator.discard();
                guardian.discard();
                sheep.discard();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enablePredatorWatch = saved;
            throw e;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "instinctPredatorWatchNoStock")
    public void noLivestockMeansNoWatch(GameTestHelper helper) {
        buildFloor(helper);
        // A predator by a lone stationed pet, no pasture in sight: nothing to guard, so the guardian
        // stays seated and the predator is left alone.
        Wolf predator = spawnWildWolf(helper, new BlockPos(5, 2, 5));
        Wolf guardian = spawnGuardianWolf(helper, new BlockPos(2, 2, 2));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(guardian.isInSittingPose(), "with no livestock near, the guardian keeps its seat");
            predator.discard();
            guardian.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reloadNeverStacksASecondWatchGoal(GameTestHelper helper) {
        buildFloor(helper);
        Wolf guardian = spawnGuardianWolf(helper, new BlockPos(3, 2, 3));
        helper.assertValueEqual(countWatchGoals(guardian), 1L, "watch goals after first load");
        // A chunk re-load re-fires ENTITY_LOAD on the same entity; simulate it directly.
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(guardian, helper.getLevel());
        helper.assertValueEqual(countWatchGoals(guardian), 1L, "watch goals after a simulated re-load");
        guardian.discard();
        helper.succeed();
    }

    private static long countWatchGoals(Wolf wolf) {
        return wolf.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal() instanceof PredatorWatchGoal)
                .count();
    }

    /** Two-layer stone floor filling the region at y=0..1; mobs walk on the y=2 surface. */
    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
    }

    /** A tamed wolf, ordered to sit (Stay), tamed before {@code addFreshEntity} fires ENTITY_LOAD —
     *  the production path for a stationed guardian loading in. */
    private static Wolf spawnGuardianWolf(GameTestHelper helper, BlockPos rel) {
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        if (wolf == null) {
            throw new IllegalStateException("could not create a wolf");
        }
        BlockPos abs = helper.absolutePos(rel);
        wolf.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        wolf.setOrderedToSit(true);
        helper.getLevel().addFreshEntity(wolf);
        return wolf;
    }

    /** An untamed wolf with its AI off so it stays put and never re-acquires a target the guardian
     *  clears — a stable stand-in for a wild predator stalking the pasture. */
    private static Wolf spawnWildWolf(GameTestHelper helper, BlockPos rel) {
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        if (wolf == null) {
            throw new IllegalStateException("could not create a wolf");
        }
        BlockPos abs = helper.absolutePos(rel);
        wolf.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(wolf);
        wolf.setNoAi(true);
        return wolf;
    }

    /** A creeper that can fuse but not hurt the test: NoAI keeps it in place, ExplosionRadius 0 makes
     *  any detonation harmless, and a 400-tick fuse outlives the timeout. Tests must discard it. */
    private static Creeper spawnFuseOnlyCreeper(GameTestHelper helper, BlockPos rel) {
        Creeper creeper = helper.spawn(EntityType.CREEPER, rel);
        creeper.setNoAi(true);
        CompoundTag tag = creeper.saveWithoutId(new CompoundTag());
        tag.putByte("ExplosionRadius", (byte) 0);
        tag.putShort("Fuse", (short) 400);
        creeper.load(tag);
        return creeper;
    }
}
