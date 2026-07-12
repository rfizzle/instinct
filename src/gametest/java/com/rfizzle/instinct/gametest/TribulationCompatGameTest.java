package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.InstinctAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;

import java.util.UUID;

/**
 * SPEC §Compat, Tribulation (consumer): with Tribulation absent from the gametest classpath (it is
 * {@code modCompileOnly}, never a runtime dependency), no doubling provider is ever registered, so
 * the veterancy rate stays exactly the un-integrated 1.0 — the graceful-degradation contract.
 */
public class TribulationCompatGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void veterancyRateIsBaseWithoutTribulation(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 2, 2));
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());

        helper.assertValueEqual(InstinctAPI.resolveVeterancyRate(wolf), 1.0,
                "with Tribulation absent, the veterancy rate is exactly 1.0");
        helper.succeed();
    }
}
