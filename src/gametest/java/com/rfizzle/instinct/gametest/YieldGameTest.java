package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.Grade;
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
 * vanilla 1–3), and a chicken's egg interval shortens by grade (−10% sturdy, −20% prime). The
 * exact bonus is checked through the handler seams; the shear also runs end to end to prove the
 * guaranteed floor.
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

    /** The egg timer vanilla forms from the scaled random draw at a given raw {@code nextInt(6000)}. */
    private static int interval(Chicken chicken, int raw) {
        return GeneticsHandler.scaledEggRandom(chicken, raw) + 6000;
    }
}
