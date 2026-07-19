package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.InstinctAnimalDownedCallback;
import com.rfizzle.instinct.api.InstinctAnimalRevivedCallback;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.coverage.CoverageResolver;
import com.rfizzle.instinct.coverage.MembershipRule;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.selfpreservation.CreeperBerthGoal;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SPEC §1 + §7 extended to the mounts set (the horse family, issue #17): a tamed mount gains
 * hazard-aware pathing and the creeper berth while riderless, and collapses downed instead of
 * dying, revivable by the same golden apple / vet kit path as pets — but with no sit pose and no
 * veterancy rank penalty, and it ejects its rider on going down. The horse family resolves into
 * the mounts set (and stays out of pets and livestock). Structure region is Fabric's 8x8x8 empty
 * template; movement tests lay their own stone floor and work on the y=2 surface. Berth tests get
 * their own {@code batch} so a live fuse never falls inside another test's awareness radius.
 */
public class MountsGameTest implements FabricGameTest {

    private static final int SIZE = 8;

    // --- Coverage ---------------------------------------------------------------------------

    @GameTest(template = EMPTY_STRUCTURE)
    public void horseFamilyResolvesMountViaShippedTag(GameTestHelper helper) {
        for (EntityType<?> type : new EntityType<?>[]{
                EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
                EntityType.CAMEL, EntityType.LLAMA, EntityType.TRADER_LLAMA}) {
            CoverageResolver.Membership membership = AnimalCoverage.membershipOf(type);
            helper.assertTrue(membership.mount(), type + " should be in the mounts set");
            helper.assertValueEqual(membership.mountRule(), MembershipRule.TAG, type + " granting rule");
            helper.assertFalse(membership.pet(), type + " is not a pet");
            helper.assertFalse(membership.livestock(), type + " stays out of livestock");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void undeadHorsesAreNotMounts(GameTestHelper helper) {
        helper.assertFalse(AnimalCoverage.isMount(EntityType.SKELETON_HORSE),
                "the skeleton horse is not a husbandry mount");
        helper.assertFalse(AnimalCoverage.isMount(EntityType.ZOMBIE_HORSE),
                "the zombie horse is not a husbandry mount");
        helper.succeed();
    }

    // --- Self-preservation (§1) -------------------------------------------------------------

    @GameTest(template = EMPTY_STRUCTURE)
    public void tamedHorsePathsAroundLava(GameTestHelper helper) {
        buildFloor(helper);
        // Lava strip across z=3 at x=0..3, carved into the walking layer; the x=4..7 gap is the
        // only safe route. Wider than the wolf test's gap so a horse's ~1.4-block footprint fits
        // through without sampling a lava column (its 2x2 pathfinding footprint needs two clear
        // columns side by side).
        for (int x = 0; x <= 3; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.LAVA.defaultBlockState());
        }
        Horse horse = spawnTamedHorse(helper, new BlockPos(1, 2, 1));
        horse.setNoAi(true);
        horse.setOnGround(true);
        BlockPos target = helper.absolutePos(new BlockPos(1, 2, 6));
        helper.succeedWhen(() -> {
            Path path = horse.getNavigation().createPath(target, 0);
            helper.assertTrue(path != null && path.canReach(),
                    "a tamed horse should find a safe route around the lava strip");
            for (int i = 0; i < path.getNodeCount(); i++) {
                var node = path.getNode(i);
                BlockPos rel = new BlockPos(node.x, node.y, node.z).subtract(helper.absolutePos(BlockPos.ZERO));
                helper.assertFalse(rel.getZ() == 3 && rel.getX() >= 0 && rel.getX() <= 3,
                        "path must not cross the lava strip (node at relative " + rel + ")");
            }
            horse.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "instinctMountBerth1")
    public void riderlessHorseFleesSwellingCreeper(GameTestHelper helper) {
        buildFloor(helper);
        Horse horse = spawnTamedHorse(helper, new BlockPos(2, 2, 2));
        Creeper creeper = spawnFuseOnlyCreeper(helper, new BlockPos(2, 2, 5));
        Vec3 creeperPos = creeper.position();
        creeper.ignite();
        helper.succeedWhen(() -> {
            helper.assertTrue(horse.position().distanceTo(creeperPos) >= 4.0,
                    "a riderless mount should flee to at least creeperBerthBlocks clear of the fuse");
            horse.discard();
            creeper.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "instinctMountBerth2")
    public void riddenHorseIgnoresCreeperBerth(GameTestHelper helper) {
        buildFloor(helper);
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        Creeper creeper = spawnFuseOnlyCreeper(helper, new BlockPos(3, 2, 4));
        ServerPlayer rider = MockPlayers.serverPlayerInLevel(helper);
        rider.startRiding(horse, true);
        helper.assertTrue(horse.isVehicle(), "precondition: the horse is being ridden");
        creeper.ignite();
        // A rider is in control (SPEC §1: mounts flee only while riderless); the berth must not
        // engage, so after the fuse has been live a while the horse is still beside the creeper.
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(horse.position().distanceTo(creeper.position()) < 4.0,
                    "a ridden mount must not flee the creeper — its rider is in control");
            rider.stopRiding();
            rider.discard();
            horse.discard();
            creeper.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void camelGetsHazardPathingAndDownedButNoBerthGoal(GameTestHelper helper) {
        // The camel is an AbstractHorse but brain-driven (empty goal selector), so the goal-based
        // berth must NOT be injected — it would fight the brain. Hazard maluses and downing, which
        // are AI-architecture-agnostic, still apply.
        Camel camel = EntityType.CAMEL.create(helper.getLevel());
        if (camel == null) {
            throw new IllegalStateException("could not create a camel");
        }
        BlockPos abs = helper.absolutePos(new BlockPos(3, 2, 3));
        camel.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        camel.setTamed(true);
        camel.setOwnerUUID(java.util.UUID.randomUUID());
        helper.getLevel().addFreshEntity(camel);

        helper.assertValueEqual(countBerthGoals(camel), 0L,
                "a brain-driven camel is injected no goal-based creeper berth");
        helper.assertTrue(camel.getPathfindingMalus(PathType.LAVA) < 0.0f,
                "hazard maluses still apply to a camel (architecture-agnostic)");
        helper.assertTrue(camel.getPathfindingMalus(PathType.DAMAGE_FIRE) < 0.0f, "fire malus applied");

        downWithArrow(helper, camel);
        helper.assertTrue(InstinctAPI.isDowned(camel), "a tamed camel still downs instead of dying");
        helper.assertTrue(camel.isNoAi(), "a downed camel's AI is stopped");
        camel.discard();
        helper.succeed();
    }

    // --- Downed & revival (§7) --------------------------------------------------------------

    @GameTest(template = EMPTY_STRUCTURE)
    public void lethalBlowDownsTamedHorseAndClearsAttackers(GameTestHelper helper) {
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 3));
        zombie.setTarget(horse);

        downWithArrow(helper, horse);

        helper.assertTrue(horse.isAlive(), "a lethal arrow leaves the mount alive");
        helper.assertTrue(horse.getHealth() == 1.0F, "downed health is pinned to 1.0");
        helper.assertTrue(InstinctAPI.isDowned(horse), "the mount is downed");
        helper.assertTrue(horse.isInvulnerable(), "a downed mount is invulnerable");
        helper.assertTrue(horse.isNoAi(), "a downed mount's AI is stopped");
        helper.assertFalse(horse.canBeSeenAsEnemy(), "a downed mount is untargetable by hostiles");
        helper.assertTrue(zombie.getTarget() == null, "a mob targeting the mount retargets on down");

        horse.discard();
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void downingEjectsTheRider(GameTestHelper helper) {
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        ServerPlayer rider = MockPlayers.serverPlayerInLevel(helper);
        try {
            rider.startRiding(horse, true);
            helper.assertTrue(horse.isVehicle(), "precondition: rider is mounted");
            downWithArrow(helper, horse);
            helper.assertTrue(InstinctAPI.isDowned(horse), "the mount is downed");
            helper.assertFalse(horse.isVehicle(), "a downed mount ejects its rider");
            helper.assertTrue(rider.getVehicle() == null, "the rider is no longer mounted");
            horse.discard();
            helper.succeed();
        } finally {
            rider.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void goldenAppleRevivesHorseWithNoRankPenalty(GameTestHelper helper) {
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        downWithArrow(helper, horse);
        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            InteractionResult result = revive(helper, reviver, horse);

            helper.assertTrue(result == InteractionResult.CONSUME, "the revive consumes the interaction");
            helper.assertFalse(InstinctAPI.isDowned(horse), "the mount is no longer downed");
            float expected = 0.5F * horse.getMaxHealth();
            helper.assertTrue(Math.abs(horse.getHealth() - expected) < 0.01F,
                    "revived to reviveHealthFraction × max health");
            helper.assertTrue(horse.hasEffect(MobEffects.REGENERATION), "revival grants Regeneration");
            helper.assertTrue(horse.isInvulnerable(), "the post-revive invulnerability window is active");
            helper.assertFalse(horse.isNoAi(), "a revived mount has its AI back");
            horse.discard();
            helper.succeed();
        } finally {
            reviver.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void vetKitRevivesHorseToo(GameTestHelper helper) {
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        downWithArrow(helper, horse);
        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(InstinctItems.VET_KIT));
            InteractionResult result = revive(helper, reviver, horse);
            helper.assertTrue(result == InteractionResult.CONSUME, "the vet kit revives the mount");
            helper.assertFalse(InstinctAPI.isDowned(horse), "the mount is revived");
            horse.discard();
            helper.succeed();
        } finally {
            reviver.discard();
        }
    }

    /** Issue #34: the public callbacks are animal-typed, so a mount's transitions signal like a pet's. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void mountTransitionsFireThePublicCallbacks(GameTestHelper helper) {
        AtomicBoolean downedFired = new AtomicBoolean(false);
        AtomicBoolean revivedFired = new AtomicBoolean(false);
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        // Match on UUID, not the entity: a registered listener can never be unregistered, so
        // capturing the entity would pin it and its level for the rest of the run.
        UUID horseId = horse.getUUID();
        InstinctAnimalDownedCallback downedListener = (animal, source) -> {
            if (animal.getUUID().equals(horseId)) {
                downedFired.set(true);
            }
        };
        InstinctAnimalRevivedCallback revivedListener = (animal, reviver, item) -> {
            if (animal.getUUID().equals(horseId)) {
                revivedFired.set(true);
            }
        };
        InstinctAnimalDownedCallback.EVENT.register(downedListener);
        InstinctAnimalRevivedCallback.EVENT.register(revivedListener);

        downWithArrow(helper, horse);
        helper.assertTrue(downedFired.get(), "InstinctAnimalDownedCallback fires for a downed mount");

        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            revive(helper, reviver, horse);
            helper.assertTrue(revivedFired.get(), "InstinctAnimalRevivedCallback fires for a revived mount");
            horse.discard();
            helper.succeed();
        } finally {
            reviver.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void lavaDeathKillsHorseForReal(GameTestHelper helper) {
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        horse.hurt(helper.getLevel().damageSources().lava(), 1000.0F);
        helper.assertFalse(horse.isAlive(), "lava is beyond saving — a real death, no down");
        helper.assertFalse(InstinctAPI.isDowned(horse), "no downed attachment for a lava death");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void untamedHorseDiesNormally(GameTestHelper helper) {
        Horse horse = EntityType.HORSE.create(helper.getLevel());
        if (horse == null) {
            throw new IllegalStateException("could not create a horse");
        }
        BlockPos abs = helper.absolutePos(new BlockPos(3, 2, 3));
        horse.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(horse);
        helper.assertFalse(horse.isTamed(), "precondition: the horse is wild");

        horse.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
        helper.assertFalse(horse.isAlive(), "an untamed mount dies vanilla-style");
        helper.assertFalse(InstinctAPI.isDowned(horse), "an untamed mount never goes down");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void downedStateSurvivesNbtRoundTrip(GameTestHelper helper) {
        Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
        downWithArrow(helper, horse);

        CompoundTag saved = new CompoundTag();
        horse.saveWithoutId(saved);
        horse.discard();

        Horse loaded = EntityType.HORSE.create(helper.getLevel());
        helper.assertTrue(loaded != null, "horse entity should be created");
        loaded.load(saved);

        helper.assertTrue(InstinctAPI.isDowned(loaded), "downed flag survives save/load");
        helper.assertTrue(loaded.isInvulnerable(), "invulnerability survives save/load");
        helper.assertTrue(loaded.isNoAi(), "the AI-stop survives save/load");
        loaded.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctMountDownedDisabled")
    public void disabledConfigIsVanillaDeathForMounts(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableDownedState;
        try {
            InstinctConfig.get().enableDownedState = false;
            Horse horse = spawnTamedHorse(helper, new BlockPos(3, 2, 3));
            horse.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
            helper.assertFalse(horse.isAlive(), "with the feature off, a lethal blow kills vanilla-style");
            helper.assertFalse(InstinctAPI.isDowned(horse), "no new downs occur while disabled");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableDownedState = saved;
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private static void downWithArrow(GameTestHelper helper, PathfinderMob mount) {
        AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
        if (arrow == null) {
            throw new IllegalStateException("could not create an arrow");
        }
        mount.hurt(helper.getLevel().damageSources().arrow(arrow, null), 1000.0F);
        arrow.discard();
    }

    private static long countBerthGoals(PathfinderMob mount) {
        return mount.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> wrapped.getGoal() instanceof CreeperBerthGoal)
                .count();
    }

    private static InteractionResult revive(GameTestHelper helper, ServerPlayer reviver, Horse horse) {
        return UseEntityCallback.EVENT.invoker()
                .interact(reviver, helper.getLevel(), InteractionHand.MAIN_HAND, horse, null);
    }

    /**
     * Spawns a horse that is already tamed when {@code addFreshEntity} fires ENTITY_LOAD — the
     * production path for a tamed mount loading in, which applies maluses and injects the berth
     * goal.
     */
    private static Horse spawnTamedHorse(GameTestHelper helper, BlockPos rel) {
        Horse horse = EntityType.HORSE.create(helper.getLevel());
        if (horse == null) {
            throw new IllegalStateException("could not create a horse");
        }
        BlockPos abs = helper.absolutePos(rel);
        horse.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        horse.setTamed(true);
        horse.setOwnerUUID(UUID.randomUUID());
        helper.getLevel().addFreshEntity(horse);
        return horse;
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

    /**
     * A creeper that can fuse but not hurt the test: NoAI keeps it in place, ExplosionRadius 0
     * makes any detonation harmless, and a 400-tick fuse outlives the test so the fuse never ends
     * (and stops the berth early) before the assertions are met. Tests must discard it.
     */
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
