package com.rfizzle.instinct.coverage;

/**
 * The pure Animal Coverage resolution core ({@code design/SPEC.md} §Animal Coverage): per entity
 * type and per set, first match wins across three layers — config (excludes, then includes), tags
 * (excludes, then includes), then the tamable/mount/breedable heuristic when {@code
 * autoDetectAnimals} is on. Exclusion beats inclusion within each layer; a tamable type is never
 * heuristically a mount or livestock, and a mount type ({@code AbstractHorse}) is never
 * heuristically livestock ({@link AnimalCapability}). The three sets resolve independently — an
 * explicit config or tag entry can put one type in more than one.
 *
 * <p>No Minecraft types: callers gather the layer facts ({@link Layers}) and this class only
 * orders them, so the full precedence matrix unit-tests at tier 1.
 */
public final class CoverageResolver {

    /** The layer facts for one entity type, as gathered by {@code AnimalCoverage}. */
    public record Layers(
            boolean configPetsExclude,
            boolean configPetsInclude,
            boolean configLivestockExclude,
            boolean configLivestockInclude,
            boolean configMountsExclude,
            boolean configMountsInclude,
            boolean tagPetsExclude,
            boolean tagPetsInclude,
            boolean tagLivestockExclude,
            boolean tagLivestockInclude,
            boolean tagMountsExclude,
            boolean tagMountsInclude,
            boolean autoDetect,
            AnimalCapability capability) {
    }

    /** One type's resolved membership: in/out per set, plus the rule that decided each. */
    public record Membership(boolean pet, MembershipRule petRule,
                             boolean livestock, MembershipRule livestockRule,
                             boolean mount, MembershipRule mountRule) {
    }

    private CoverageResolver() {
    }

    public static Membership resolve(Layers layers) {
        boolean pet;
        MembershipRule petRule;
        if (layers.configPetsExclude()) {
            pet = false;
            petRule = MembershipRule.CONFIG;
        } else if (layers.configPetsInclude()) {
            pet = true;
            petRule = MembershipRule.CONFIG;
        } else if (layers.tagPetsExclude()) {
            pet = false;
            petRule = MembershipRule.TAG;
        } else if (layers.tagPetsInclude()) {
            pet = true;
            petRule = MembershipRule.TAG;
        } else if (layers.autoDetect() && layers.capability() == AnimalCapability.TAMABLE) {
            pet = true;
            petRule = MembershipRule.HEURISTIC;
        } else {
            pet = false;
            petRule = MembershipRule.NONE;
        }

        boolean livestock;
        MembershipRule livestockRule;
        if (layers.configLivestockExclude()) {
            livestock = false;
            livestockRule = MembershipRule.CONFIG;
        } else if (layers.configLivestockInclude()) {
            livestock = true;
            livestockRule = MembershipRule.CONFIG;
        } else if (layers.tagLivestockExclude()) {
            livestock = false;
            livestockRule = MembershipRule.TAG;
        } else if (layers.tagLivestockInclude()) {
            livestock = true;
            livestockRule = MembershipRule.TAG;
        } else if (layers.autoDetect() && layers.capability() == AnimalCapability.BREEDABLE) {
            livestock = true;
            livestockRule = MembershipRule.HEURISTIC;
        } else {
            livestock = false;
            livestockRule = MembershipRule.NONE;
        }

        boolean mount;
        MembershipRule mountRule;
        if (layers.configMountsExclude()) {
            mount = false;
            mountRule = MembershipRule.CONFIG;
        } else if (layers.configMountsInclude()) {
            mount = true;
            mountRule = MembershipRule.CONFIG;
        } else if (layers.tagMountsExclude()) {
            mount = false;
            mountRule = MembershipRule.TAG;
        } else if (layers.tagMountsInclude()) {
            mount = true;
            mountRule = MembershipRule.TAG;
        } else if (layers.autoDetect() && layers.capability() == AnimalCapability.MOUNT) {
            mount = true;
            mountRule = MembershipRule.HEURISTIC;
        } else {
            mount = false;
            mountRule = MembershipRule.NONE;
        }

        return new Membership(pet, petRule, livestock, livestockRule, mount, mountRule);
    }
}
