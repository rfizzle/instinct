package com.rfizzle.instinct.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class SkeletonGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void modLoads(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded("instinct"),
                "instinct should be loaded in the gametest server");
        helper.succeed();
    }
}
