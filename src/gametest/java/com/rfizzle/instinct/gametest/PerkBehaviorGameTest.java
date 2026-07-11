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

import java.lang.reflect.Method;

/**
 * SPEC §3 perk behaviors: placid suppresses the panic sprint unless the animal is on fire, fertile
 * shortens the post-breed love cooldown by grade, and the hardy/fleet attribute bonuses apply once
 * and never stack across re-assertion. The placid decision is asserted through the goal's own
 * {@code isCalm} predicate (deterministic), not through vanilla's random flee-position search.
 */
public class PerkBehaviorGameTest implements FabricGameTest {

    private static final double EPSILON = 1e-4;

    @GameTest(template = EMPTY_STRUCTURE)
    public void placidHoldsCalmUnlessBurning(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        PlacidPanicGoal panic = placidGoal(helper, cow);

        // Placid and off fire: the goal stands down (isCalm), and that gates canUse deterministically —
        // it returns false before vanilla's random flee search ever runs.
        setPerk(cow, Grade.STURDY, Perk.PLACID);
        helper.assertTrue(isCalm(helper, panic), "a placid cow off fire holds calm");
        helper.assertFalse(panic.canUse(), "a calm cow never starts the panic goal");

        // A non-placid cow is never calm — it panics exactly like vanilla.
        setPerk(cow, Grade.STURDY, Perk.HARDY);
        helper.assertFalse(isCalm(helper, panic), "a non-placid cow is not suppressed");

        // Placid but on fire: flight overrides calm.
        setPerk(cow, Grade.STURDY, Perk.PLACID);
        cow.setRemainingFireTicks(100);
        helper.assertFalse(isCalm(helper, panic), "fire overrides a placid cow's calm");

        cow.discard();
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

    /** Reads the goal's private placid decision — deterministic, unlike the random flee search. */
    private static boolean isCalm(GameTestHelper helper, PlacidPanicGoal goal) {
        Method method;
        try {
            method = PlacidPanicGoal.class.getDeclaredMethod("isCalm");
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            helper.fail("PlacidPanicGoal.isCalm not found — signature changed? " + e);
            return false;
        }
        try {
            return (boolean) method.invoke(goal);
        } catch (ReflectiveOperationException e) {
            helper.fail("PlacidPanicGoal.isCalm threw: " + e);
            return false;
        }
    }
}
