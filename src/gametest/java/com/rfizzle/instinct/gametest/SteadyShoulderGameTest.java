package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.shoulders.SteadyShoulders;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * SPEC §1 Shoulder riding: a perched pets-set animal rides through jumps, short falls, and
 * scratches, and comes off only on a serious hit or a deliberate sneak. The membership resolution
 * from a real shoulder tag and the config gate are checked at the decision level; the {@code hurt()}
 * wrap, the {@code aiStep()} fall-branch wrap, and the sneak {@code tick()}-TAIL inject are each
 * driven end-to-end against a real server-ticked mock player, past vanilla's 20-tick post-mount
 * grace. Callers discard the mock player, which spawns near world spawn outside the structure.
 */
public class SteadyShoulderGameTest implements FabricGameTest {

    private static final int SIZE = 8;

    @GameTest(template = EMPTY_STRUCTURE)
    public void perchedParrotResolvesAndGates(GameTestHelper helper) {
        buildFloor(helper);
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
            helper.assertFalse(SteadyShoulders.dropsOnSneak(owner),
                    "no sneak drop while the owner stands");
            owner.setShiftKeyDown(true);
            helper.assertTrue(SteadyShoulders.dropsOnSneak(owner),
                    "sneaking drops the parrot on purpose");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void nonPetShoulderRiderKeepsVanilla(GameTestHelper helper) {
        buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        try {
            // A shoulder rider outside the pets set (a cow resolves as livestock) is left to vanilla.
            mountShoulder(helper, owner, "minecraft:cow");
            helper.assertFalse(SteadyShoulders.holdsInstinctPet(owner),
                    "a non-pet shoulder rider is not covered");
            helper.assertFalse(SteadyShoulders.keepsThroughHit(owner, 1.0f),
                    "no hit suppression for a non-pet rider");
            owner.setShiftKeyDown(true);
            helper.assertFalse(SteadyShoulders.dropsOnSneak(owner),
                    "no sneak drop for a non-pet rider");
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unresolvableShoulderRiderKeepsVanilla(GameTestHelper helper) {
        buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        try {
            // A hand-edited save can carry a shoulder tag naming a type that no longer exists —
            // an uninstalled mod's animal, or outright junk. Neither may resolve: the entity-type
            // registry answers an unknown id with minecraft:pig unless the lookup bypasses its
            // default, and a malformed id used to throw straight out into vanilla's aiStep.
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
        buildFloor(helper);
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
        buildFloor(helper);
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
        buildFloor(helper);
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
    public void sneakDropsPerchedParrot(GameTestHelper helper) {
        buildFloor(helper);
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        mountShoulder(helper, owner, "minecraft:parrot");
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    // Sneak, then run the full player tick (doTick calls Player#tick, where the
                    // TAIL inject sits) so the sneak drop fires past the grace.
                    owner.setShiftKeyDown(true);
                    owner.doTick();
                    boolean dropped = owner.getShoulderEntityLeft().isEmpty();
                    owner.discard();
                    helper.assertTrue(dropped, "sneaking drops the perched parrot");
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctSteadyShoulderDisabled")
    public void disabledConfigLeavesEveryGateOpen(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableSteadyShoulders;
        ServerPlayer owner = placeOwner(helper, new BlockPos(2, 2, 2));
        try {
            InstinctConfig.get().enableSteadyShoulders = false;
            mountShoulder(helper, owner, "minecraft:parrot");
            // Every gate returns "don't interfere", so vanilla's dismount-on-anything stands.
            helper.assertFalse(SteadyShoulders.keepsThroughHit(owner, 1.0f),
                    "disabled: a scratch is left to dismount the parrot");
            helper.assertFalse(SteadyShoulders.keepsThroughFall(owner),
                    "disabled: the fall branch is left to vanilla");
            owner.setShiftKeyDown(true);
            helper.assertFalse(SteadyShoulders.dropsOnSneak(owner),
                    "disabled: no added sneak drop");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableSteadyShoulders = saved;
            owner.discard();
        }
    }

    /** Two-layer stone floor at y=0..1; the owner stands on the y=2 surface. */
    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                helper.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
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

    private static void mountShoulder(GameTestHelper helper, ServerPlayer owner, String entityId) {
        CompoundTag shoulder = new CompoundTag();
        shoulder.putString("id", entityId);
        helper.assertTrue(owner.setEntityOnShoulder(shoulder),
                "precondition: shoulder rider mounted");
    }
}
