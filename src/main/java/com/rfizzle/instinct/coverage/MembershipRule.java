package com.rfizzle.instinct.coverage;

/**
 * Which Animal Coverage layer decided a type's set membership — the modded-animal debugging
 * surface reported by {@code /instinct info}. {@code NONE} means no layer matched (the type is
 * simply not in the set).
 */
public enum MembershipRule {
    CONFIG("config"),
    TAG("tag"),
    HEURISTIC("heuristic"),
    NONE("none");

    private final String translationKey;

    MembershipRule(String name) {
        this.translationKey = "command.instinct.info.rule." + name;
    }

    /** The {@code command.instinct.info.rule.*} key naming this rule in command output. */
    public String translationKey() {
        return translationKey;
    }
}
