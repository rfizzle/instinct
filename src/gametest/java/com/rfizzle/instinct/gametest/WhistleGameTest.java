package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.DownedData;
import com.rfizzle.instinct.data.GuardData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.gametest.util.PetSpawns;
import com.rfizzle.instinct.gametest.util.TestFloors;
import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.whistle.WhistleActions;
import com.rfizzle.instinct.whistle.WhistleLocator;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SPEC §6 command whistle. The core commands assert outcome and state directly (deterministic, no
 * raycast); the raycast dispatch (a cow orders a round-up, herding-off rounds up nothing) is aimed
 * and driven through {@link WhistleActions#command}. The round-up convergence asserts the outcome —
 * every cow reaches the player, none takes an attack target — never the choreography, and its
 * config-mutating sibling restores the flag in {@code finally}, mirroring {@link HerdingGameTest}.
 */
public class WhistleGameTest implements FabricGameTest {

    // The round-up tests drive a herd ~7 blocks across the floor. EMPTY_STRUCTURE force-loads only an
    // 8x8 box, so a cow placed beyond it lands in a loaded-but-not-entity-ticking chunk and its AI never
    // runs — a frozen straggler that never reaches. This 16x5x6 lane keeps the whole herd inside the
    // entity-ticking box, exactly as the herding drive tests use it.
    private static final String LANE = "instinct:drive_lane";

    @GameTest(template = EMPTY_STRUCTURE)
    public void toggleSitsThenStandsMixedPack(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        List<Wolf> wolves = new ArrayList<>();
        wolves.add(PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID()));
        wolves.add(PetSpawns.spawnTamedWolf(helper, new BlockPos(5, 2, 3), owner.getUUID()));
        wolves.add(PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 5), owner.getUUID()));
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
        TestFloors.buildFloor(helper, 10, 10);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(5, 2, 5));
        Wolf a = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());
        Wolf b = PetSpawns.spawnTamedWolf(helper, new BlockPos(7, 2, 3), owner.getUUID());
        Wolf downed = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 7), owner.getUUID());
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
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        Wolf standing = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());
        Wolf downed = PetSpawns.spawnTamedWolf(helper, new BlockPos(5, 2, 3), owner.getUUID());
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

    // A generous poll budget: driving the whole herd home is real pathfinding convergence that needs
    // ample headroom on a loaded/variable CI runner even though it settles in a fraction of it locally.
    // succeedWhen exits the instant every cow has reached, so the budget only bounds a struggling run.
    @GameTest(template = LANE, timeoutTicks = 3000, batch = "instinctRoundUp")
    public void roundUpBringsTheHerdToThePlayer(GameTestHelper helper) {
        // The round-up drives livestock through the §4 tempt machinery, so it needs flocking on;
        // pin both flags rather than trust config another batch may have left flipped (restored on
        // success). Its own batch keeps a concurrent test from flipping them back mid-run.
        boolean savedFlock = InstinctConfig.get().enableFlocking;
        boolean savedHerd = InstinctConfig.get().enableHerding;
        InstinctConfig.get().enableFlocking = true;
        InstinctConfig.get().enableHerding = true;
        try {
            TestFloors.buildFloor(helper, 16, 6);
            ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 3));
            // A following pet drives the herd home — the §6 promise (and what pushes the last straggler
            // through the animals already gathered at the player, exactly as in the §4 drive).
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());
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

    // Same convergence headroom as the flocking-on round-up above — more marginal here, since with
    // flocking off there is no drive-assist and the cows reach on the order alone.
    @GameTest(template = LANE, timeoutTicks = 3000, batch = "instinctRoundUpNoFlock")
    public void roundUpWorksWithFlockingOff(GameTestHelper helper) {
        // SPEC §4: with flocking off but herding on, the drive assist never activates, but the whistle
        // round-up still works — the order is a tempt source the flocking toggle must not silence.
        boolean savedFlock = InstinctConfig.get().enableFlocking;
        boolean savedHerd = InstinctConfig.get().enableHerding;
        InstinctConfig.get().enableFlocking = false;
        InstinctConfig.get().enableHerding = true;
        try {
            TestFloors.buildFloor(helper, 16, 6);
            ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 3));
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
            TestFloors.buildFloor(helper, 10, 8);
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

    @GameTest(template = EMPTY_STRUCTURE)
    public void guardOrderPostsCombatPetsAndSkipsNonCombat(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 10, 10);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(5, 2, 5));
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 4), owner.getUUID());
        Parrot parrot = PetSpawns.spawnTamedParrot(helper, new BlockPos(6, 2, 4), owner.getUUID());
        BlockPos anchor = helper.absolutePos(new BlockPos(5, 2, 5));

        WhistleActions.WhistleResult result = WhistleActions.guardOrder(owner, anchor);
        helper.assertValueEqual(result.outcome(), WhistleActions.WhistleResult.Outcome.GUARD, "a guard order is issued");
        helper.assertValueEqual(result.count(), 1, "only the combat-capable wolf is posted");
        GuardData posted = wolf.getAttached(InstinctAttachments.GUARD);
        helper.assertTrue(posted != null, "the wolf takes a guard post");
        helper.assertValueEqual(posted.anchor(), anchor, "the post is anchored at the looked-at spot");
        helper.assertFalse(wolf.isOrderedToSit(), "a posted pet stands to hold its ground");
        helper.assertTrue(parrot.getAttached(InstinctAttachments.GUARD) == null,
                "the non-combat parrot (no attack-damage attribute) is never posted");

        wolf.discard();
        parrot.discard();
        owner.discard();
        helper.succeed();
    }

    // Engagement is a goal-driven scan (a few ticks), so this polls with succeedWhen rather than
    // asserting instantly. The cow sits closer to the post than the zombie, so a guard that targeted
    // the nearest body would pick it — proving the monsters-only filter, not mere proximity.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void guardingWolfEngagesTheHostileAndIgnoresLivestock(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 12, 12);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 2));
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(6, 2, 6), owner.getUUID());
        BlockPos anchor = helper.absolutePos(new BlockPos(6, 2, 6));
        wolf.setAttached(InstinctAttachments.GUARD, new GuardData(anchor));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 6));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 6));

        helper.succeedWhen(() -> {
            helper.assertFalse(wolf.getTarget() == cow, "the guard never turns on livestock");
            helper.assertTrue(wolf.getTarget() == zombie, "the guard engages the hostile that entered its radius");
            cow.discard();
            zombie.discard();
            wolf.discard();
            owner.discard();
        });
    }

    // The guard must resume after a kill: vanilla melee leaves a dead hostile set as the target, so the
    // goal has to clear it and re-engage rather than stay pinned to a corpse. Two-phase: engage the
    // first hostile, remove it, then assert the guard picks up a second one that enters the post.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void guardResumesAfterItsHostileDies(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 12, 12);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(2, 2, 2));
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(6, 2, 6), owner.getUUID());
        wolf.setAttached(InstinctAttachments.GUARD, new GuardData(helper.absolutePos(new BlockPos(6, 2, 6))));
        Zombie first = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 6));
        Zombie[] second = new Zombie[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(wolf.getTarget() == first, "the guard engages the first hostile"))
                .thenExecute(first::discard)
                .thenExecute(() -> second[0] = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 6)))
                .thenWaitUntil(() -> helper.assertTrue(wolf.getTarget() == second[0],
                        "once its first target is gone the guard clears it and engages the next hostile"))
                .thenExecute(() -> {
                    if (second[0] != null) {
                        second[0].discard();
                    }
                    wolf.discard();
                    owner.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aNewOrderClearsTheGuardPost(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3), owner.getUUID());
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));

        WhistleActions.guardOrder(owner, anchor);
        helper.assertTrue(wolf.getAttached(InstinctAttachments.GUARD) != null, "the wolf is posted after a guard order");

        WhistleActions.toggle(owner);
        helper.assertTrue(wolf.getAttached(InstinctAttachments.GUARD) == null,
                "a Stay/Follow toggle replaces the guard post");

        wolf.discard();
        owner.discard();
        helper.succeed();
    }

    // ── lost-pet locator (SPEC §6) ────────────────────────────────────────────────────────────────
    // A world-wide owned-pet scan, so these run in their own batch (temporally isolated from the
    // pathfinding batches) and each filters by its own mock owner. The census is a snapshot, so every
    // test reads locate() synchronously in the spawn tick, no ticks needed.
    //
    // These tests stand pets well outside the 8x8 box EMPTY_STRUCTURE force-loads, and a pet is
    // enumerable only once its chunk is accessible. The spawn helpers force that chunk and then assert
    // the pet reached the level's entity lookup — see prepareSpawn and assertEnumerable.

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctLocate")
    public void locateReportsDistantPetAndSkipsNearOne(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        Wolf near = PetSpawns.spawnTamedWolf(helper, new BlockPos(6, 2, 4), owner.getUUID()); // ~2 blocks — within voice
        Wolf far = PetSpawns.spawnTamedWolf(helper, new BlockPos(30, 2, 4), owner.getUUID()); // ~26 blocks east — beyond voice
        far.setOrderedToSit(true);

        WhistleActions.LocateResult result = WhistleActions.locate(owner);
        helper.assertValueEqual(result.sightings().size(), 1, "only the pet beyond the whistle's voice is listed");
        helper.assertValueEqual(result.overflow(), 0, "no overflow for a single distant pet");
        WhistleActions.Sighting sighting = result.sightings().get(0);
        helper.assertTrue(sighting.sameDimension(), "the distant pet is in the player's dimension");
        helper.assertValueEqual(sighting.direction(), WhistleLocator.Compass8.E, "a pet due east reads east");
        helper.assertValueEqual(sighting.state(), WhistleLocator.PetState.SITTING, "a sitting pet reads sitting");
        helper.assertTrue(sighting.blocks() >= 25 && sighting.blocks() <= 27,
                "the distance is reported in whole blocks (~26)");

        near.discard();
        far.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctLocate")
    public void locateIncludesTheDownedPatient(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        Wolf downed = PetSpawns.spawnTamedWolf(helper, new BlockPos(4, 2, 30), owner.getUUID()); // ~26 blocks south
        downed.setAttached(InstinctAttachments.DOWNED, new DownedData(helper.getLevel().getGameTime()));

        WhistleActions.LocateResult result = WhistleActions.locate(owner);
        helper.assertValueEqual(result.sightings().size(), 1,
                "a downed distant pet is listed (the locator finds the patient, unlike a command)");
        WhistleActions.Sighting sighting = result.sightings().get(0);
        helper.assertValueEqual(sighting.state(), WhistleLocator.PetState.DOWNED, "the downed pet reads downed");
        helper.assertValueEqual(sighting.direction(), WhistleLocator.Compass8.S, "a pet due south reads south");

        downed.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctLocate")
    public void locateReportsNothingWhenEveryPetIsNear(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        Wolf near = PetSpawns.spawnTamedWolf(helper, new BlockPos(6, 2, 4), owner.getUUID()); // within voice

        WhistleActions.LocateResult result = WhistleActions.locate(owner);
        helper.assertValueEqual(result.sightings().size(), 0, "a pet within the whistle's voice is never a locator line");
        helper.assertValueEqual(result.overflow(), 0, "no overflow when nothing is beyond earshot");

        near.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctLocate")
    public void locateCapsTheListAndCountsTheOverflow(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 10, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        List<Wolf> pack = new ArrayList<>();
        // Twelve pets fanned south of the player, each 22..27 blocks away — well past
        // the 20-block voice, so all twelve count as sightings, two of them as overflow past the cap.
        for (int z = 26; z <= 30; z += 2) {
            for (int x = 2; x <= 8; x += 2) {
                pack.add(PetSpawns.spawnTamedWolf(helper, new BlockPos(x, 2, z), owner.getUUID()));
            }
        }

        WhistleActions.LocateResult result = WhistleActions.locate(owner);
        helper.assertValueEqual(result.sightings().size(), WhistleLocator.MAX_LINES, "the census caps at the line limit");
        helper.assertValueEqual(result.overflow(), 2, "the two pets past the cap are counted as overflow");

        pack.forEach(Animal::discard);
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctLocate")
    public void locateReportsAGuardingPetsPosture(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 8, 8);
        ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
        // A pet posted to guard (order stands it and writes the GUARD attachment), then left behind
        // beyond the whistle's voice — its posture reads "guarding", not "following".
        Wolf posted = PetSpawns.spawnTamedWolf(helper, new BlockPos(30, 2, 4), owner.getUUID());
        posted.setOrderedToSit(false);
        posted.setAttached(InstinctAttachments.GUARD,
                new GuardData(helper.absolutePos(new BlockPos(30, 2, 4))));

        WhistleActions.LocateResult result = WhistleActions.locate(owner);
        helper.assertValueEqual(result.sightings().size(), 1, "the posted pet beyond earshot is listed");
        helper.assertValueEqual(result.sightings().get(0).state(), WhistleLocator.PetState.GUARDING,
                "a guarding pet reads guarding, not following");

        posted.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctLocateCooldown")
    public void locateAppliesACooldownFloorEvenWithTheKnobZeroed(GameTestHelper helper) {
        // Locate's world-wide scan must not be floodable: even with whistleCooldownTicks zeroed, a
        // locate press floors the whistle cooldown so the next press is gated.
        boolean savedEnabled = InstinctConfig.get().enableWhistle;
        int savedCooldown = InstinctConfig.get().whistleCooldownTicks;
        InstinctConfig.get().enableWhistle = true;
        InstinctConfig.get().whistleCooldownTicks = 0;
        try {
            TestFloors.buildFloor(helper, 8, 8);
            ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
            helper.assertFalse(owner.getCooldowns().isOnCooldown(InstinctItems.COMMAND_WHISTLE),
                    "the whistle starts off cooldown");

            WhistleActions.performLocate(owner);
            helper.assertTrue(owner.getCooldowns().isOnCooldown(InstinctItems.COMMAND_WHISTLE),
                    "a locate press floors the cooldown even with the config knob at 0");

            owner.discard();
            helper.succeed();
        } finally {
            InstinctConfig.get().enableWhistle = savedEnabled;
            InstinctConfig.get().whistleCooldownTicks = savedCooldown;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctLocateDisabled")
    public void locateIsInertWhenWhistleDisabled(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableWhistle;
        InstinctConfig.get().enableWhistle = false;
        try {
            // The distant pet is genuinely enumerable, so the empty result reflects the disabled flag
            // rather than a pet the census could never have seen.
            TestFloors.buildFloor(helper, 8, 8);
            ServerPlayer owner = mockPlayer(helper, new BlockPos(4, 2, 4));
            Wolf far = PetSpawns.spawnTamedWolf(helper, new BlockPos(30, 2, 4), owner.getUUID());

            WhistleActions.LocateResult result = WhistleActions.locate(owner);
            helper.assertValueEqual(result.sightings().size(), 0,
                    "with the whistle disabled the locator reports nothing, even with a distant pet");

            far.discard();
            owner.discard();
            helper.succeed();
        } finally {
            InstinctConfig.get().enableWhistle = saved;
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    @GameTest(template = EMPTY_STRUCTURE)
    public void togglingTenPetsGrantsPackLeader(GameTestHelper helper) {
        TestFloors.buildFloor(helper, 12, 12);
        ServerPlayer owner = listeningPlayer(helper, new BlockPos(5, 2, 5));
        ServerPlayer bystander = listeningPlayer(helper, new BlockPos(6, 2, 5));
        for (int i = 0; i < 10; i++) {
            PetSpawns.spawnTamedWolf(helper, new BlockPos(3 + (i % 5), 2, 3 + (i / 5)), owner.getUUID());
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
     * A three-cow herd just outside the 5-block arrival ring (~7 blocks from the player at 2,2,3),
     * fanned across z so each animal crosses the ring in its own lane before the herd reconverges and
     * piles up. cows.get(0) is the round-up target. The short travel keeps convergence robust against
     * pathfinding RNG while still starting the herd outside the arrival ring.
     */
    private static List<Cow> spawnHerd(GameTestHelper helper) {
        List<Cow> cows = new ArrayList<>();
        // Fanned across z inside the drive lane's entity-ticking box (x 0..15, z 0..5), each ~7 blocks
        // from the player at (2, 3) — well past the 5-block arrival, so every cow must be driven in.
        cows.add(helper.spawn(EntityType.COW, new BlockPos(9, 2, 3)));
        cows.add(helper.spawn(EntityType.COW, new BlockPos(9, 2, 2)));
        cows.add(helper.spawn(EntityType.COW, new BlockPos(9, 2, 4)));
        return cows;
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
}
