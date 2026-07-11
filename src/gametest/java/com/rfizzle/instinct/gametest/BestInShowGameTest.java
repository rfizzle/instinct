package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;

/**
 * SPEC §Advancements: Best in Show grants when an animal you bred is born prime, and only to the
 * breeder — the love-cause player — not to a bystander. The child is forced prime with a pedigree
 * treat flag so the grant is deterministic.
 */
public class BestInShowGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void breedingAPrimeAnimalGrantsTheBreederOnly(GameTestHelper helper) {
        ServerPlayer breeder = spawnListeningPlayer(helper);
        ServerPlayer bystander = spawnListeningPlayer(helper);
        try {
            Cow a = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
            Cow b = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
            // The treat flag forces the calf prime regardless of the inheritance roll.
            a.setAttached(InstinctAttachments.GENETICS,
                    new GeneticsData(0, com.rfizzle.instinct.api.Perk.NONE, true, 0L));
            a.setInLove(breeder);

            Animal calf = GeneticsGameTest.breed(helper, a, b);
            helper.assertTrue(calf != null && InstinctAPI.getGrade(calf) == Grade.PRIME,
                    "the calf is born prime");

            helper.assertTrue(isGranted(helper, breeder), "the breeder earns Best in Show");
            helper.assertFalse(isGranted(helper, bystander), "a bystander does not");
            helper.succeed();
        } finally {
            breeder.discard();
            bystander.discard();
        }
    }

    private static ServerPlayer spawnListeningPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        return player;
    }

    private static boolean isGranted(GameTestHelper helper, ServerPlayer player) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(Instinct.id("best_in_show"));
        helper.assertTrue(holder != null, "best_in_show advancement is loaded (datagen output present)");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
