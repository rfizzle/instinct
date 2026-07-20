package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.InstinctAnimalRevivedCallback;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.HomeData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.gametest.util.PetSpawns;
import com.rfizzle.instinct.gametest.util.TestFloors;
import com.rfizzle.instinct.kennel.KennelHandler;
import com.rfizzle.instinct.registry.InstinctBlocks;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import com.rfizzle.instinct.whistle.WhistleActions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.AbstractArrow;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SPEC §9 kennel post. The whistle assigns pets a home and recalls them to it on Stay; a downed pet
 * beside a post recovers on its own, slowly and rank-free, while a downed pet with no post nearby
 * stays down. The assignment and Stay paths are driven through the {@link WhistleActions} seam
 * directly (deterministic); recall and recovery poll with {@code succeedWhen} because both are real
 * convergence. Config-mutating tests get their own batch and restore in {@code finally}.
 */
public class KennelGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void assignHomeWritesHomeAndRecalls(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        BlockPos postRel = new BlockPos(4, 2, 4);
        helper.setBlock(postRel, InstinctBlocks.KENNEL_POST.defaultBlockState());
        BlockPos postAbs = helper.absolutePos(postRel);
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());

        WhistleActions.WhistleResult result = WhistleActions.assignHome(owner, postAbs);
        helper.assertValueEqual(result.outcome(), WhistleActions.WhistleResult.Outcome.ASSIGN_HOME, "an assign-home order is issued");
        helper.assertValueEqual(result.count(), 1, "the one pet in range is homed");
        HomeData home = wolf.getAttached(InstinctAttachments.HOME);
        helper.assertTrue(home != null, "the wolf carries a home");
        helper.assertValueEqual(home.post(), postAbs, "home is the kennel post that was clicked");
        helper.assertTrue(home.dimension().equals(helper.getLevel().dimension()), "home records the post's dimension");
        helper.assertTrue(KennelHandler.isRecalling(wolf), "an assigned pet is sent home now");
        helper.assertFalse(wolf.isOrderedToSit(), "a recalling pet stands to walk home");

        wolf.discard();
        owner.discard();
        helper.succeed();
    }

    // Recall is real pathfinding, so poll: the homed wolf walks to its post and settles (sits) there.
    // Kept compact — within the small EMPTY_STRUCTURE region the pathfinder searches (a longer trek is
    // a structure-bound artifact, not a behavior; real worlds repath and advance over any distance).
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void stayRecallsHomedPetToItsPost(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 10, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 4));
        BlockPos postRel = new BlockPos(7, 2, 4);
        helper.setBlock(postRel, InstinctBlocks.KENNEL_POST.defaultBlockState());
        BlockPos postAbs = helper.absolutePos(postRel);
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 4), owner.getUUID());
        wolf.setAttached(InstinctAttachments.HOME, new HomeData(postAbs, helper.getLevel().dimension()));

        WhistleActions.WhistleResult result = WhistleActions.toggle(owner);
        helper.assertValueEqual(result.outcome(), WhistleActions.WhistleResult.Outcome.STAY, "Stay is ordered");
        helper.assertFalse(wolf.isOrderedToSit(), "a homed pet stands to walk home rather than sit in place");

        helper.succeedWhen(() -> {
            double d2 = wolf.position().distanceToSqr(postAbs.getX() + 0.5, postAbs.getY(), postAbs.getZ() + 0.5);
            helper.assertTrue(d2 <= 4.0, "the homed pet reaches its post (distSq=" + d2 + ")");
            helper.assertTrue(wolf.isOrderedToSit(), "it settles (sits) once home");
            helper.assertFalse(KennelHandler.isRecalling(wolf), "the recall ends on arrival");
            wolf.discard();
            owner.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unhomedPetOnStaySitsInPlace(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());

        WhistleActions.toggle(owner);
        helper.assertTrue(wolf.isOrderedToSit(), "an un-homed pet sits where it stands, exactly as before");
        helper.assertFalse(KennelHandler.isRecalling(wolf), "an un-homed pet is never recalled");

        wolf.discard();
        owner.discard();
        helper.succeed();
    }

    // A pet homed to a post that no longer stands settles where it is rather than trekking to nothing.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void staleHomeSettlesInPlace(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 12, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 4));
        // A home whose post block was never placed — its chunk is loaded, so the goal can see it is gone.
        BlockPos ghostHome = helper.absolutePos(new BlockPos(8, 2, 4));
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 4), owner.getUUID());
        BlockPos start = wolf.blockPosition();
        wolf.setAttached(InstinctAttachments.HOME, new HomeData(ghostHome, helper.getLevel().dimension()));

        WhistleActions.toggle(owner);
        helper.succeedWhen(() -> {
            helper.assertTrue(wolf.isOrderedToSit(), "a pet homed to a mined post settles");
            helper.assertFalse(KennelHandler.isRecalling(wolf), "the recall ends without a post to reach");
            helper.assertTrue(wolf.blockPosition().distSqr(start) <= 9.0,
                    "it sits near where it stood rather than trekking to the gone post");
            wolf.discard();
            owner.discard();
        });
    }

    // Recovery is a slow over-time sweep, so poll: a downed pet beside a post gets back up, keeping its
    // rank (rank-free, unlike an item revival). A short recovery window keeps the test fast.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctKennelRecovery")
    public void recoversBesideAPostWithoutLosingRank(GameTestHelper helper) {
        int savedSeconds = InstinctConfig.get().kennelRecoverySeconds;
        boolean savedEnabled = InstinctConfig.get().enableKennelPost;
        InstinctConfig.get().kennelRecoverySeconds = 1; // 20-tick threshold for a fast test
        InstinctConfig.get().enableKennelPost = true;
        try {
            TestFloors.buildFloor(helper, 8, 8);
            helper.setBlock(new BlockPos(5, 2, 4), InstinctBlocks.KENNEL_POST.defaultBlockState());
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 4), UUID.randomUUID());
            VeterancyHandler.setAccruedDays(wolf, 60.0); // rank 3 at default thresholds
            helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 3, "precondition: rank 3");
            downWithArrow(helper, wolf);
            helper.assertTrue(InstinctAPI.isDowned(wolf), "precondition: downed beside the post");

            helper.succeedWhen(() -> {
                helper.assertFalse(InstinctAPI.isDowned(wolf), "the pet recovers on its own beside the post");
                helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 3, "home recovery costs no rank");
                wolf.discard();
                InstinctConfig.get().kennelRecoverySeconds = savedSeconds;
                InstinctConfig.get().enableKennelPost = savedEnabled;
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().kennelRecoverySeconds = savedSeconds;
            InstinctConfig.get().enableKennelPost = savedEnabled;
            throw e;
        }
    }

    // Issue #34: post recovery is a path back up like any other, so it fires the public revived
    // callback — but with no reviving player and no item spent, unlike the field revival.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctKennelRecoveryCallback")
    public void postRecoveryFiresTheRevivedCallbackWithNoReviverOrItem(GameTestHelper helper) {
        int savedSeconds = InstinctConfig.get().kennelRecoverySeconds;
        boolean savedEnabled = InstinctConfig.get().enableKennelPost;
        InstinctConfig.get().kennelRecoverySeconds = 1;
        InstinctConfig.get().enableKennelPost = true;
        try {
            TestFloors.buildFloor(helper, 8, 8);
            helper.setBlock(new BlockPos(5, 2, 4), InstinctBlocks.KENNEL_POST.defaultBlockState());
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 4), UUID.randomUUID());
            AtomicBoolean revivedFired = new AtomicBoolean(false);
            AtomicBoolean noReviverOrItem = new AtomicBoolean(false);
            // Match on UUID, not the entity: a registered listener can never be unregistered, so
            // capturing the entity would pin it and its level for the rest of the run.
            UUID wolfId = wolf.getUUID();
            InstinctAnimalRevivedCallback.EVENT.register((animal, reviver, item) -> {
                if (animal.getUUID().equals(wolfId)) {
                    revivedFired.set(true);
                    noReviverOrItem.set(reviver == null && item.isEmpty());
                }
            });
            downWithArrow(helper, wolf);
            helper.assertTrue(InstinctAPI.isDowned(wolf), "precondition: downed beside the post");

            helper.succeedWhen(() -> {
                helper.assertTrue(revivedFired.get(),
                        "InstinctAnimalRevivedCallback fires on kennel-post recovery");
                helper.assertTrue(noReviverOrItem.get(),
                        "post recovery carries a null reviver and an empty stack");
                wolf.discard();
                InstinctConfig.get().kennelRecoverySeconds = savedSeconds;
                InstinctConfig.get().enableKennelPost = savedEnabled;
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().kennelRecoverySeconds = savedSeconds;
            InstinctConfig.get().enableKennelPost = savedEnabled;
            throw e;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "instinctKennelNoPost")
    public void noRecoveryWithoutAPost(GameTestHelper helper) {
        int savedSeconds = InstinctConfig.get().kennelRecoverySeconds;
        InstinctConfig.get().kennelRecoverySeconds = 1; // would recover fast IF a post were near
        try {
            TestFloors.buildFloor(helper, 8, 8);
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 4), UUID.randomUUID());
            downWithArrow(helper, wolf);
            helper.assertTrue(InstinctAPI.isDowned(wolf), "precondition: downed with no post nearby");

            // Wait well past the recovery window, then assert it never got up.
            helper.startSequence()
                    .thenIdle(60)
                    .thenExecute(() -> helper.assertTrue(InstinctAPI.isDowned(wolf),
                            "with no kennel post nearby a downed pet stays down — the field item is still needed"))
                    .thenExecute(wolf::discard)
                    .thenExecute(() -> InstinctConfig.get().kennelRecoverySeconds = savedSeconds)
                    .thenSucceed();
        } catch (RuntimeException e) {
            InstinctConfig.get().kennelRecoverySeconds = savedSeconds;
            throw e;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "instinctKennelDisabled")
    public void disabledFeatureSkipsRecovery(GameTestHelper helper) {
        boolean savedEnabled = InstinctConfig.get().enableKennelPost;
        int savedSeconds = InstinctConfig.get().kennelRecoverySeconds;
        InstinctConfig.get().enableKennelPost = false;
        InstinctConfig.get().kennelRecoverySeconds = 1;
        try {
            TestFloors.buildFloor(helper, 8, 8);
            helper.setBlock(new BlockPos(5, 2, 4), InstinctBlocks.KENNEL_POST.defaultBlockState());
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 4), UUID.randomUUID());
            downWithArrow(helper, wolf);

            helper.startSequence()
                    .thenIdle(60)
                    .thenExecute(() -> helper.assertTrue(InstinctAPI.isDowned(wolf),
                            "with the kennel feature off, a downed pet never recovers at a post"))
                    .thenExecute(wolf::discard)
                    .thenExecute(() -> {
                        InstinctConfig.get().enableKennelPost = savedEnabled;
                        InstinctConfig.get().kennelRecoverySeconds = savedSeconds;
                    })
                    .thenSucceed();
        } catch (RuntimeException e) {
            InstinctConfig.get().enableKennelPost = savedEnabled;
            InstinctConfig.get().kennelRecoverySeconds = savedSeconds;
            throw e;
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos rel) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        BlockPos abs = helper.absolutePos(rel);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        return player;
    }

    /** Applies a lethal arrow hit (not beyond saving), downing a healthy tamed pet. */
    private static void downWithArrow(GameTestHelper helper, Wolf wolf) {
        AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
        if (arrow == null) {
            throw new IllegalStateException("could not create an arrow");
        }
        wolf.hurt(helper.getLevel().damageSources().arrow(arrow, null), 1000.0F);
        arrow.discard();
    }
}
