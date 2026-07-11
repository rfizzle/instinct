package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.data.DownedData;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.VeterancyData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;

/**
 * The three persistent attachments: latent by default (an untouched animal carries no attachment
 * and reads as vanilla through {@code InstinctAPI}), and values survive an entity NBT save/load
 * round trip — the same path chunk serialization uses.
 */
public class AttachmentPersistenceGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void absentAttachmentsReadVanillaDefaultsThroughApi(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 1, 2, 1);
        helper.assertTrue(cow.getAttached(InstinctAttachments.GENETICS) == null,
                "a fresh cow must carry no genetics attachment");
        helper.assertValueEqual(InstinctAPI.getGrade(cow), Grade.ORDINARY, "default grade");
        helper.assertValueEqual(InstinctAPI.getPerk(cow), Perk.NONE, "default perk");
        helper.assertFalse(InstinctAPI.isTroughFed(cow), "fresh cow is not trough-fed");
        helper.assertFalse(InstinctAPI.isDowned(cow), "fresh cow is not downed");

        Wolf wolf = helper.spawn(EntityType.WOLF, 1, 2, 1);
        helper.assertTrue(wolf.getAttached(InstinctAttachments.VETERANCY) == null,
                "a fresh wolf must carry no veterancy attachment");
        helper.assertValueEqual(InstinctAPI.getVeterancyDays(wolf), 0.0, "default veterancy days");
        helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 0, "default veterancy rank");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void attachmentsSurviveEntityNbtRoundTrip(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 1, 2, 1);
        cow.setAttached(InstinctAttachments.GENETICS, new GeneticsData(2, Perk.FLEET, true, 100L));
        cow.setAttached(InstinctAttachments.DOWNED, new DownedData(500L));

        CompoundTag saved = new CompoundTag();
        cow.saveWithoutId(saved);
        cow.discard();

        Cow loaded = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(loaded != null, "cow entity should be created");
        loaded.load(saved);

        GeneticsData genetics = loaded.getAttached(InstinctAttachments.GENETICS);
        helper.assertTrue(genetics != null, "genetics attachment should survive save/load");
        helper.assertValueEqual(genetics.grade(), 2, "grade");
        helper.assertValueEqual(genetics.perk(), Perk.FLEET, "perk");
        helper.assertTrue(genetics.primeNextOffspring(), "pedigree-treat flag should survive");
        helper.assertValueEqual(genetics.lastTroughFeedTime(), 100L, "trough-fed time");
        helper.assertValueEqual(InstinctAPI.getGrade(loaded), Grade.PRIME, "grade through the API");
        helper.assertTrue(InstinctAPI.isDowned(loaded), "downed state should survive save/load");
        loaded.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void veterancySurvivesEntityNbtRoundTrip(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, 1, 2, 1);
        wolf.setAttached(InstinctAttachments.VETERANCY, new VeterancyData(42.5, 1234L));

        CompoundTag saved = new CompoundTag();
        wolf.saveWithoutId(saved);
        wolf.discard();

        Wolf loaded = EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(loaded != null, "wolf entity should be created");
        loaded.load(saved);

        VeterancyData veterancy = loaded.getAttached(InstinctAttachments.VETERANCY);
        helper.assertTrue(veterancy != null, "veterancy attachment should survive save/load");
        helper.assertValueEqual(veterancy.accruedDays(), 42.5, "accrued days");
        helper.assertValueEqual(veterancy.lastAccrualGameTime(), 1234L, "last accrual time");
        helper.assertValueEqual(InstinctAPI.getVeterancyDays(loaded), 42.5, "days through the API");
        helper.assertValueEqual(InstinctAPI.getVeterancyRank(loaded), 2,
                "42.5 days should derive rank 2 at default thresholds");
        loaded.discard();
        helper.succeed();
    }
}
