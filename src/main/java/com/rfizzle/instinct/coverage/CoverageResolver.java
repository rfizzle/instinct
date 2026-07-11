package com.rfizzle.instinct.coverage;

/**
 * The pure Animal Coverage resolution core ({@code design/SPEC.md} §Animal Coverage): per entity
 * type and per set, first match wins across three layers — config (excludes, then includes), tags
 * (excludes, then includes), then the tamable/breedable heuristic when {@code autoDetectAnimals}
 * is on. Exclusion beats inclusion within each layer, and a tamable type is never heuristically
 * livestock ({@link AnimalCapability}). The two sets resolve independently — an explicit config or
 * tag entry can put one type in both.
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
            boolean tagPetsExclude,
            boolean tagPetsInclude,
            boolean tagLivestockExclude,
            boolean tagLivestockInclude,
            boolean autoDetect,
            AnimalCapability capability) {
    }

    /** One type's resolved membership: in/out per set, plus the rule that decided each. */
    public record Membership(boolean pet, MembershipRule petRule, boolean livestock, MembershipRule livestockRule) {
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

        return new Membership(pet, petRule, livestock, livestockRule);
    }
}
