package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.gametest.util.TestFloors;
import com.rfizzle.instinct.shoulders.ShoulderDismountGesture;
import com.rfizzle.instinct.shoulders.SneakTapTracker;
import com.rfizzle.instinct.shoulders.SteadyShoulders;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * SPEC §1 Shoulder riding: a perched pets-set animal rides through jumps, short falls, and
 * scratches, and comes off only on a serious hit or the deliberate dismount gesture. The membership
 * resolution from a real shoulder tag and the config gate are checked at the decision level; the
 * {@code hurt()} wrap, the {@code aiStep()} fall-branch wrap, and the gesture {@code tick()}-TAIL
 * inject are each driven end-to-end against a real server-ticked mock player, past vanilla's 20-tick
 * post-mount grace.
 *
 * <p>The gesture tests feed sneak samples one server tick apart, since a double tap is only a double
 * tap across real ticks: each step sets the sneak state and calls {@code doTick()} (the only path that
 * reaches {@code Player#tick}, where the inject sits), with an idle tick between so the game clock the
 * tracker reads actually advances.
 *
 * <p>Each gesture test opens with one <em>settling</em> tick at sneak-released. The tracker is created
 * on the player's first ticked frame and seeded with the sneak state it finds, deliberately, so that a
 * crouch already underway is never counted as a press. A mock player is not ticked until the test ticks
 * it, so opening straight on the press would seed the tracker as already-sneaking and the gesture could
 * never start — and the two "keeps the parrot" tests would then pass for the wrong reason, since a
 * tracker that never sees a press also never drops the bird.
 *
 * <p>Callers discard the mock player, which spawns near world spawn outside the structure.
 */
public class SteadyShoulderGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void perchedParrotResolvesAndGates(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        try {
            mountShoulder(helper, owner, "minecraft:parrot");
            helper.assertTrue(SteadyShoulders.holdsInstinctPet(owner),
                    "a perched parrot resolves into the pets set");
            helper.assertTrue(SteadyShoulders.keepsThroughHit(owner, 1.0f),
                    "a scratch keeps the parrot");
            helper.assertFalse(SteadyShoulders.keepsThroughHit(owner, 6.0f),
                    "a serious hit dislodges the parrot");
            helper.assertTrue(SteadyShoulders.keepsThroughFall(owner),
                    "a standing owner keeps the parrot through the fall branch");
            SneakTapTracker tracker = new SneakTapTracker(false);
            helper.assertFalse(SteadyShoulders.dropsOnGesture(owner, tracker),
                    "no drop while the owner stands");
            owner.setShiftKeyDown(true);
            helper.assertFalse(SteadyShoulders.dropsOnGesture(owner, tracker),
                    "one press is half a gesture, so it does not drop the parrot");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nonPetShoulderRiderKeepsVanilla(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        try {
            // A shoulder rider outside the pets set (a cow resolves as livestock) is left to vanilla.
            mountShoulder(helper, owner, "minecraft:cow");
            helper.assertFalse(SteadyShoulders.holdsInstinctPet(owner),
                    "a non-pet shoulder rider is not covered");
            helper.assertFalse(SteadyShoulders.keepsThroughHit(owner, 1.0f),
                    "no hit suppression for a non-pet rider");
            owner.setShiftKeyDown(true);
            helper.assertFalse(SteadyShoulders.dropsOnGesture(owner, new SneakTapTracker(false)),
                    "no gesture drop for a non-pet rider");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unresolvableShoulderRiderKeepsVanilla(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        try {
            // A hand-edited save can carry a shoulder tag naming a type that no longer exists —
            // an uninstalled mod's animal, or outright junk. Neither may resolve: the entity-type
            // registry answers an unknown id with minecraft:pig unless the lookup bypasses its
            // default, and ResourceLocation.parse throws on a malformed id rather than rejecting
            // it, which would carry the failure straight into vanilla's aiStep.
            for (String id : new String[]{"instinct:no_such_entity_type", "not a valid id!"}) {
                mountShoulder(helper, owner, id);
                helper.assertFalse(SteadyShoulders.holdsInstinctPet(owner),
                        "an unresolvable shoulder rider is not covered: " + id);
                helper.assertFalse(SteadyShoulders.keepsThroughFall(owner),
                        "no fall suppression for an unresolvable rider: " + id);
                helper.assertFalse(SteadyShoulders.keepsThroughHit(owner, 1.0f),
                        "no hit suppression for an unresolvable rider: " + id);
            }
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void minorHitKeepsPerchedParrot(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    // genericKill bypasses the mock player's spawn-invulnerability so the hit
                    // actually reaches Player#hurt; the wrap keys on the amount, not the source.
                    owner.hurt(helper.getLevel().damageSources().genericKill(), 1.0f);
                    boolean kept = !owner.getShoulderEntityLeft().isEmpty();
                    owner.discard();
                    helper.assertTrue(kept, "a scratch keeps the parrot perched");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void seriousHitDropsPerchedParrot(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    // genericKill bypasses the mock player's spawn-invulnerability so the hit
                    // actually reaches Player#hurt; the wrap keys on the amount, not the source.
                    owner.hurt(helper.getLevel().damageSources().genericKill(), 6.0f);
                    boolean dropped = owner.getShoulderEntityLeft().isEmpty();
                    owner.discard();
                    helper.assertTrue(dropped, "a serious hit dislodges the parrot");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fallKeepsPerchedParrot(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    // Lift the owner into open air (no ground collision to reset fallDistance) and
                    // stage a fall, then run one aiStep: vanilla's fall-branch removal fires and the
                    // wrap suppresses it, so the parrot rides the fall.
                    BlockPos air = helper.absolutePos(new BlockPos(2, 6, 2));
                    owner.moveTo(air.getX() + 0.5, air.getY(), air.getZ() + 0.5, 0.0f, 0.0f);
                    owner.setDeltaMovement(0.0, 0.0, 0.0);
                    owner.setOnGround(false);
                    owner.fallDistance = 1.0f;
                    owner.aiStep();
                    boolean kept = !owner.getShoulderEntityLeft().isEmpty();
                    owner.discard();
                    helper.assertTrue(kept, "a fall keeps the perched parrot up");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void doubleTapSneakDropsPerchedParrot(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> tickSneaking(owner, false))   // settle (see class doc)
                .thenIdle(1)
                .thenExecute(() -> tickSneaking(owner, true))    // press
                .thenIdle(1)
                .thenExecute(() -> tickSneaking(owner, false))   // release — the tap banks
                .thenIdle(1)
                .thenExecute(() -> tickSneaking(owner, true))    // press again — the gesture completes
                .thenExecute(() -> {
                    boolean dropped = owner.getShoulderEntityLeft().isEmpty();
                    owner.discard();
                    helper.assertTrue(dropped, "a double-tap sneak drops the perched parrot");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void singleSneakTapKeepsPerchedParrot(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> tickSneaking(owner, false))   // settle (see class doc)
                .thenIdle(1)
                .thenExecute(() -> tickSneaking(owner, true))
                .thenIdle(1)
                .thenExecute(() -> tickSneaking(owner, false))
                .thenIdle(20)                                   // the banked tap expires
                .thenExecute(() -> tickSneaking(owner, true))    // so this reads as a fresh press
                .thenExecute(() -> {
                    boolean kept = !owner.getShoulderEntityLeft().isEmpty();
                    owner.discard();
                    helper.assertTrue(kept, "one stray sneak tap leaves the parrot perched");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void heldSneakKeepsPerchedParrot(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        // The crouch a player holds to place a block or work a ledge — the whole reason for the
        // gesture — must never dislodge the bird, however long it is held.
        GameTestSequence sequence = helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> tickSneaking(owner, false))   // settle (see class doc)
                .thenIdle(1);
        for (int i = 0; i < 20; i++) {
            sequence = sequence.thenExecute(() -> tickSneaking(owner, true)).thenIdle(1);
        }
        sequence.thenExecute(() -> {
            boolean kept = !owner.getShoulderEntityLeft().isEmpty();
            owner.discard();
            helper.assertTrue(kept, "a held crouch leaves the parrot perched");
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctSteadyShoulderLegacySneak")
    public void sneakGestureDropsOnAnySneak(GameTestHelper helper) {
        TestFloors.buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        ShoulderDismountGesture saved = InstinctConfig.get().shoulderDismountGesture;
        InstinctConfig.get().shoulderDismountGesture = ShoulderDismountGesture.SNEAK;
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    try {
                        // The escape hatch for anyone who preferred the plain-sneak feel: one sneak,
                        // one tick, bird down.
                        tickSneaking(owner, true);
                        helper.assertTrue(owner.getShoulderEntityLeft().isEmpty(),
                                "the SNEAK gesture drops the parrot on a plain sneak");
                    } finally {
                        InstinctConfig.get().shoulderDismountGesture = saved;
                        owner.discard();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctSteadyShoulderDisabled")
    public void disabledConfigLeavesEveryGateOpen(GameTestHelper helper) {
        boolean savedEnabled = InstinctConfig.get().enableSteadyShoulders;
        ShoulderDismountGesture savedGesture = InstinctConfig.get().shoulderDismountGesture;
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        try {
            InstinctConfig.get().enableSteadyShoulders = false;
            // Pick the gesture that would fire on a single held sneak, so the assertion below can
            // only pass because the feature toggle closed the gate.
            InstinctConfig.get().shoulderDismountGesture = ShoulderDismountGesture.SNEAK;
            mountShoulder(helper, owner, "minecraft:parrot");
            // Every gate returns "don't interfere", so vanilla's dismount-on-anything stands.
            helper.assertFalse(SteadyShoulders.keepsThroughHit(owner, 1.0f),
                    "disabled: a scratch is left to dismount the parrot");
            helper.assertFalse(SteadyShoulders.keepsThroughFall(owner),
                    "disabled: the fall branch is left to vanilla");
            owner.setShiftKeyDown(true);
            helper.assertFalse(SteadyShoulders.dropsOnGesture(owner, new SneakTapTracker(false)),
                    "disabled: no added gesture drop");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableSteadyShoulders = savedEnabled;
            InstinctConfig.get().shoulderDismountGesture = savedGesture;
            owner.discard();
        }
    }

    /**
     * A mock owner planted on the floor: no gravity and a forced on-ground flag keep it resting so
     * it never falls (satisfying the perch precondition and never taking incidental fall damage),
     * and invulnerability is cleared so {@code hurt()} actually reaches the shoulder-removal call.
     */
    private static ServerPlayer placeOwner(GameTestHelper helper, BlockPos rel) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        BlockPos abs = helper.absolutePos(rel);
        owner.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        owner.setNoGravity(true);
        owner.setOnGround(true);
        owner.getAbilities().invulnerable = false;
        return owner;
    }

    /**
     * Feeds one sneak sample through the real {@code Player#tick} inject. {@code ServerPlayer#tick} does
     * not call {@code super.tick()}, so {@code doTick()} is the only path that reaches the inject.
     */
    private static void tickSneaking(ServerPlayer owner, boolean sneaking) {
        owner.setShiftKeyDown(sneaking);
        owner.doTick();
    }

    private static void mountShoulder(GameTestHelper helper, ServerPlayer owner, String entityId) {
        CompoundTag shoulder = new CompoundTag();
        shoulder.putString("id", entityId);
        helper.assertTrue(owner.setEntityOnShoulder(shoulder),
                "precondition: shoulder rider mounted");
    }
}
