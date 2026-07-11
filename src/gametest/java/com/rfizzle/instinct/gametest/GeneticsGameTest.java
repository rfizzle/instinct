package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import com.rfizzle.instinct.genetics.ProductTable;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.item.PedigreeTreatItem;
import com.rfizzle.instinct.registry.InstinctItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * SPEC §3 genetics through the real breeding pipeline: grade inheritance under well-fed and crowded
 * conditions, the pedigree treat, the mooshroom-conversion carry, the graded death-drop bonus, and
 * the {@code enableGenetics} freeze. Breeding is driven directly through
 * {@code spawnChildFromBreeding} so the {@code AnimalMixin} hooks fire deterministically. Config
 * mutations run in isolated batches and restore in a finally.
 */
public class GeneticsGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctGeneticsUp")
    public void hayBaleWellFedBreedingUpgradesTheGrade(GameTestHelper helper) {
        double savedUp = InstinctConfig.get().gradeUpgradeChance;
        try {
            InstinctConfig.get().gradeUpgradeChance = 1.0;
            Cow a = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
            Cow b = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
            GeneticsHandler.setGrade(a, Grade.STURDY);
            GeneticsHandler.setGrade(b, Grade.STURDY);
            helper.setBlock(new BlockPos(2, 2, 3), Blocks.HAY_BLOCK);

            Animal calf = breed(helper, a, b);
            helper.assertTrue(calf != null, "breeding produced a calf");
            helper.assertValueEqual(InstinctAPI.getGrade(calf), Grade.PRIME,
                    "sturdy × sturdy, well-fed, upgrade chance 1.0 → prime");
            helper.assertTrue(InstinctAPI.getPerk(calf) != Perk.NONE,
                    "a graded calf is born with a perk");
            helper.succeed();
        } finally {
            InstinctConfig.get().gradeUpgradeChance = savedUp;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctGeneticsDown")
    public void crowdedBreedingDowngradesTheGrade(GameTestHelper helper) {
        double savedDown = InstinctConfig.get().gradeDowngradeChance;
        try {
            InstinctConfig.get().gradeDowngradeChance = 1.0;
            // 13 covered animals within the crowding radius, no hay: crowded and not well-fed.
            List<Cow> herd = new java.util.ArrayList<>();
            for (int i = 0; i < 13; i++) {
                Cow cow = helper.spawn(EntityType.COW, new BlockPos(2 + (i % 4), 2, 2 + (i / 4)));
                GeneticsHandler.setGrade(cow, Grade.STURDY);
                herd.add(cow);
            }
            Animal calf = breed(helper, herd.get(0), herd.get(1));
            helper.assertTrue(calf != null, "breeding produced a calf");
            helper.assertValueEqual(InstinctAPI.getGrade(calf), Grade.ORDINARY,
                    "sturdy × sturdy in a 13-animal crush, downgrade chance 1.0 → ordinary");
            helper.succeed();
        } finally {
            InstinctConfig.get().gradeDowngradeChance = savedDown;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void pedigreeTreatFlagsThenBirthsPrimeAndClears(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            player.getAbilities().instabuild = false;
            Cow a = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
            Cow b = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
            ItemStack treat = new ItemStack(InstinctItems.PEDIGREE_TREAT, 2);

            ((PedigreeTreatItem) InstinctItems.PEDIGREE_TREAT)
                    .interactLivingEntity(treat, player, a, InteractionHand.MAIN_HAND);
            helper.assertTrue(a.getAttached(InstinctAttachments.GENETICS).primeNextOffspring(),
                    "the treat set the prime-next-offspring flag");
            helper.assertValueEqual(treat.getCount(), 1, "one treat consumed");

            // A second treat before breeding is refused with no consume.
            ((PedigreeTreatItem) InstinctItems.PEDIGREE_TREAT)
                    .interactLivingEntity(treat, player, a, InteractionHand.MAIN_HAND);
            helper.assertValueEqual(treat.getCount(), 1, "a second treat is refused, not consumed");

            Animal calf = breed(helper, a, b);
            helper.assertTrue(calf != null, "breeding produced a calf");
            helper.assertValueEqual(InstinctAPI.getGrade(calf), Grade.PRIME,
                    "the flagged parent births a prime calf");
            helper.assertFalse(a.getAttached(InstinctAttachments.GENETICS).primeNextOffspring(),
                    "the flag is cleared after use");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void primeCowDeathDropsTheGradedBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        GeneticsHandler.setGrade(cow, Grade.PRIME);

        // The data-path bonus for a real prime cow is exactly +2 beef and +1 leather.
        DoubleSupplier roll = () -> 0.0;
        List<ItemStack> bonus = ProductTable.bonusDrops(cow, 2, false, List.of(), true, roll);
        helper.assertValueEqual(countIn(bonus, Items.BEEF), 2, "prime bonus is +2 beef");
        helper.assertValueEqual(countIn(bonus, Items.LEATHER), 1, "prime bonus is +1 leather");

        // Through the real death: a prime cow can never drop fewer than 3 beef (1–3 vanilla + 2)
        // or fewer than 1 leather (0–2 vanilla + 1), proving the AFTER_DEATH hook fired.
        cow.hurt(level.damageSources().generic(), 1000.0F);
        AABB area = new AABB(cow.blockPosition()).inflate(6.0);
        int beef = 0;
        int leather = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (item.getItem().is(Items.BEEF)) beef += item.getItem().getCount();
            if (item.getItem().is(Items.LEATHER)) leather += item.getItem().getCount();
        }
        helper.assertTrue(beef >= 3, "prime cow drops at least 3 beef, got " + beef);
        helper.assertTrue(leather >= 1, "prime cow drops at least 1 leather, got " + leather);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void mooshroomShearCarriesGeneticsToTheCow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MushroomCow mooshroom = helper.spawn(EntityType.MOOSHROOM, new BlockPos(3, 2, 3));
        GeneticsHandler.setGrade(mooshroom, Grade.PRIME);
        mooshroom.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(Grade.PRIME.level(), Perk.HARDY, false, 0L));

        BlockPos where = mooshroom.blockPosition();
        mooshroom.shear(SoundSource.PLAYERS);

        AABB area = new AABB(where).inflate(4.0);
        Cow cow = null;
        for (Cow candidate : level.getEntitiesOfClass(Cow.class, area)) {
            if (!(candidate instanceof MushroomCow)) {
                cow = candidate;
            }
        }
        helper.assertTrue(cow != null, "shearing the mooshroom produced a cow");
        helper.assertValueEqual(InstinctAPI.getGrade(cow), Grade.PRIME,
                "the grade carried across the conversion");
        helper.assertValueEqual(InstinctAPI.getPerk(cow), Perk.HARDY,
                "the perk carried across the conversion");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctGeneticsOff")
    public void disabledGeneticsFreezesInheritanceButKeepsData(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableGenetics;
        try {
            InstinctConfig.get().enableGenetics = false;
            Cow a = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
            Cow b = helper.spawn(EntityType.COW, new BlockPos(3, 2, 2));
            // Existing attachment data is retained untouched while genetics is disabled.
            a.setAttached(InstinctAttachments.GENETICS, new GeneticsData(2, Perk.HARDY, false, 0L));
            helper.assertValueEqual(a.getAttached(InstinctAttachments.GENETICS).grade(), 2,
                    "an existing grade is retained while disabled");

            Animal calf = breed(helper, a, b);
            helper.assertTrue(calf != null, "breeding still produces a calf");
            helper.assertValueEqual(InstinctAPI.getGrade(calf), Grade.ORDINARY,
                    "no inheritance roll while genetics is disabled — the calf stays ordinary");
            helper.assertValueEqual(InstinctAPI.getPerk(calf), Perk.NONE, "no perk while disabled");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableGenetics = saved;
        }
    }

    /**
     * Breeds two animals through the real pipeline and returns the newborn baby, or {@code null}.
     */
    static Animal breed(GameTestHelper helper, Animal a, Animal b) {
        ServerLevel level = helper.getLevel();
        AABB area = new AABB(a.blockPosition()).inflate(24.0);
        Set<Integer> before = new HashSet<>();
        for (Animal existing : level.getEntitiesOfClass(Animal.class, area)) {
            before.add(existing.getId());
        }
        a.spawnChildFromBreeding(level, b);
        for (Animal candidate : level.getEntitiesOfClass(Animal.class, area)) {
            if (!before.contains(candidate.getId()) && candidate.isBaby()) {
                return candidate;
            }
        }
        return null;
    }

    private static int countIn(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
