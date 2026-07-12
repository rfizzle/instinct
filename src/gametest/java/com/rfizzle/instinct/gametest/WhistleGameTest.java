package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.DownedData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.whistle.WhistleActions;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SPEC §6 command whistle. The core commands assert outcome and state directly (deterministic, no
 * raycast); the raycast dispatch (a cow orders a round-up, herding-off rounds up nothing) is aimed
 * and driven through {@link WhistleActions#command}. The round-up convergence asserts the outcome —
 * every cow reaches the player, none takes an attack target — never the choreography, and its
 * config-mutating sibling restores the flag in {@code finally}, mirroring {@link HerdingGameTest}.
 */
public class WhistleGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void toggleSitsThenStandsMixedPack(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        List<Wolf> wolves = new ArrayList<>();
        wolves.add(spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID()));
        wolves.add(spawnTamedWolf(helper, new BlockPos(5, 2, 3), owner.getUUID()));
        wolves.add(spawnTamedWolf(helper, new BlockPos(4, 2, 5), owner.getUUID()));
        // Mixed states: two standing, one sitting — the any-standing rule sits the whole pack.
        wolves.get(1).setOrderedToSit(true);

        WhistleActions.WhistleResult first = WhistleActions.toggle(owner);
        helper.assertValueEqual(first.outcome(), WhistleActions.WhistleResult.Outcome.STAY, "any standing → Stay");
        helper.assertValueEqual(first.count(), 3, "all three pets counted");
        for (Wolf wolf : wolves) {
            helper.assertTrue(wolf.isOrderedToSit(), "every pet sits after the first toggle");
        }

        WhistleActions.WhistleResult second = WhistleActions.toggle(owner);
        helper.assertValueEqual(second.outcome(), WhistleActions.WhistleResult.Outcome.FOLLOW, "all sitting → Follow");
        for (Wolf wolf : wolves) {
            helper.assertFalse(wolf.isOrderedToSit(), "every pet stands after the second toggle");
        }

        wolves.forEach(Animal::discard);
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void attackOrderTargetsCombatPetsAndSkipsTheDowned(GameTestHelper helper) {
        buildFloor(helper, 10, 10);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(5, 2, 5));
        Wolf a = spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());
        Wolf b = spawnTamedWolf(helper, new BlockPos(7, 2, 3), owner.getUUID());
        Wolf downed = spawnTamedWolf(helper, new BlockPos(3, 2, 7), owner.getUUID());
        downed.setAttached(InstinctAttachments.DOWNED, new DownedData(helper.getLevel().getGameTime()));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));

        WhistleActions.WhistleResult result = WhistleActions.attackOrder(owner, zombie);
        helper.assertValueEqual(result.outcome(), WhistleActions.WhistleResult.Outcome.ATTACK, "attack order issued");
        helper.assertValueEqual(result.count(), 2, "only the two live combat pets are counted");
        helper.assertTrue(a.getTarget() == zombie, "the first live wolf targets the zombie");
        helper.assertTrue(b.getTarget() == zombie, "the second live wolf targets the zombie");
        helper.assertFalse(a.isOrderedToSit(), "an attack order stands a pet up");
        helper.assertTrue(downed.getTarget() == null, "the downed wolf never acquires a target");

        a.discard();
        b.discard();
        downed.discard();
        zombie.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void downedPetDoesNotRespondOrCount(GameTestHelper helper) {
        buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        Wolf standing = spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());
        Wolf downed = spawnTamedWolf(helper, new BlockPos(5, 2, 3), owner.getUUID());
        downed.setOrderedToSit(false);
        downed.setAttached(InstinctAttachments.DOWNED, new DownedData(helper.getLevel().getGameTime()));

        WhistleActions.WhistleResult result = WhistleActions.toggle(owner);
        helper.assertValueEqual(result.count(), 1, "the downed pet is not counted");
        helper.assertTrue(standing.isOrderedToSit(), "the live pet answered the whistle");
        helper.assertFalse(downed.isOrderedToSit(), "the downed pet's state is untouched");

        standing.discard();
        downed.discard();
        owner.discard();
        helper.succeed();
    }

    // A generous tick budget: driving the whole herd to the player is real pathfinding
    // convergence, which needs headroom to finish on a loaded/variable CI runner even though it
    // usually settles in a fraction of it. The assertion (every cow reaches) is unchanged.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "instinctRoundUp")
    public void roundUpBringsTheHerdToThePlayer(GameTestHelper helper) {
        // The round-up drives livestock through the §4 tempt machinery, so it needs flocking on;
        // pin both flags rather than trust config another batch may have left flipped (restored on
        // success). Its own batch keeps a concurrent test from flipping them back mid-run.
        boolean savedFlock = InstinctConfig.get().enableFlocking;
        boolean savedHerd = InstinctConfig.get().enableHerding;
        InstinctConfig.get().enableFlocking = true;
        InstinctConfig.get().enableHerding = true;
        try {
            buildFloor(helper, 16, 8);
            ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 4));
            // A following pet drives the herd home — the §6 promise (and what pushes the last straggler
            // through the animals already gathered at the player, exactly as in the §4 drive).
            Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 4), owner.getUUID());
            List<Cow> cows = spawnHerd(helper);

            WhistleActions.WhistleResult result = WhistleActions.roundUp(owner, cows.get(0));
            helper.assertValueEqual(result.outcome(), WhistleActions.WhistleResult.Outcome.ROUND_UP, "a cow orders a round-up");
            // Each animal's order clears once it is within 5 blocks (§6), after which it resumes normal
            // AI and may drift, so success is "every cow reached at some point", not "all within 5 at once".
            Set<Cow> reached = new HashSet<>();
            helper.succeedWhen(() -> {
                for (Cow cow : cows) {
                    helper.assertTrue(cow.getTarget() == null, "a rounded-up cow never acquires an attack target");
                    if (cow.position().distanceTo(owner.position()) <= 5.0) {
                        reached.add(cow);
                    }
                }
                helper.assertTrue(reached.containsAll(cows), "every rounded-up cow reaches within 5 blocks of the player");
                cows.forEach(Animal::discard);
                wolf.discard();
                owner.discard();
                InstinctConfig.get().enableFlocking = savedFlock;
                InstinctConfig.get().enableHerding = savedHerd;
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enableFlocking = savedFlock;
            InstinctConfig.get().enableHerding = savedHerd;
            throw e;
        }
    }

    // Same convergence headroom as the flocking-on round-up above.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "instinctRoundUpNoFlock")
    public void roundUpWorksWithFlockingOff(GameTestHelper helper) {
        // SPEC §4: with flocking off but herding on, the drive assist never activates, but the whistle
        // round-up still works — the order is a tempt source the flocking toggle must not silence.
        boolean savedFlock = InstinctConfig.get().enableFlocking;
        boolean savedHerd = InstinctConfig.get().enableHerding;
        InstinctConfig.get().enableFlocking = false;
        InstinctConfig.get().enableHerding = true;
        try {
            buildFloor(helper, 16, 8);
            ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 4));
            List<Cow> cows = spawnHerd(helper);

            helper.assertValueEqual(WhistleActions.roundUp(owner, cows.get(0)).outcome(),
                    WhistleActions.WhistleResult.Outcome.ROUND_UP, "a round-up is ordered even with flocking off");
            Set<Cow> reached = new HashSet<>();
            helper.succeedWhen(() -> {
                for (Cow cow : cows) {
                    if (cow.position().distanceTo(owner.position()) <= 5.0) {
                        reached.add(cow);
                    }
                }
                helper.assertTrue(reached.containsAll(cows),
                        "every cow reaches the player on the order alone, with flocking off");
                cows.forEach(Animal::discard);
                owner.discard();
                InstinctConfig.get().enableFlocking = savedFlock;
                InstinctConfig.get().enableHerding = savedHerd;
            });
        } catch (RuntimeException e) {
            InstinctConfig.get().enableFlocking = savedFlock;
            InstinctConfig.get().enableHerding = savedHerd;
            throw e;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "instinctRoundUpOff")
    public void herdingOffRoundsUpNothing(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableHerding;
        InstinctConfig.get().enableHerding = false;
        try {
            buildFloor(helper, 10, 8);
            ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 4));
            Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 4));
            aimAt(owner, cow);

            WhistleActions.WhistleResult result = WhistleActions.command(owner);
            helper.assertValueEqual(result.outcome(),
                    WhistleActions.WhistleResult.Outcome.NOTHING_TO_ROUND_UP,
                    "with herding off, a cow rounds up nothing");
            helper.assertTrue(cow.getTarget() == null, "the cow never becomes an attack target");
            cow.discard();
            owner.discard();
        } finally {
            InstinctConfig.get().enableHerding = saved;
        }
        helper.succeed();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    @GameTest(template = EMPTY_STRUCTURE)
    public void togglingTenPetsGrantsPackLeader(GameTestHelper helper) {
        buildFloor(helper, 12, 12);
        ServerPlayer owner = listeningPlayer(helper, new BlockPos(5, 2, 5));
        ServerPlayer bystander = listeningPlayer(helper, new BlockPos(6, 2, 5));
        for (int i = 0; i < 10; i++) {
            spawnTamedWolf(helper, new BlockPos(3 + (i % 5), 2, 3 + (i / 5)), owner.getUUID());
        }

        WhistleActions.performToggle(owner);
        helper.assertTrue(isGranted(helper, owner), "one whistle press over 10 owned pets grants pack_leader");
        helper.assertFalse(isGranted(helper, bystander), "a bystander who pressed nothing is not granted");
        owner.discard();
        bystander.discard();
        helper.succeed();
    }

    private static ServerPlayer mockPlayer(GameTestHelper helper, BlockPos rel) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        BlockPos abs = helper.absolutePos(rel);
        player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        return player;
    }

    /** A mock player whose advancement tracker is reloaded so a fresh trigger has a listener. */
    private static ServerPlayer listeningPlayer(GameTestHelper helper, BlockPos rel) {
        ServerPlayer player = mockPlayer(helper, rel);
        player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        return player;
    }

    private static boolean isGranted(GameTestHelper helper, ServerPlayer player) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Instinct.id("pack_leader"));
        helper.assertTrue(holder != null, "pack_leader advancement is loaded (datagen output present)");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /**
     * A three-cow herd just outside the 5-block arrival ring (~6 blocks from the player at 2,2,4),
     * fanned across z so each animal crosses the ring in its own lane before the herd reconverges and
     * piles up. cows.get(0) is the round-up target. The short travel keeps convergence robust against
     * pathfinding RNG while still starting the herd outside the arrival ring.
     */
    private static List<Cow> spawnHerd(GameTestHelper helper) {
        List<Cow> cows = new ArrayList<>();
        cows.add(helper.spawn(EntityType.COW, new BlockPos(8, 2, 4)));
        cows.add(helper.spawn(EntityType.COW, new BlockPos(8, 2, 2)));
        cows.add(helper.spawn(EntityType.COW, new BlockPos(8, 2, 6)));
        return cows;
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

    /** Points the player's view at the target's centre, so the whistle raycast clips it. */
    private static void aimAt(ServerPlayer player, Animal target) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(eye);
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, horizontal));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
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
