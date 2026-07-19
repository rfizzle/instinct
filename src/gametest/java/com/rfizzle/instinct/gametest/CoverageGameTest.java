package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.coverage.CoverageResolver;
import com.rfizzle.instinct.coverage.MembershipRule;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Animal Coverage resolution against a live server: shipped default tags, the seeded heuristic
 * capability cache, and the config override layer. The config-mutating tests rewrite the shared
 * {@code config/instinct.json}, so each gets a unique {@code batch} — vanilla runs batches
 * strictly sequentially, keeping other tests from observing a mutated config — and restores the
 * original file in {@code finally}.
 */
public class CoverageGameTest implements FabricGameTest {

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("instinct.json");

    @GameTest(template = EMPTY_STRUCTURE)
    public void wolfResolvesPetViaShippedTag(GameTestHelper helper) {
        CoverageResolver.Membership membership = AnimalCoverage.membershipOf(EntityType.WOLF);
        helper.assertTrue(membership.pet(), "wolf should be in the pets set");
        helper.assertValueEqual(membership.petRule(), MembershipRule.TAG, "wolf granting rule");
        helper.assertFalse(membership.livestock(), "wolf should not be livestock");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cowResolvesLivestockViaShippedTag(GameTestHelper helper) {
        CoverageResolver.Membership membership = AnimalCoverage.membershipOf(EntityType.COW);
        helper.assertTrue(membership.livestock(), "cow should be in the livestock set");
        helper.assertValueEqual(membership.livestockRule(), MembershipRule.TAG, "cow granting rule");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void foxResolvesLivestockViaHeuristic(GameTestHelper helper) {
        CoverageResolver.Membership membership = AnimalCoverage.membershipOf(EntityType.FOX);
        helper.assertTrue(membership.livestock(), "fox (breedable, untagged) should be livestock");
        helper.assertValueEqual(membership.livestockRule(), MembershipRule.HEURISTIC, "fox granting rule");
        helper.assertFalse(membership.pet(), "fox is not tamable and never a pet");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void horseFamilyResolvesOutViaShippedExcludeTag(GameTestHelper helper) {
        CoverageResolver.Membership membership = AnimalCoverage.membershipOf(EntityType.HORSE);
        helper.assertFalse(membership.livestock(), "horse should be excluded from livestock");
        helper.assertValueEqual(membership.livestockRule(), MembershipRule.TAG, "horse exclusion rule");
        helper.assertTrue(membership.mount(), "horse should be in the mounts set");
        helper.assertValueEqual(membership.mountRule(), MembershipRule.TAG, "horse mount granting rule");
        helper.assertFalse(membership.pet(), "horse is not a pet");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctConfig1")
    public void configExcludeBeatsTheLivestockTag(GameTestHelper helper) {
        byte[] original = readConfig(helper);
        if (original == null) {
            return;
        }
        try {
            Files.writeString(CONFIG_FILE, """
                    { "configVersion": 1, "livestockExclude": ["minecraft:cow"] }
                    """);
            InstinctConfig.reload();

            CoverageResolver.Membership membership = AnimalCoverage.membershipOf(EntityType.COW);
            helper.assertFalse(membership.livestock(), "livestockExclude should beat the shipped tag");
            helper.assertValueEqual(membership.livestockRule(), MembershipRule.CONFIG, "exclusion rule");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error during test: " + e.getMessage());
        } finally {
            restoreConfig(original);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctConfig2")
    public void autoDetectOffReducesResolutionToTagsAndConfig(GameTestHelper helper) {
        byte[] original = readConfig(helper);
        if (original == null) {
            return;
        }
        try {
            Files.writeString(CONFIG_FILE, """
                    { "configVersion": 1, "autoDetectAnimals": false }
                    """);
            InstinctConfig.reload();

            helper.assertFalse(AnimalCoverage.isLivestock(EntityType.FOX),
                    "heuristic membership should be off");
            helper.assertTrue(AnimalCoverage.isLivestock(EntityType.COW),
                    "tag membership should be unaffected");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error during test: " + e.getMessage());
        } finally {
            restoreConfig(original);
        }
    }

    /**
     * The boolean fast path and the full record must answer identically for every set and every
     * resolution layer — the shipped tags, the exclude tags, and the heuristic are all represented
     * here. Most of the mod reads coverage through the is* methods and only {@code /instinct info}
     * and these tests read the record, so a divergence would otherwise surface as scattered
     * feature-level bugs rather than a coverage failure.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void fastPathAgreesWithTheFullRecord(GameTestHelper helper) {
        for (EntityType<?> type : new EntityType<?>[]{
                EntityType.WOLF, EntityType.CAT, EntityType.COW, EntityType.SHEEP,
                EntityType.FOX, EntityType.HORSE, EntityType.CAMEL, EntityType.ZOMBIE}) {
            CoverageResolver.Membership membership = AnimalCoverage.membershipOf(type);
            String name = type.toShortString();
            helper.assertValueEqual(AnimalCoverage.isPet(type), membership.pet(), "isPet for " + name);
            helper.assertValueEqual(AnimalCoverage.isLivestock(type), membership.livestock(),
                    "isLivestock for " + name);
            helper.assertValueEqual(AnimalCoverage.isMount(type), membership.mount(), "isMount for " + name);
        }
        helper.succeed();
    }

    /**
     * The fast path skips building the type's registry id when a set has no config entries to match
     * it against, so the branch that does build it only runs with a non-empty list — this pins that
     * branch against the record path.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctConfig3")
    public void fastPathAgreesWithTheFullRecordUnderConfigOverrides(GameTestHelper helper) {
        byte[] original = readConfig(helper);
        if (original == null) {
            return;
        }
        try {
            Files.writeString(CONFIG_FILE, """
                    { "configVersion": 1, "livestockExclude": ["minecraft:cow"],
                      "petsInclude": ["minecraft:fox"], "mountsExclude": ["minecraft:horse"] }
                    """);
            InstinctConfig.reload();

            helper.assertFalse(AnimalCoverage.isLivestock(EntityType.COW),
                    "config exclude should drop the cow from livestock on the fast path");
            helper.assertTrue(AnimalCoverage.isPet(EntityType.FOX),
                    "config include should add the fox to pets on the fast path");
            helper.assertFalse(AnimalCoverage.isMount(EntityType.HORSE),
                    "config exclude should drop the horse from mounts on the fast path");

            for (EntityType<?> type : new EntityType<?>[]{
                    EntityType.COW, EntityType.FOX, EntityType.HORSE, EntityType.WOLF}) {
                CoverageResolver.Membership membership = AnimalCoverage.membershipOf(type);
                String name = type.toShortString();
                helper.assertValueEqual(AnimalCoverage.isPet(type), membership.pet(), "isPet for " + name);
                helper.assertValueEqual(AnimalCoverage.isLivestock(type), membership.livestock(),
                        "isLivestock for " + name);
                helper.assertValueEqual(AnimalCoverage.isMount(type), membership.mount(),
                        "isMount for " + name);
            }
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error during test: " + e.getMessage());
        } finally {
            restoreConfig(original);
        }
    }

    /** The Entity overloads learn the instance's capability, so they answer for an unprobed type. */
    @GameTest(template = EMPTY_STRUCTURE)
    public void entityOverloadsResolveThroughLearnedCapability(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, 1, 2, 1);
        helper.assertTrue(AnimalCoverage.isPet(wolf), "a spawned wolf resolves into the pets set");
        helper.assertFalse(AnimalCoverage.isLivestock(wolf), "a wolf is not livestock");
        helper.assertValueEqual(AnimalCoverage.isPet(wolf), AnimalCoverage.membershipOf(wolf).pet(),
                "entity fast path agrees with the entity record path");
        helper.succeed();
    }

    private static byte[] readConfig(GameTestHelper helper) {
        try {
            return Files.readAllBytes(CONFIG_FILE);
        } catch (IOException e) {
            helper.fail("Could not read original config: " + e.getMessage());
            return null;
        }
    }

    private static void restoreConfig(byte[] original) {
        try {
            Files.write(CONFIG_FILE, original);
            InstinctConfig.reload();
        } catch (IOException e) {
            com.rfizzle.instinct.Instinct.LOGGER.error("Failed to restore config", e);
        }
    }
}
