package com.rfizzle.instinct.api;

import net.minecraft.util.StringRepresentable;

/**
 * A graded animal's birth perk ({@code design/SPEC.md} §3). Grade-0 animals, uncovered animals,
 * and animals with no genetics data read as {@link #NONE}.
 */
@Stable
public enum Perk implements StringRepresentable {
    NONE("none"),
    HARDY("hardy"),
    FLEET("fleet"),
    FERTILE("fertile"),
    PLACID("placid");

    private final String name;

    Perk(String name) {
        this.name = name;
    }

    /** The {@code instinct.perk.*} key for this perk's display name. */
    public String translationKey() {
        return "instinct.perk." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
