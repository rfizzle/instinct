package com.rfizzle.instinct.coverage;

import com.rfizzle.instinct.coverage.CoverageResolver.Layers;
import com.rfizzle.instinct.coverage.CoverageResolver.Membership;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the full Animal Coverage precedence matrix: config > tag > heuristic, exclusion beats
 * inclusion within each layer, a tamable type is never heuristically livestock, a mount type is
 * never heuristically livestock, and {@code autoDetectAnimals = false} reduces resolution to
 * tags + config.
 */
class CoverageResolverTest {

    /** A builder over the layer facts so each test names only what it sets. */
    private static final class LayersBuilder {
        boolean configPetsExclude, configPetsInclude, configLivestockExclude, configLivestockInclude;
        boolean configMountsExclude, configMountsInclude;
        boolean tagPetsExclude, tagPetsInclude, tagLivestockExclude, tagLivestockInclude;
        boolean tagMountsExclude, tagMountsInclude;
        boolean autoDetect = true;
        AnimalCapability capability = AnimalCapability.NONE;

        Layers build() {
            return new Layers(configPetsExclude, configPetsInclude, configLivestockExclude, configLivestockInclude,
                    configMountsExclude, configMountsInclude,
                    tagPetsExclude, tagPetsInclude, tagLivestockExclude, tagLivestockInclude,
                    tagMountsExclude, tagMountsInclude,
                    autoDetect, capability);
        }
    }

    @Test
    void tamableTypeIsHeuristicallyPetAndNeverLivestock() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.TAMABLE;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertTrue(membership.pet());
        assertEquals(MembershipRule.HEURISTIC, membership.petRule());
        assertFalse(membership.livestock(), "a pet type is never heuristically livestock");
        assertEquals(MembershipRule.NONE, membership.livestockRule());
    }

    @Test
    void breedableTypeIsHeuristicallyLivestockOnly() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.BREEDABLE;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertFalse(membership.pet());
        assertTrue(membership.livestock());
        assertEquals(MembershipRule.HEURISTIC, membership.livestockRule());
    }

    @Test
    void nonAnimalTypeIsInNeitherSet() {
        Membership membership = CoverageResolver.resolve(new LayersBuilder().build());

        assertFalse(membership.pet());
        assertFalse(membership.livestock());
        assertEquals(MembershipRule.NONE, membership.petRule());
        assertEquals(MembershipRule.NONE, membership.livestockRule());
    }

    @Test
    void autoDetectOffReducesToTagsAndConfig() {
        LayersBuilder layers = new LayersBuilder();
        layers.autoDetect = false;
        layers.capability = AnimalCapability.BREEDABLE;
        Membership heuristicOnly = CoverageResolver.resolve(layers.build());
        assertFalse(heuristicOnly.livestock(), "heuristic must be dead with autoDetect off");

        layers.tagLivestockInclude = true;
        Membership viaTag = CoverageResolver.resolve(layers.build());
        assertTrue(viaTag.livestock(), "tags still resolve with autoDetect off");
        assertEquals(MembershipRule.TAG, viaTag.livestockRule());
    }

    @Test
    void tagIncludeBeatsHeuristic() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.TAMABLE;
        layers.tagPetsInclude = true;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertTrue(membership.pet());
        assertEquals(MembershipRule.TAG, membership.petRule(), "tag layer wins before the heuristic runs");
    }

    @Test
    void tagExcludeBeatsTagIncludeAndHeuristic() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.BREEDABLE;
        layers.tagLivestockInclude = true;
        layers.tagLivestockExclude = true;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertFalse(membership.livestock(), "exclusion beats inclusion within the tag layer");
        assertEquals(MembershipRule.TAG, membership.livestockRule());
    }

    @Test
    void configIncludeBeatsTagExclude() {
        LayersBuilder layers = new LayersBuilder();
        layers.tagLivestockExclude = true;
        layers.configLivestockInclude = true;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertTrue(membership.livestock(), "the server owner has the last word");
        assertEquals(MembershipRule.CONFIG, membership.livestockRule());
    }

    @Test
    void configExcludeBeatsEverything() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.TAMABLE;
        layers.tagPetsInclude = true;
        layers.configPetsInclude = true;
        layers.configPetsExclude = true;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertFalse(membership.pet(), "config exclusion beats config inclusion, tags, and heuristic");
        assertEquals(MembershipRule.CONFIG, membership.petRule());
    }

    @Test
    void petsExclusionDoesNotMakeATamableTypeLivestock() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.TAMABLE;
        layers.configPetsExclude = true;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertFalse(membership.pet());
        assertFalse(membership.livestock(),
                "excluding a tamable type from pets must not drop it into livestock");
    }

    @Test
    void setsResolveIndependently() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.TAMABLE;
        layers.configLivestockInclude = true;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertTrue(membership.pet(), "heuristic pet");
        assertEquals(MembershipRule.HEURISTIC, membership.petRule());
        assertTrue(membership.livestock(), "explicit config entry may put the same type in both sets");
        assertEquals(MembershipRule.CONFIG, membership.livestockRule());
    }

    @Test
    void mountTypeIsHeuristicallyMountAndNeverPetOrLivestock() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.MOUNT;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertTrue(membership.mount(), "an AbstractHorse type is heuristically a mount");
        assertEquals(MembershipRule.HEURISTIC, membership.mountRule());
        assertFalse(membership.livestock(), "a mount type is never heuristically livestock");
        assertEquals(MembershipRule.NONE, membership.livestockRule());
        assertFalse(membership.pet(), "a mount type is never heuristically a pet");
        assertEquals(MembershipRule.NONE, membership.petRule());
    }

    @Test
    void tamableTypeIsNeverHeuristicallyMount() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.TAMABLE;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertFalse(membership.mount(), "a tamable type is never heuristically a mount");
        assertEquals(MembershipRule.NONE, membership.mountRule());
    }

    @Test
    void breedableTypeIsNeverHeuristicallyMount() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.BREEDABLE;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertFalse(membership.mount(), "a plain breedable type is never heuristically a mount");
        assertTrue(membership.livestock(), "it is livestock instead");
    }

    @Test
    void mountConfigExcludeBeatsTagInclude() {
        LayersBuilder layers = new LayersBuilder();
        layers.capability = AnimalCapability.MOUNT;
        layers.tagMountsInclude = true;
        layers.configMountsExclude = true;
        Membership membership = CoverageResolver.resolve(layers.build());

        assertFalse(membership.mount(), "config exclusion is the server owner's last word for mounts too");
        assertEquals(MembershipRule.CONFIG, membership.mountRule());
    }

    @Test
    void mountAutoDetectOffLeavesTagResolution() {
        LayersBuilder layers = new LayersBuilder();
        layers.autoDetect = false;
        layers.capability = AnimalCapability.MOUNT;
        assertFalse(CoverageResolver.resolve(layers.build()).mount(),
                "the mount heuristic is dead with autoDetect off");

        layers.tagMountsInclude = true;
        Membership viaTag = CoverageResolver.resolve(layers.build());
        assertTrue(viaTag.mount(), "the mounts tag still resolves with autoDetect off");
        assertEquals(MembershipRule.TAG, viaTag.mountRule());
    }
}
