package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.VeterancyData;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.inspection.Inspection;
import com.rfizzle.instinct.veterancy.Veterancy;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * SPEC §2 rank behaviors: Warning (once per threat per owner per 300 ticks, sitting pets warn
 * without standing), "Knows your swing" (the owner's sweep skips rank-2+ pets; direct hits and
 * lower ranks unchanged), Mentor (nearby lower-rank pets accrue ×1.25, composing with the rate
 * provider), and the {@code enableRankBehaviors} toggle disabling exactly the three. Tests drive
 * the handler's passes directly and run in isolated batches where cross-structure entities
 * (rank-3 mentors, targeting monsters, global pass counts) could bleed between concurrent tests.
 */
public class RankBehaviorsGameTest implements FabricGameTest {

    private static final double EPSILON = 1e-3;

    // Own batch: warningPass() returns a global count, so no other structure may host a
    // targeting monster while this asserts exact counts.
    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctWarning")
    public void seasonedPetWarnsOncePerThreatWindow(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            owner.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

            Wolf wolf = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(3, 2, 2));
            wolf.setOwnerUUID(owner.getUUID());
            VeterancyHandler.setAccruedDays(wolf, 10.0);
            wolf.setOrderedToSit(true);
            wolf.setInSittingPose(true);

            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
            zombie.setNoAi(true);
            zombie.setTarget(owner);

            helper.assertValueEqual(VeterancyHandler.warningPass(), 1,
                    "the seasoned wolf warns about the threat");
            helper.assertTrue(wolf.isOrderedToSit(), "a sitting pet warns without standing");
            helper.assertValueEqual(VeterancyHandler.warningPass(), 0,
                    "the same threat goes quiet for 300 ticks");

            zombie.discard();
            wolf.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctWarning")
    public void rankZeroPetsNeverWarn(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            owner.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

            Wolf wolf = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(3, 2, 2));
            wolf.setOwnerUUID(owner.getUUID());

            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
            zombie.setNoAi(true);
            zombie.setTarget(owner);

            helper.assertValueEqual(VeterancyHandler.warningPass(), 0,
                    "a fresh tame has not learned the warning");

            zombie.discard();
            wolf.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ownersSweepSkipsRankTwoPetButHitsRankZero(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        // Off so the sweep-dodge is the operative rule: blanket friendly-fire protection (§1) would
        // otherwise spare the rank-0 pet too, hiding the rank-gated behavior this test pins.
        boolean savedFriendlyFire = InstinctConfig.get().enableOwnerFriendlyFireProtection;
        try {
            InstinctConfig.get().enableOwnerFriendlyFireProtection = false;
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            owner.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.IRON_SWORD));
            owner.setOnGround(true);
            chargeAttack(helper, owner);

            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
            zombie.setNoAi(true);

