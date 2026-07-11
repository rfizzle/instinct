package com.rfizzle.instinct.api;

import net.minecraft.util.StringRepresentable;

/**
 * A livestock animal's bloodline grade ({@code design/SPEC.md} §3). Uncovered animals and animals
 * with no genetics data read as {@link #ORDINARY}.
 */
@Stable
public enum Grade implements StringRepresentable {
    ORDINARY(0, "ordinary"),
    STURDY(1, "sturdy"),
    PRIME(2, "prime");

    private final int level;
    private final String name;

    Grade(int level, String name) {
        this.level = level;
        this.name = name;
    }

    /** The numeric grade level, 0–2. */
    public int level() {
        return level;
    }

    /** The grade for a numeric level; out-of-range values clamp to the nearest grade. */
    public static Grade fromLevel(int level) {
        if (level <= 0) return ORDINARY;
        if (level == 1) return STURDY;
        return PRIME;
    }

    /** The {@code instinct.grade.*} key for this grade's display name. */
    public String translationKey() {
        return "instinct.grade." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
