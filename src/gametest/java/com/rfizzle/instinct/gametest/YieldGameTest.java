package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * SPEC §3 renewable yields: a sheep shears extra wool by grade (+1 sturdy, +2 prime on top of the
 * vanilla 1–3); a chicken's egg interval and a sheep's wool-regrowth graze roll shorten by grade
 * (−10% sturdy, −20% prime) and further by the fertile perk (−15%×grade, config-tunable). The exact
 * bonuses are checked through the handler seams; the shear also runs end to end to prove the
 * guaranteed floor, and the disabled paths (knob at 0, genetics off) are asserted inert.
 */
public class YieldGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void gradedSheepShearsBonusWool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Sheep sturdy = helper.spawn(EntityType.SHEEP, new BlockPos(2, 2, 2));
        GeneticsHandler.setGrade(sturdy, Grade.STURDY);
        Sheep prime = helper.spawn(EntityType.SHEEP, new BlockPos(6, 2, 6));
        GeneticsHandler.setGrade(prime, Grade.PRIME);

        helper.assertValueEqual(GeneticsHandler.shearWoolBonus(sturdy), 1, "sturdy shears +1 wool");
        helper.assertValueEqual(GeneticsHandler.shearWoolBonus(prime), 2, "prime shears +2 wool");

        // End to end: a sturdy sheep can never shear fewer than 2 wool (vanilla 1–3 + 1).
        BlockPos where = sturdy.blockPosition();
        sturdy.shear(SoundSource.PLAYERS);
        int wool = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(where).inflate(4.0))) {
            if (item.getItem().is(Items.WHITE_WOOL)) {
                wool += item.getItem().getCount();
            }
        }
        helper.assertTrue(wool >= 2, "a sturdy sheep shears at least 2 wool, got " + wool);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void gradedChickenLaysFaster(GameTestHelper helper) {
        Chicken ordinary = helper.spawn(EntityType.CHICKEN, new BlockPos(2, 2, 2));
        Chicken sturdy = helper.spawn(EntityType.CHICKEN, new BlockPos(4, 2, 2));
        GeneticsHandler.setGrade(sturdy, Grade.STURDY);
        Chicken prime = helper.spawn(EntityType.CHICKEN, new BlockPos(6, 2, 2));
        GeneticsHandler.setGrade(prime, Grade.PRIME);

        // Vanilla forms the egg timer as scaledEggRandom(...) + 6000, so assert the resulting
        // interval scales by grade across the whole nextInt(6000) domain, including the fast end.
        helper.assertValueEqual(interval(ordinary, 5000), 11000, "ordinary lays at the vanilla interval");
        helper.assertValueEqual(interval(sturdy, 5000), 9900, "sturdy: 11000 → 9900 (−10%)");
        helper.assertValueEqual(interval(prime, 5000), 8800, "prime: 11000 → 8800 (−20%)");

        // The fastest roll (raw = 0) must still be reduced, not clamped back to the vanilla floor:
        // the earlier clamp-to-zero bug restored 6000 here for a prime chicken.
        helper.assertValueEqual(interval(ordinary, 0), 6000, "ordinary floor is the vanilla 6000");
        helper.assertValueEqual(interval(sturdy, 0), 5400, "sturdy floor: 6000 → 5400 (−10%)");
        helper.assertValueEqual(interval(prime, 0), 4800, "prime floor: 6000 → 4800 (−20%), never the vanilla 6000");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fertileChickenLaysEvenFaster(GameTestHelper helper) {
        Chicken prime = helper.spawn(EntityType.CHICKEN, new BlockPos(2, 2, 2));
        GeneticsHandler.setGrade(prime, Grade.PRIME); // prime, no perk
        Chicken primeFertile = helper.spawn(EntityType.CHICKEN, new BlockPos(4, 2, 2));
        primeFertile.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.PRIME.level(), Perk.FERTILE, false, 0L));

        // Grade alone holds the shipped −20%; the fertile perk stacks multiplicatively to 0.80×0.70.
        helper.assertValueEqual(interval(prime, 5000), 8800, "prime non-fertile: 11000 → 8800 (−20%)");
        helper.assertValueEqual(interval(primeFertile, 5000), 6160, "prime fertile: 11000 → 6160 (0.56)");
        helper.assertTrue(interval(primeFertile, 5000) < interval(prime, 5000),
                "a fertile hen lays faster than a plain hen of the same grade");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void gradedSheepRegrowsWoolFaster(GameTestHelper helper) {
        Sheep ordinary = helper.spawn(EntityType.SHEEP, new BlockPos(2, 2, 2));
        Sheep prime = helper.spawn(EntityType.SHEEP, new BlockPos(4, 2, 2));
        GeneticsHandler.setGrade(prime, Grade.PRIME);
        Sheep primeFertile = helper.spawn(EntityType.SHEEP, new BlockPos(6, 2, 6));
        primeFertile.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.PRIME.level(), Perk.FERTILE, false, 0L));
        Sheep primeFertileLamb = helper.spawn(EntityType.SHEEP, new BlockPos(8, 2, 2));
        primeFertileLamb.setBaby(true);
        primeFertileLamb.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.PRIME.level(), Perk.FERTILE, false, 0L));

        // The graze modulus shrinks, raising the per-poll grass-eating chance that re-wools a sheep.
        helper.assertValueEqual(GeneticsHandler.scaledGrazeInterval(ordinary, 1000), 1000,
                "ordinary grazes at the vanilla 1/1000 rate");
        helper.assertValueEqual(GeneticsHandler.scaledGrazeInterval(prime, 1000), 800, "prime: 1000 → 800 (−20%)");
        helper.assertValueEqual(GeneticsHandler.scaledGrazeInterval(primeFertile, 1000), 560,
                "prime fertile: 1000 → 560 (0.56)");
        // A lamb is left on the vanilla cadence even prime + fertile: its graze ages it up, not
        // re-wools, so scaling would speed growth — which §3 fertile scope excludes.
        helper.assertValueEqual(GeneticsHandler.scaledGrazeInterval(primeFertileLamb, 1000), 1000,
                "a graded/fertile lamb grazes at the vanilla rate — no growth speed-up");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void renewableCadenceInertWhenDisabled(GameTestHelper helper) {
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(2, 2, 2));
        sheep.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.PRIME.level(), Perk.FERTILE, false, 0L));
        Chicken hen = helper.spawn(EntityType.CHICKEN, new BlockPos(4, 2, 2));
        hen.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.PRIME.level(), Perk.FERTILE, false, 0L));

        InstinctConfig config = InstinctConfig.get();
        double savedReduction = config.fertileRenewableReduction;
        boolean savedEnabled = config.enableGenetics;
        try {
            // Knob at 0 confines fertile back to breeding: both fall to plain grade-only scaling.
            config.fertileRenewableReduction = 0.0;
            helper.assertValueEqual(GeneticsHandler.scaledGrazeInterval(sheep, 1000), 800,
                    "knob 0 → fertile sheep grazes like a plain prime (0.80)");
            helper.assertValueEqual(interval(hen, 5000), 8800,
                    "knob 0 → fertile hen lays like a plain prime (−20%)");

            // Genetics off: both cadences return the raw vanilla value untouched.
            config.enableGenetics = false;
            helper.assertValueEqual(GeneticsHandler.scaledGrazeInterval(sheep, 1000), 1000,
                    "genetics off → vanilla graze rate");
            helper.assertValueEqual(interval(hen, 5000), 11000, "genetics off → vanilla egg interval");
        } finally {
            config.fertileRenewableReduction = savedReduction;
            config.enableGenetics = savedEnabled;
        }
        helper.succeed();
    }

    /** The egg timer vanilla forms from the scaled random draw at a given raw {@code nextInt(6000)}. */
    private static int interval(Chicken chicken, int raw) {
        return GeneticsHandler.scaledEggRandom(chicken, raw) + 6000;
    }
}
