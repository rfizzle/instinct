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
        boolean petHeuristic = layers.autoDetect() && layers.capability() == AnimalCapability.TAMABLE;
        boolean livestockHeuristic =
                layers.autoDetect() && layers.capability() == AnimalCapability.BREEDABLE;
        boolean mountHeuristic = layers.autoDetect() && layers.capability() == AnimalCapability.MOUNT;

        return new Membership(
                inSet(layers.configPetsExclude(), layers.configPetsInclude(),
                        layers.tagPetsExclude(), layers.tagPetsInclude(), petHeuristic),
                ruleFor(layers.configPetsExclude(), layers.configPetsInclude(),
                        layers.tagPetsExclude(), layers.tagPetsInclude(), petHeuristic),
                inSet(layers.configLivestockExclude(), layers.configLivestockInclude(),
                        layers.tagLivestockExclude(), layers.tagLivestockInclude(), livestockHeuristic),
                ruleFor(layers.configLivestockExclude(), layers.configLivestockInclude(),
                        layers.tagLivestockExclude(), layers.tagLivestockInclude(), livestockHeuristic),
                inSet(layers.configMountsExclude(), layers.configMountsInclude(),
                        layers.tagMountsExclude(), layers.tagMountsInclude(), mountHeuristic),
                ruleFor(layers.configMountsExclude(), layers.configMountsInclude(),
                        layers.tagMountsExclude(), layers.tagMountsInclude(), mountHeuristic));
    }

    /**
     * The precedence ladder for one set: config excludes, then config includes, then tag excludes,
     * then tag includes, then the heuristic. First match wins; nothing matching means out of the set.
     *
     * <p>This is the single definition of the ladder — {@link #resolve} runs it three times to build
     * the full record, and the boolean fast path in {@code AnimalCoverage} runs it once for the one
     * set a caller asked about, so the two can never answer differently.
     */
    public static boolean inSet(boolean configExclude, boolean configInclude,
                                boolean tagExclude, boolean tagInclude, boolean heuristic) {
        if (configExclude) {
            return false;
        }
        if (configInclude) {
            return true;
        }
        if (tagExclude) {
            return false;
        }
        if (tagInclude) {
            return true;
        }
        return heuristic;
    }

    /** Which layer of {@link #inSet}'s ladder decided the answer. */
    public static MembershipRule ruleFor(boolean configExclude, boolean configInclude,
                                         boolean tagExclude, boolean tagInclude, boolean heuristic) {
        if (configExclude || configInclude) {
            return MembershipRule.CONFIG;
        }
        if (tagExclude || tagInclude) {
            return MembershipRule.TAG;
        }
        return heuristic ? MembershipRule.HEURISTIC : MembershipRule.NONE;
    }
}
