package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.PetSpawns;
import com.rfizzle.instinct.item.KeepsakeEngraving;
import com.rfizzle.instinct.keepsake.KeepsakeHandler;
import com.rfizzle.instinct.registry.InstinctDataComponents;
import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * SPEC §7 keepsake collar: a tamed pet lost beyond saving — to fire, lava, or the void — leaves an
 * engraved collar carrying its name and veterancy standing; every other loser (a mount, an untamed
 * animal, livestock) and the disabled config leave nothing; a void loss lays the collar on safe
 * ground rather than dropping it below the world. The lava-death cases exercise the {@code AFTER_DEATH}
 * wiring end to end; the void-placement case drives {@link KeepsakeHandler#dropKeepsake} directly for
 * determinism (a real void death is unavailable inside a floating test region).
 */
public class KeepsakeGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void lavaLossLeavesAnEngravedCollar(GameTestHelper helper) {
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        wolf.setCustomName(Component.literal("Rex"));
        VeterancyHandler.setAccruedDays(wolf, 60.0); // rank 3 at default thresholds

        wolf.hurt(helper.getLevel().damageSources().lava(), 1000.0F);
        helper.assertFalse(wolf.isAlive(), "lava is a real death for a pet");

        ItemEntity collar = singleCollar(helper);
        KeepsakeEngraving engraving = collar.getItem().get(InstinctDataComponents.KEEPSAKE_ENGRAVING);
        helper.assertTrue(engraving != null, "the dropped collar carries an engraving");
        helper.assertTrue(engraving.petName().getString().equals("Rex"), "the engraving names the pet");
        helper.assertValueEqual(engraving.rank(), 3, "the engraving snapshots the veterancy rank");
        helper.assertValueEqual(engraving.daysSeen(), 60, "the engraving snapshots days at your side");
        collar.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void unrankedPetStillLeavesACollarWithNoRank(GameTestHelper helper) {
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3)); // fresh tame — 0 days, rank 0

        wolf.hurt(helper.getLevel().damageSources().lava(), 1000.0F);

        ItemEntity collar = singleCollar(helper);
        KeepsakeEngraving engraving = collar.getItem().get(InstinctDataComponents.KEEPSAKE_ENGRAVING);
        helper.assertTrue(engraving != null, "even an unranked tame leaves a collar");
        helper.assertValueEqual(engraving.rank(), 0, "an unranked pet's engraving carries rank 0");
        helper.assertValueEqual(engraving.daysSeen(), 0, "a fresh tame has seen 0 days");
        collar.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mountUntamedAndLivestockLeaveNoCollar(GameTestHelper helper) {
        // A tamed mount (horse family) is not a TamableAnimal pet — the AFTER_DEATH gate rejects it.
        Horse horse = PetSpawns.spawnAt(helper, EntityType.HORSE, new BlockPos(1, 2, 1));
        horse.setTamed(true);
        horse.hurt(helper.getLevel().damageSources().lava(), 1000.0F);

        // An untamed wolf is a pet species but not owned — dropKeepsake rejects the un-tamed pet.
        Wolf untamed = PetSpawns.spawnAt(helper, EntityType.WOLF, new BlockPos(3, 2, 3));
        untamed.hurt(helper.getLevel().damageSources().lava(), 1000.0F);

        // Livestock is not a TamableAnimal at all.
        Cow cow = PetSpawns.spawnAt(helper, EntityType.COW, new BlockPos(5, 2, 5));
        cow.hurt(helper.getLevel().damageSources().lava(), 1000.0F);

        helper.assertTrue(collarsIn(helper).isEmpty(),
                "no mount, untamed animal, or livestock leaves a keepsake collar");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void voidLossLaysTheCollarOnSafeGround(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3));

        KeepsakeHandler.dropKeepsake(wolf, helper.getLevel().damageSources().fellOutOfWorld());

        ItemEntity collar = singleCollar(helper);
        helper.assertTrue(collar.getY() > helper.getLevel().getMinBuildHeight() + 1,
                "a void loss lays the collar on safe ground, not below the world");
        collar.discard();
        wolf.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctKeepsakeDisabled")
    public void disabledConfigLeavesNoCollar(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableKeepsakeCollar;
        try {
            InstinctConfig.get().enableKeepsakeCollar = false;
            Wolf wolf = PetSpawns.spawnTamedWolf(helper, new BlockPos(3, 2, 3));
            wolf.hurt(helper.getLevel().damageSources().lava(), 1000.0F);
            helper.assertFalse(wolf.isAlive(), "the pet still dies to lava");
            helper.assertTrue(collarsIn(helper).isEmpty(), "with the feature off, no collar drops");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableKeepsakeCollar = saved;
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private static List<ItemEntity> collarsIn(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
        AABB box = new AABB(origin).inflate(24.0);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.getItem().is(InstinctItems.KEEPSAKE_COLLAR));
    }

    private static ItemEntity singleCollar(GameTestHelper helper) {
        List<ItemEntity> collars = collarsIn(helper);
        helper.assertTrue(collars.size() == 1, "exactly one keepsake collar dropped, found " + collars.size());
        return collars.get(0);
    }
}
