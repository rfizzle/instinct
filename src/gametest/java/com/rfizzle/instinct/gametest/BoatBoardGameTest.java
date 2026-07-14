package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.boating.BoardBoatGoal;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * SPEC §4 "Water crossings", the boat-boarding half: a following pet takes the boat's single spare
 * seat, only one pet boards it, the pet hops out when the owner lands, and the behavior stays put
 * when the feature is off or the pet is on Stay. Outcome assertions on ride state, never on the
 * approach path — the same "feel over choreography" discipline as {@link HerdingGameTest}.
 */
public class BoatBoardGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void petBoardsOwnersBoat(GameTestHelper helper) {
        buildFloor(helper, 10, 8);
        ServerPlayer driver = driverInBoat(helper, new BlockPos(6, 2, 4));
        Boat boat = (Boat) driver.getVehicle();
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 4), driver.getUUID());
        helper.succeedWhen(() -> {
            helper.assertTrue(wolf.getVehicle() == boat, "the following pet takes the boat's spare seat");
            wolf.discard();
            boat.discard();
            driver.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void onlyOnePetTakesTheSpareSeat(GameTestHelper helper) {
        buildFloor(helper, 10, 8);
        ServerPlayer driver = driverInBoat(helper, new BlockPos(6, 2, 4));
        Boat boat = (Boat) driver.getVehicle();
        Wolf near = spawnTamedWolf(helper, new BlockPos(4, 2, 4), driver.getUUID());
        Wolf far = spawnTamedWolf(helper, new BlockPos(2, 2, 4), driver.getUUID());
        // The boat seats two; the driver fills one, so exactly one wolf can ever board — the vanilla
        // seat cap enforces it even though both pets are eligible.
        helper.succeedWhen(() -> {
            boolean nearAboard = near.getVehicle() == boat;
            boolean farAboard = far.getVehicle() == boat;
            helper.assertTrue(nearAboard ^ farAboard, "exactly one pet takes the single spare seat");
            helper.assertTrue(boat.getPassengers().size() == 2,
                    "the boat holds the driver plus one pet, never a third");
            near.discard();
            far.discard();
            boat.discard();
            driver.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void petDisembarksWhenOwnerLands(GameTestHelper helper) {
        buildFloor(helper, 10, 8);
        ServerPlayer driver = driverInBoat(helper, new BlockPos(6, 2, 4));
        Boat boat = (Boat) driver.getVehicle();
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 4), driver.getUUID());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(wolf.getVehicle() == boat, "the pet boards first"))
                .thenExecute(driver::stopRiding) // the owner steps ashore
                .thenWaitUntil(() -> helper.assertTrue(wolf.getVehicle() == null,
                        "the pet hops out once its owner leaves the boat"))
                .thenExecute(() -> {
                    wolf.discard();
                    boat.discard();
                    driver.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "instinctBoatingOff")
    public void noBoardingWhileDisabled(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enablePetBoating;
        InstinctConfig.get().enablePetBoating = false;
        try {
            buildFloor(helper, 10, 8);
            ServerPlayer driver = driverInBoat(helper, new BlockPos(6, 2, 4));
            Boat boat = (Boat) driver.getVehicle();
            Wolf wolf = spawnTamedWolf(helper, new BlockPos(4, 2, 4), driver.getUUID());
            helper.runAfterDelay(60, () -> {
                try {
                    helper.assertTrue(wolf.getVehicle() == null, "a pet never boards while the feature is off");
                } finally {
                    InstinctConfig.get().enablePetBoating = saved;
                }
                wolf.discard();
                boat.discard();
                driver.discard();
                helper.succeed();
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enablePetBoating = saved;
            throw e;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void sittingPetDoesNotBoard(GameTestHelper helper) {
        buildFloor(helper, 10, 8);
        ServerPlayer driver = driverInBoat(helper, new BlockPos(6, 2, 4));
        Boat boat = (Boat) driver.getVehicle();
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(4, 2, 4), driver.getUUID());
        wolf.setOrderedToSit(true); // on Stay — it holds its ground, it does not chase the boat
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(wolf.getVehicle() == null, "a pet on Stay never boards");
            wolf.discard();
            boat.discard();
            driver.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void petWithCombatTargetDoesNotBoard(GameTestHelper helper) {
        buildFloor(helper, 10, 8);
        ServerPlayer driver = driverInBoat(helper, new BlockPos(6, 2, 4));
        Boat boat = (Boat) driver.getVehicle();
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(4, 2, 4), driver.getUUID());
        // An invulnerable cow the wolf can never kill: the combat target holds for the whole window, so
        // it never clears mid-test and frees the boarding goal to start (the old flake).
        Cow prey = helper.spawn(EntityType.COW, new BlockPos(4, 2, 7));
        prey.setInvulnerable(true);
        wolf.setTarget(prey); // a pet defending itself stands the boarding goal down
        helper.startSequence()
                // Hold the target across the window and assert the boarding goal never works while it stands.
                .thenExecuteFor(60, () -> {
                    wolf.setTarget(prey);
                    helper.assertFalse(isBoardGoalRunning(wolf),
                            "a pet with a combat target never works the boarding goal");
                    helper.assertTrue(wolf.getVehicle() == null, "a pet with a combat target does not board");
                })
                .thenExecute(() -> {
                    wolf.discard();
                    prey.discard();
                    boat.discard();
                    driver.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void adoptsAndDisembarksAPetSeatedBeforeTheGoalRuns(GameTestHelper helper) {
        buildFloor(helper, 10, 8);
        ServerPlayer driver = driverInBoat(helper, new BlockPos(6, 2, 4));
        Boat boat = (Boat) driver.getVehicle();
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(5, 2, 4), driver.getUUID());
        wolf.startRiding(boat); // pre-seated, standing in for a world reloaded mid-voyage
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(wolf.getVehicle() == boat && isBoardGoalRunning(wolf),
                        "the goal adopts the already-seated pet"))
                .thenExecute(driver::stopRiding) // the owner steps ashore
                .thenWaitUntil(() -> helper.assertTrue(wolf.getVehicle() == null,
                        "the adopted pet still hops out when its owner leaves the boat"))
                .thenExecute(() -> {
                    wolf.discard();
                    boat.discard();
                    driver.discard();
                })
                .thenSucceed();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static boolean isBoardGoalRunning(Wolf wolf) {
        for (WrappedGoal wrapped : wolf.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof BoardBoatGoal && wrapped.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private static ServerPlayer driverInBoat(GameTestHelper helper, BlockPos rel) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        BlockPos abs = helper.absolutePos(rel);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        Boat boat = helper.spawn(EntityType.BOAT, rel);
        player.startRiding(boat, true); // force the driver into the controlling seat
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
}
