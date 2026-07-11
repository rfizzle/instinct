package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import com.rfizzle.instinct.genetics.PlacidPanicGoal;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;

/**
 * SPEC §3 perk behaviors: placid suppresses the panic sprint unless the animal is on fire, fertile
 * shortens the post-breed love cooldown by grade, and the hardy/fleet attribute bonuses apply once
 * and never stack across re-assertion. Panic is exercised through the swapped {@link PlacidPanicGoal}
 * on a laid floor so {@code findRandomPosition} is reliable.
 */
public class PerkBehaviorGameTest implements FabricGameTest {

    private static final double EPSILON = 1e-4;

    @GameTest(template = EMPTY_STRUCTURE)
    public void placidCowHoldsUnderDamageButFleesFire(GameTestHelper helper) {
        layFloor(helper);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        Zombie attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
        attacker.setNoAi(true);
        PlacidPanicGoal panic = placidGoal(helper, cow);

        // Placid, not on fire: a mob-attack (a panic cause) does not start the panic sprint.
        cow.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.STURDY.level(), Perk.PLACID, false, 0L));
        cow.hurt(cow.level().damageSources().mobAttack(attacker), 1.0F);
        helper.assertFalse(panic.canUse(), "a placid cow stays calm under a panic-causing hit");

        // Flip to a non-placid perk: the same hit now panics, proving the damage is a real panic cause.
        cow.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.STURDY.level(), Perk.HARDY, false, 0L));
        cow.hurt(cow.level().damageSources().mobAttack(attacker), 1.0F);
        helper.assertTrue(panic.canUse(), "a non-placid cow panics from the same hit");

        // Placid but on fire: flight overrides calm.
        cow.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.STURDY.level(), Perk.PLACID, false, 0L));
        cow.setRemainingFireTicks(100);
        cow.hurt(cow.level().damageSources().onFire(), 1.0F);
        helper.assertTrue(panic.canUse(), "a placid cow on fire flees");

        cow.discard();
        attacker.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fertilePrimeParentsResetToAShorterLoveCooldown(GameTestHelper helper) {
        Cow fertileA = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        Cow fertileB = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
        setPerk(fertileA, Grade.PRIME, Perk.FERTILE);
        setPerk(fertileB, Grade.PRIME, Perk.FERTILE);
        GeneticsGameTest.breed(helper, fertileA, fertileB);
        helper.assertValueEqual(fertileA.getAge(), 4200, "fertile prime → 4200-tick love cooldown");
        helper.assertValueEqual(fertileB.getAge(), 4200, "both fertile prime parents shorten");

        Cow controlA = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        Cow controlB = helper.spawn(EntityType.COW, new BlockPos(6, 2, 5));
        GeneticsGameTest.breed(helper, controlA, controlB);
        helper.assertValueEqual(controlA.getAge(), 6000, "a non-fertile parent keeps the 6000-tick cooldown");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hardyAndFleetApplyOnceAndStayIdempotent(GameTestHelper helper) {
        Cow hardy = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        double baseHealth = hardy.getMaxHealth();
        setPerk(hardy, Grade.PRIME, Perk.HARDY);
        GeneticsHandler.reassertModifiers(hardy);
        GeneticsHandler.reassertModifiers(hardy);
        helper.assertTrue(Math.abs(hardy.getMaxHealth() - baseHealth - 2.0) < EPSILON,
                "hardy prime = +2 max health, not stacked across two re-asserts");

        Cow fleet = helper.spawn(EntityType.COW, new BlockPos(4, 2, 2));
        double baseSpeed = fleet.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        setPerk(fleet, Grade.PRIME, Perk.FLEET);
        GeneticsHandler.reassertModifiers(fleet);
        GeneticsHandler.reassertModifiers(fleet);
        AttributeInstance speed = fleet.getAttribute(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed.getModifier(GeneticsHandler.SPEED_MODIFIER_ID) != null,
                "fleet applies a speed modifier");
        helper.assertTrue(Math.abs(speed.getValue() - baseSpeed * 1.08) < EPSILON,
                "fleet prime = +8% base speed, applied once");
        helper.succeed();
    }

    private static void setPerk(Cow cow, Grade grade, Perk perk) {
        cow.setAttached(InstinctAttachments.GENETICS, new GeneticsData(grade.level(), perk, false, 0L));
        GeneticsHandler.reassertModifiers(cow);
    }

    private static PlacidPanicGoal placidGoal(GameTestHelper helper, Cow cow) {
        for (WrappedGoal wrapped : cow.goalSelector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();
            if (goal instanceof PlacidPanicGoal placid) {
                return placid;
            }
        }
        helper.fail("cow did not receive a PlacidPanicGoal on load");
        return null;
    }

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }
}
