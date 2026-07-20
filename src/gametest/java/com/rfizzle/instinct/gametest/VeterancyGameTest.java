package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.VeterancyData;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.gametest.util.PetSpawns;
import com.rfizzle.instinct.veterancy.Veterancy;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;

import java.util.UUID;

import static com.rfizzle.instinct.Instinct.id;

/**
 * SPEC §2 Pet Veterancy: the accrual engine, rank derivation, the fixed-id attribute bonuses
 * (replace, never stack; health-only for species without an attack attribute), the
 * {@code /instinct set veterancy} re-derivation, the Old Friend advancement, and the
 * {@code enableVeterancy} kill switch. Tests drive {@link VeterancyHandler}'s passes directly —
 * the 200-tick live cadence is timing, not logic.
 */
public class VeterancyGameTest implements FabricGameTest {

    private static final double EPSILON = 1e-4;

    @GameTest(template = EMPTY_STRUCTURE)
    public void crossingTheFirstThresholdRanksUpBuffsAndHeals(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 2));
        double baseMax = wolf.getMaxHealth();
        wolf.setHealth((float) baseMax);
        // A fresh gametest world's game time is near zero, so "10 days ago" is not a settable
        // timestamp. Model the same crossing exactly: the pet sits half the real elapsed gap
        // below the threshold (last accrued at t=1, the world's dawn), so the live pass carries
        // it across the 10-day line whatever the world's age.
        long now = helper.getLevel().getGameTime();
        double gapDays = (now - 1) / Veterancy.TICKS_PER_DAY;
        helper.assertTrue(gapDays > 0.0, "precondition: some game time has elapsed");
        wolf.setAttached(InstinctAttachments.VETERANCY,
                new VeterancyData(10.0 - gapDays / 2.0, 1L));

        VeterancyHandler.accrualPass();

        helper.assertTrue(InstinctAPI.getVeterancyDays(wolf) >= 10.0,
                "the live pass should have accrued the pet across the 10-day threshold");
        helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 1, "rank at 10+ days");
        helper.assertTrue(Math.abs(wolf.getMaxHealth() - baseMax - 2.0) < EPSILON,
                "rank 1 adds +2.0 max health");
        helper.assertTrue(Math.abs(wolf.getHealth() - baseMax - 2.0) < EPSILON,
                "the rank-up heals by the increment — no phantom empty hearts");
        wolf.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void setVeterancyRederivesRankAndBonusesImmediately(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 2));
        double baseMax = wolf.getMaxHealth();
        double baseAttack = wolf.getAttributeValue(Attributes.ATTACK_DAMAGE);

        int rank = VeterancyHandler.setAccruedDays(wolf, 60.0);
        helper.assertValueEqual(rank, 3, "60 days derives rank 3");
        helper.assertTrue(Math.abs(wolf.getMaxHealth() - baseMax - 6.0) < EPSILON,
                "rank 3 max health is cumulative +6.0");
        helper.assertTrue(Math.abs(wolf.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack - 3.0) < EPSILON,
                "rank 3 attack damage is cumulative +3.0");

        // Re-applying replaces, never stacks.
        VeterancyHandler.reassert(wolf);
        helper.assertTrue(Math.abs(wolf.getMaxHealth() - baseMax - 6.0) < EPSILON,
                "a second derivation must not stack a second modifier");

        // Demotion drops the bonuses and clamps current health to the lowered max.
        wolf.setHealth(wolf.getMaxHealth());
        helper.assertValueEqual(VeterancyHandler.setAccruedDays(wolf, 0.0), 0, "0 days derives rank 0");
        AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(health != null && health.getModifier(VeterancyHandler.HEALTH_MODIFIER_ID) == null,
                "demotion to rank 0 removes the health modifier");
        helper.assertTrue(wolf.getHealth() <= wolf.getMaxHealth() + EPSILON,
                "current health is clamped to the demoted max");
        wolf.discard();
        helper.succeed();
    }

    /**
     * SPEC §2 keys the damage bonus on the attack attribute's presence. On 1.21.1 every vanilla
     * pet (parrots included, at 3.0) carries {@code ATTACK_DAMAGE}, so the health-only branch is
     * reachable only by modded species — this pins the vanilla side: a non-combat pet ranks up
     * with both fixed-id bonuses applied, harmlessly for a species that never attacks.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void parrotRanksUpWithBothBonuses(GameTestHelper helper) {
        Parrot parrot = helper.spawn(EntityType.PARROT, new BlockPos(2, 2, 2));
        parrot.setNoAi(true);
        parrot.setTame(true, false);
        parrot.setOwnerUUID(UUID.randomUUID());
        double baseMax = parrot.getMaxHealth();

        int rank = VeterancyHandler.setAccruedDays(parrot, 30.0);
        helper.assertValueEqual(rank, 2, "30 days derives rank 2");
        helper.assertTrue(Math.abs(parrot.getMaxHealth() - baseMax - 4.0) < EPSILON,
                "rank 2 health bonus applies");
        AttributeInstance attack = parrot.getAttribute(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(attack != null && attack.getModifier(VeterancyHandler.ATTACK_MODIFIER_ID) != null,
                "1.21.1 parrots carry an attack attribute, so the damage bonus rides it");
        parrot.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void setCommandIsOpGatedAndInfoIsPublic(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher()
                .getRoot().getChild("instinct");
        helper.assertTrue(root != null, "/instinct should be registered");
        var nonOp = helper.getLevel().getServer().createCommandSourceStack().withPermission(0);
        var op = helper.getLevel().getServer().createCommandSourceStack().withPermission(2);
        helper.assertTrue(root.getChild("info").canUse(nonOp), "info should be public");
        helper.assertFalse(root.getChild("set").canUse(nonOp), "set should deny non-ops");
        helper.assertTrue(root.getChild("set").canUse(op), "set should allow ops");
        helper.assertTrue(root.getChild("set").getChild("veterancy") != null,
                "set veterancy should be registered");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void oldFriendGrantsForTheOwnerOnly(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer bystander = MockPlayers.serverPlayerInLevel(helper);
        Wolf wolf = null;
        try {
            // Reloading against the live manager guarantees the freshly-registered trigger has
            // advancement listeners for these players before the first fire.
            owner.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
            bystander.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());

            wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 2));
            wolf.setOwnerUUID(owner.getUUID());
            VeterancyHandler.setAccruedDays(wolf, 60.0);

            AdvancementHolder oldFriend = helper.getLevel().getServer().getAdvancements()
                    .get(id("old_friend"));
            helper.assertTrue(oldFriend != null, "old_friend advancement should be loaded (datagen output)");
            helper.assertTrue(owner.getAdvancements().getOrStartProgress(oldFriend).isDone(),
                    "the owner of a rank-3 pet earns Old Friend");
            helper.assertFalse(bystander.getAdvancements().getOrStartProgress(oldFriend).isDone(),
                    "a bystander earns nothing");
            helper.succeed();
        } finally {
            if (wolf != null) {
                wolf.discard();
            }
            owner.discard();
            bystander.discard();
        }
    }

    // Own batch: this flips the master toggle, which would blind every concurrently running
    // veterancy structure sharing the tick.
    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctVeterancyOff")
    public void disabledVeterancyStopsAccrualAndStripsBonuses(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableVeterancy;
        Wolf wolf = null;
        try {
            wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 2));
            VeterancyHandler.setAccruedDays(wolf, 60.0);
            double buffedMax = wolf.getMaxHealth();

            InstinctConfig.get().enableVeterancy = false;
            wolf.setAttached(InstinctAttachments.VETERANCY, new VeterancyData(60.0, 1L));
            VeterancyHandler.accrualPass();

            helper.assertTrue(Math.abs(InstinctAPI.getVeterancyDays(wolf) - 60.0) < EPSILON,
                    "accrual stops while disabled");
            AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
            helper.assertTrue(health != null && health.getModifier(VeterancyHandler.HEALTH_MODIFIER_ID) == null,
                    "bonuses are removed while disabled");
            helper.assertTrue(wolf.getMaxHealth() < buffedMax, "max health returns to base");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableVeterancy = saved;
            if (wolf != null) {
                wolf.discard();
            }
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void untamedPetKeepsDaysButLosesBonuses(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 2));
        VeterancyHandler.setAccruedDays(wolf, 30.0);
        helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 2, "precondition: rank 2");

        wolf.setTame(false, false);
        VeterancyHandler.accrualPass();

        AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(health != null && health.getModifier(VeterancyHandler.HEALTH_MODIFIER_ID) == null,
                "an untamed animal carries no veterancy bonus");
        VeterancyData data = wolf.getAttached(InstinctAttachments.VETERANCY);
        helper.assertTrue(data != null && Math.abs(data.accruedDays() - 30.0) < EPSILON,
                "the attachment is retained — re-taming resumes from prior days");
        wolf.discard();
        helper.succeed();
    }

    /** Rank derives against the live threshold list — config edits promote or demote on re-derive. */
    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctThresholds")
    public void thresholdEditsPromoteAndDemoteOnRederive(GameTestHelper helper) {
        var saved = InstinctConfig.get().veterancyThresholdDays;
        Wolf wolf = null;
        try {
            wolf = spawnTamedWolf(helper, new BlockPos(2, 2, 2));
            VeterancyHandler.setAccruedDays(wolf, 25.0);
            helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 1, "default thresholds: rank 1");

            InstinctConfig.get().veterancyThresholdDays = java.util.List.of(5, 10, 20);
            helper.assertValueEqual(VeterancyHandler.reassert(wolf), 3, "shortened thresholds promote");

            InstinctConfig.get().veterancyThresholdDays = java.util.List.of(50, 100, 200);
            helper.assertValueEqual(VeterancyHandler.reassert(wolf), 0, "lengthened thresholds demote");
            AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH);
            helper.assertTrue(health != null && health.getModifier(VeterancyHandler.HEALTH_MODIFIER_ID) == null,
                    "attributes follow the demotion");
            helper.succeed();
        } finally {
            InstinctConfig.get().veterancyThresholdDays = saved;
            if (wolf != null) {
                wolf.discard();
            }
        }
    }

    /**
     * A tamed wolf frozen in place, shared with {@code RankBehaviorsGameTest} and
     * {@code FriendlyFireGameTest}. NoAi lands after the load so the wolf still receives the
     * tamed-only goal injection these suites assert against; it only stops it wandering after.
     */
    static Wolf spawnTamedWolf(GameTestHelper helper, BlockPos rel) {
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, rel);
        wolf.setNoAi(true);
        return wolf;
    }
}
