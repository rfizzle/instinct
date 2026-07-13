package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.herding.FlockingTemptGoal;
import com.rfizzle.instinct.herding.HerdWorkGoal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SPEC §4 Flocking &amp; Herding: the exact-class tempt-goal swap (including a pig's two tempt goals)
 * and its idempotency, the widened flock range vs the disabled-config vanilla range, flock spacing,
 * and the pet drive assist converging a straggler without any cow taking damage. Convergence tests
 * assert outcomes, never choreography, per the issue. Config-mutating tests get their own batch and
 * restore the flag in {@code finally}, mirroring {@link SelfPreservationGameTest}.
 */
public class HerdingGameTest implements FabricGameTest {

    private static final String LANE = "instinct:drive_lane";

    @GameTest(template = EMPTY_STRUCTURE)
    public void livestockGetsFlockingTemptGoal(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        helper.assertValueEqual(countFlocking(cow), 1L, "cow's food tempt goal is swapped for flocking");
        helper.assertValueEqual(countVanillaTempt(cow), 0L, "no exact-class TemptGoal remains");
        cow.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pigSwapsBothTemptGoals(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
        // A pig registers two exact-class TemptGoals (food, carrot-on-a-stick); both must swap.
        helper.assertValueEqual(countFlocking(pig), 2L, "both of the pig's tempt goals are swapped");
        helper.assertValueEqual(countVanillaTempt(pig), 0L, "no exact-class TemptGoal is left behind");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void reloadNeverStacksASecondFlockingGoal(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        helper.assertValueEqual(countFlocking(cow), 1L, "flocking goal after first load");
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(cow, helper.getLevel());
        helper.assertValueEqual(countFlocking(cow), 1L, "no second flocking goal after a simulated re-load");
        cow.discard();
        helper.succeed();
    }

    // Own batch: same-batch tests run concurrently in adjacent structures, and a cow's tempt target
    // is the globally nearest wheat-holder — another test's driver would sit inside the flock range.
    // Serializing these tests keeps each cow's only candidate driver its own. (Cf. the berth batches
    // in SelfPreservationGameTest.)
    @GameTest(template = LANE, timeoutTicks = 200, batch = "instinctFlockRange")
    public void flockTemptsBeyondVanillaRange(GameTestHelper helper) {
        buildFloor(helper, 16, 6);
        // The driver is discarded inside the success callback, not a finally: succeedWhen returns
        // immediately after scheduling its poll, so a finally would remove the driver before the cow
        // ever ticks. (Cf. the creeper tests, which likewise clean up inside the callback.)
        ServerPlayer driver = wheatHolder(helper, new BlockPos(1, 2, 3));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(13, 2, 3)); // 12 blocks away
        helper.succeedWhen(() -> {
            helper.assertTrue(flockingGoal(cow).getTemptedPlayer() == driver,
                    "flocking tempts a cow 12 blocks out (past vanilla's 10-block range)");
            cow.discard();
            driver.discard();
        });
    }

    @GameTest(template = LANE, timeoutTicks = 60, batch = "instinctFlockingOff")
    public void disabledFlockingKeepsVanillaRange(GameTestHelper helper) {
        // Config is restored inside the deferred callback, not a synchronous finally: the flag must
        // stay off across the delay for the assertion to be meaningful, so a finally would restore it
        // before the check ever runs. A synchronous setup failure still restores it via the catch.
        boolean saved = InstinctConfig.get().enableFlocking;
        InstinctConfig.get().enableFlocking = false;
        try {
            buildFloor(helper, 16, 6);
            ServerPlayer driver = wheatHolder(helper, new BlockPos(1, 2, 3));
            Cow cow = helper.spawn(EntityType.COW, new BlockPos(14, 2, 3)); // 13 blocks: outside vanilla's 10
            helper.runAfterDelay(30, () -> {
                try {
                    helper.assertTrue(flockingGoal(cow).getTemptedPlayer() == null,
                            "with flocking off, a cow 13 blocks out is not tempted (vanilla 10-block range)");
                } finally {
                    InstinctConfig.get().enableFlocking = saved;
                }
                cow.discard();
                driver.discard();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enableFlocking = saved;
            throw e;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "instinctSpacing")
    public void temptedCowsHoldSpacing(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        ServerPlayer driver = wheatHolder(helper, new BlockPos(4, 2, 4));
        List<Cow> cows = new ArrayList<>();
        int[][] spots = {{2, 2}, {2, 6}, {6, 2}, {6, 6}, {4, 2}, {4, 6}};
        for (int[] spot : spots) {
            cows.add(helper.spawn(EntityType.COW, new BlockPos(spot[0], 2, spot[1])));
        }
        helper.succeedWhen(() -> {
            double closest = closestPair(cows);
            // Target is flockSpacingBlocks (2.0); a small tolerance absorbs steering/collision jitter.
            // The assertion that matters is that the flock never collapses into a shoving pile.
            helper.assertTrue(closest >= 1.8,
                    "tempted cows keep their spacing (closest pair " + closest + ")");
            cows.forEach(Animal::discard);
            driver.discard();
        });
    }

    @GameTest(template = LANE, timeoutTicks = 600, batch = "instinctDrive")
    public void driveConvergesStragglerWithoutDamage(GameTestHelper helper) {
        buildFloor(helper, 16, 6);
        ServerPlayer driver = wheatHolder(helper, new BlockPos(1, 2, 3));
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 3), driver.getUUID());
        List<Cow> cows = new ArrayList<>();
        int[][] spots = {{3, 2}, {3, 4}, {6, 2}, {6, 4}, {9, 3}, {13, 3}}; // last two are stragglers (>8)
        for (int[] spot : spots) {
            cows.add(helper.spawn(EntityType.COW, new BlockPos(spot[0], 2, spot[1])));
        }
        helper.succeedWhen(() -> {
            for (Cow cow : cows) {
                helper.assertTrue(cow.position().distanceTo(driver.position()) <= 5.0,
                        "every driven cow reaches within 5 blocks of the driver");
                helper.assertTrue(cow.getHealth() == cow.getMaxHealth(),
                        "a pressed cow is never damaged");
            }
            helper.assertFalse(wolf.getTarget() instanceof Cow, "the pressing wolf never targets a cow");
            cows.forEach(Animal::discard);
            wolf.discard();
            driver.discard();
        });
    }

    @GameTest(template = LANE, timeoutTicks = 80, batch = "instinctHerdingNoFlock")
    public void driveAssistInertWhenFlockingOff(GameTestHelper helper) {
        boolean savedFlock = InstinctConfig.get().enableFlocking;
        boolean savedHerd = InstinctConfig.get().enableHerding;
        InstinctConfig.get().enableFlocking = false;
        InstinctConfig.get().enableHerding = true;
        try {
            buildFloor(helper, 16, 6);
            ServerPlayer driver = wheatHolder(helper, new BlockPos(1, 2, 3));
            Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 3), driver.getUUID());
            for (int[] spot : new int[][]{{3, 2}, {3, 4}, {6, 3}, {9, 3}}) {
                helper.spawn(EntityType.COW, new BlockPos(spot[0], 2, spot[1]));
            }
            helper.runAfterDelay(40, () -> {
                try {
                    helper.assertFalse(isHerdWorkRunning(wolf),
                            "drive assist never activates while flocking is off");
                } finally {
                    InstinctConfig.get().enableFlocking = savedFlock;
                    InstinctConfig.get().enableHerding = savedHerd;
                }
                wolf.discard();
                driver.discard();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enableFlocking = savedFlock;
            InstinctConfig.get().enableHerding = savedHerd;
            throw e;
        }
    }

    @GameTest(template = LANE, timeoutTicks = 80, batch = "instinctHerdingOff")
    public void driveAssistInertWhenHerdingOff(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableHerding;
        InstinctConfig.get().enableHerding = false; // flocking stays on: cows flock, but no pet works
        try {
            buildFloor(helper, 16, 6);
            ServerPlayer driver = wheatHolder(helper, new BlockPos(1, 2, 3));
            Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 3), driver.getUUID());
            for (int[] spot : new int[][]{{3, 2}, {3, 4}, {6, 3}, {9, 3}}) {
                helper.spawn(EntityType.COW, new BlockPos(spot[0], 2, spot[1]));
            }
            helper.runAfterDelay(40, () -> {
                try {
                    helper.assertFalse(isHerdWorkRunning(wolf),
                            "drive assist never activates while herding is off");
                } finally {
                    InstinctConfig.get().enableHerding = saved;
                }
                wolf.discard();
                driver.discard();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enableHerding = saved;
            throw e;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void flockingLowersWaterMalusWhileTempted(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 3));
        float defaultMalus = cow.getPathfindingMalus(PathType.WATER);
        ServerPlayer driver = wheatHolder(helper, new BlockPos(3, 2, 3));
        // While the flock goal runs the water cost is zeroed so the cow commits to a river line
        // instead of a shoreline detour; the moment the food is gone the goal stops and restores it,
        // so the swim enablement never leaks past the drive.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cow.getPathfindingMalus(PathType.WATER) == 0.0F,
                        "a flock-tempted cow's water cost is zeroed"))
                .thenExecute(() -> driver.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY))
                .thenWaitUntil(() -> helper.assertTrue(cow.getPathfindingMalus(PathType.WATER) == defaultMalus,
                        "the water cost is restored once the flock goal stops"))
                .thenExecute(() -> {
                    cow.discard();
                    driver.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "instinctNoFlockMalus")
    public void flockingOffLeavesWaterMalusUntouched(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableFlocking;
        InstinctConfig.get().enableFlocking = false;
        try {
            buildFloor(helper, 8, 8);
            Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 3));
            float defaultMalus = cow.getPathfindingMalus(PathType.WATER);
            wheatHolder(helper, new BlockPos(3, 2, 3)); // the vanilla tempt goal still runs on held food
            helper.runAfterDelay(40, () -> {
                try {
                    helper.assertTrue(cow.getPathfindingMalus(PathType.WATER) == defaultMalus,
                            "with flocking off the vanilla tempt goal never lowers the water cost");
                } finally {
                    InstinctConfig.get().enableFlocking = saved;
                }
                cow.discard();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enableFlocking = saved;
            throw e;
        }
    }

    @GameTest(template = LANE, timeoutTicks = 600, batch = "instinctDrive")
    public void driveCrossesWaterToConverge(GameTestHelper helper) {
        buildWaterCrossing(helper);
        ServerPlayer driver = wheatHolder(helper, new BlockPos(13, 2, 3));
        List<Cow> cows = new ArrayList<>();
        for (int[] spot : new int[][]{{2, 2}, {2, 3}, {3, 3}}) {
            cows.add(helper.spawn(EntityType.COW, new BlockPos(spot[0], 2, spot[1])));
        }
        helper.succeedWhen(() -> {
            for (Cow cow : cows) {
                helper.assertTrue(cow.position().distanceTo(driver.position()) <= 5.0,
                        "every driven cow crosses the water channel and reaches the driver");
                helper.assertTrue(cow.getHealth() == cow.getMaxHealth(),
                        "no cow drowns making the crossing");
            }
            cows.forEach(Animal::discard);
            driver.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "instinctFlockToggle")
    public void disablingFlockingMidDriveRestoresWaterMalus(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 3));
        float defaultMalus = cow.getPathfindingMalus(PathType.WATER);
        ServerPlayer driver = wheatHolder(helper, new BlockPos(3, 2, 3));
        // Toggling the feature off mid-drive must restore the water cost promptly — the goal re-reads
        // the flag live rather than holding a lowered cost until the tempt naturally ends. Isolated in
        // its own batch so the config mutation never bleeds into another test.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cow.getPathfindingMalus(PathType.WATER) == 0.0F,
                        "the flock goal lowers the water cost while flocking is on"))
                .thenExecute(() -> InstinctConfig.get().enableFlocking = false)
                .thenWaitUntil(() -> helper.assertTrue(cow.getPathfindingMalus(PathType.WATER) == defaultMalus,
                        "disabling flocking mid-drive restores the water cost promptly"))
                .thenExecute(() -> {
                    InstinctConfig.get().enableFlocking = true;
                    cow.discard();
                    driver.discard();
                })
                .thenSucceed();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static long countFlocking(Animal animal) {
        return animal.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal() instanceof FlockingTemptGoal)
                .count();
    }

    private static long countVanillaTempt(Animal animal) {
        return animal.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal().getClass()
                        == net.minecraft.world.entity.ai.goal.TemptGoal.class)
                .count();
    }

    private static FlockingTemptGoal flockingGoal(Animal animal) {
        for (WrappedGoal wrapped : animal.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof FlockingTemptGoal flocking) {
                return flocking;
            }
        }
        throw new AssertionError("animal did not receive a FlockingTemptGoal");
    }

    private static boolean isHerdWorkRunning(Wolf wolf) {
        for (WrappedGoal wrapped : wolf.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof HerdWorkGoal && wrapped.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private static double closestPair(List<Cow> cows) {
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < cows.size(); i++) {
            for (int j = i + 1; j < cows.size(); j++) {
                closest = Math.min(closest, cows.get(i).position().distanceTo(cows.get(j).position()));
            }
        }
        return closest;
    }

    private static ServerPlayer wheatHolder(GameTestHelper helper, BlockPos rel) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        BlockPos abs = helper.absolutePos(rel);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
        return player;
    }

    private static Wolf spawnTamedWolf(GameTestHelper helper, BlockPos rel, UUID owner) {
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        if (wolf == null) {
            throw new IllegalStateException("could not create a wolf");
        }
        BlockPos abs = helper.absolutePos(rel);
        wolf.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(owner);
        helper.getLevel().addFreshEntity(wolf);
        return wolf;
    }

    private static void buildFloor(GameTestHelper helper, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
    }

    /**
     * A 16×6 sealed pen split by a water channel: solid banks at x[1..5] (cows spawn) and x[10..14]
     * (the driver waits), a 4-wide water strip between them spanning the full depth, and a two-block
     * stone wall on the whole perimeter. The wall contains the water source blocks (so nothing spills
     * out of the test bounds) and leaves no dry detour, so a cow can only reach the driver by
     * committing to the crossing.
     */
    private static void buildWaterCrossing(GameTestHelper helper) {
        int width = 16;
        int depth = 6;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                boolean wall = x == 0 || x == width - 1 || z == 0 || z == depth - 1;
                boolean channel = x >= 6 && x <= 9;
                if (wall) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.SMOOTH_STONE.defaultBlockState());
                } else {
                    helper.setBlock(new BlockPos(x, 1, z),
                            channel ? Blocks.WATER.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
        }
    }
}