            Wolf veteran = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(3, 2, 2));
            veteran.setOwnerUUID(owner.getUUID());
            VeterancyHandler.setAccruedDays(veteran, 30.0);
            Wolf fresh = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(2, 2, 3));
            fresh.setOwnerUUID(owner.getUUID());
            float veteranHealth = veteran.getHealth();
            float freshHealth = fresh.getHealth();
            float zombieHealth = zombie.getHealth();

            owner.attack(zombie);

            helper.assertTrue(zombie.getHealth() < zombieHealth, "the direct hit lands");
            helper.assertTrue(fresh.getHealth() < freshHealth,
                    "a rank-0 wolf beside the target is caught by the sweep");
            helper.assertTrue(Math.abs(veteran.getHealth() - veteranHealth) < EPSILON,
                    "the rank-2 wolf ducks its owner's sweep");

            zombie.discard();
            veteran.discard();
            fresh.discard();
            helper.succeed();
        } finally {
            InstinctConfig.get().enableOwnerFriendlyFireProtection = savedFriendlyFire;
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void directHitsStillLandOnRankTwoPets(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        // Off so a direct hit is observable: blanket friendly-fire protection (§1) blocks the owner's
        // own hits at every rank. The sweep-dodge is the arc-only trick, never full owner immunity.
        boolean savedFriendlyFire = InstinctConfig.get().enableOwnerFriendlyFireProtection;
        try {
            InstinctConfig.get().enableOwnerFriendlyFireProtection = false;
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            owner.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.IRON_SWORD));
            owner.setOnGround(true);
            chargeAttack(helper, owner);

            Wolf veteran = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(3, 2, 2));
            veteran.setOwnerUUID(owner.getUUID());
            VeterancyHandler.setAccruedDays(veteran, 30.0);
            veteran.setHealth(veteran.getMaxHealth());
            float before = veteran.getHealth();

            owner.attack(veteran);

            helper.assertTrue(veteran.getHealth() < before,
                    "the pet learned to duck the arc, not to be immune to its owner");
            veteran.discard();
            helper.succeed();
        } finally {
            InstinctConfig.get().enableOwnerFriendlyFireProtection = savedFriendlyFire;
            owner.discard();
        }
    }

    // Own batch: a rank-3 wolf in a neighboring concurrent structure would mentor this pup and
    // skew the measured rates.
    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctMentor")
    public void mentorSpeedsNearbyPupAccrualAndComposesWithProvider(GameTestHelper helper) {
        Wolf mentor = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(2, 2, 2));
        Wolf pup = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(4, 2, 2));
        try {
            VeterancyHandler.setAccruedDays(mentor, 60.0);
            // A fresh gametest world's game time is near zero, so past timestamps are modeled as
            // "last accrued at the world's dawn" (t=1); the pass accrues the real elapsed gap.
            double gapDays = (helper.getLevel().getGameTime() - 1) / Veterancy.TICKS_PER_DAY;
            helper.assertTrue(gapDays > 0.0, "precondition: some game time has elapsed");

            // The gap beside a Venerable wolf accrues at ×1.25.
            pup.setAttached(InstinctAttachments.VETERANCY, new VeterancyData(0.0, 1L));
            VeterancyHandler.accrualPass();
            double mentored = InstinctAPI.getVeterancyDays(pup);
            helper.assertTrue(Math.abs(mentored - gapDays * 1.25) < EPSILON,
                    "a pup beside a mentor accrues at 1.25x, got " + mentored + " for gap " + gapDays);

            // The same gap without the mentor accrues at ×1.0 (the control pup).
            mentor.discard();
            pup.setAttached(InstinctAttachments.VETERANCY, new VeterancyData(0.0, 1L));
            VeterancyHandler.accrualPass();
            double control = InstinctAPI.getVeterancyDays(pup);
            helper.assertTrue(Math.abs(control - gapDays) < EPSILON,
                    "the control pup accrues at 1.0x, got " + control + " for gap " + gapDays);

            // A registered rate provider multiplies live accrual (2.0 alone here; the 2.0 × 1.25
            // mentor composition is pinned by the Veterancy.liveRate unit test).
            try {
                InstinctAPI.setVeterancyRateProvider(pet -> 2.0);
                pup.setAttached(InstinctAttachments.VETERANCY, new VeterancyData(0.0, 1L));
                VeterancyHandler.accrualPass();
                double provided = InstinctAPI.getVeterancyDays(pup);
                helper.assertTrue(Math.abs(provided - gapDays * 2.0) < EPSILON,
                        "the provider rate multiplies live accrual, got " + provided + " for gap " + gapDays);
            } finally {
                InstinctAPI.setVeterancyRateProvider(pet -> 1.0);
            }
            helper.succeed();
        } finally {
            pup.discard();
            if (mentor.isAlive()) {
                mentor.discard();
            }
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void inspectionAnswersTheOwnerOncePerAcquisition(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer stranger = MockPlayers.serverPlayerInLevel(helper);
        Wolf wolf = null;
        try {
            wolf = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(4, 2, 2));
            wolf.setOwnerUUID(owner.getUUID());

            BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 2));
            for (ServerPlayer player : new ServerPlayer[]{owner, stranger}) {
                player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, wolf.getEyePosition());
                player.setShiftKeyDown(true);
            }

            helper.assertTrue(Inspection.tickPlayer(owner), "the crouching owner gets the line");
            helper.assertFalse(Inspection.tickPlayer(owner), "the same acquisition answers once");

            owner.setShiftKeyDown(false);
            helper.assertFalse(Inspection.tickPlayer(owner), "no line without crouch");
            owner.setShiftKeyDown(true);
            helper.assertTrue(Inspection.tickPlayer(owner), "re-crouching is a new acquisition");

            helper.assertFalse(Inspection.tickPlayer(stranger), "the line answers only the owner");
            helper.succeed();
        } finally {
            if (wolf != null) {
                wolf.discard();
            }
            owner.discard();
            stranger.discard();
        }
    }

    // Own batch: flips enableRankBehaviors, which would blind concurrent behavior tests.
    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctBehaviorsOff")
    public void disabledRankBehaviorsKeepAccrualAndBonuses(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableRankBehaviors;
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        Wolf wolf = null;
        Wolf mentor = null;
        Zombie zombie = null;
        try {
            InstinctConfig.get().enableRankBehaviors = false;
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            owner.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

            wolf = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(3, 2, 2));
            wolf.setOwnerUUID(owner.getUUID());
            helper.assertValueEqual(VeterancyHandler.setAccruedDays(wolf, 10.0), 1,
                    "ranks and bonuses stay on — only the three behaviors go");

            zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
            zombie.setNoAi(true);
            zombie.setTarget(owner);
            helper.assertValueEqual(VeterancyHandler.warningPass(), 0, "no warning while disabled");

            mentor = VeterancyGameTest.spawnTamedWolf(helper, new BlockPos(4, 2, 4));
            VeterancyHandler.setAccruedDays(mentor, 60.0);
            double gapDays = (helper.getLevel().getGameTime() - 1) / Veterancy.TICKS_PER_DAY;
            wolf.setAttached(InstinctAttachments.VETERANCY, new VeterancyData(0.0, 1L));
            VeterancyHandler.accrualPass();
            helper.assertTrue(Math.abs(InstinctAPI.getVeterancyDays(wolf) - gapDays) < EPSILON,
                    "no mentor bonus while disabled");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableRankBehaviors = saved;
            if (wolf != null) {
                wolf.discard();
            }
            if (mentor != null) {
                mentor.discard();
            }
            if (zombie != null) {
                zombie.discard();
            }
            owner.discard();
        }
    }

    /**
     * Fully charges the player's attack meter: the sweep's {@code bl} gate needs
     * {@code getAttackStrengthScale > 0.9}, and the ticker is protected — reflection is the
     * established pattern for private state in gametests.
     */
    private static void chargeAttack(GameTestHelper helper, ServerPlayer player) {
        try {
            var field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 1000);
        } catch (ReflectiveOperationException e) {
            helper.fail("LivingEntity.attackStrengthTicker not found — signature changed? " + e);
        }
    }
}
