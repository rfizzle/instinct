package com.rfizzle.instinct.coverage;

/**
 * What an entity type's Java class can do, as observed on an instance — the input to the
 * Animal Coverage heuristic. {@code TAMABLE}, {@code MOUNT}, and {@code BREEDABLE} are mutually
 * exclusive by construction (a {@code TamableAnimal} is {@code TAMABLE}, an {@code AbstractHorse}
 * is {@code MOUNT}, any other {@code Animal} is {@code BREEDABLE}), which is what makes "a pet is
 * never heuristically livestock" and "a mount is never heuristically livestock" structural.
 */
public enum AnimalCapability {
    /** Extends {@code TamableAnimal} — heuristically a pet. */
    TAMABLE,
    /** Extends {@code AbstractHorse} — heuristically a mount. */
    MOUNT,
    /** Extends {@code Animal} but not {@code TamableAnimal}/{@code AbstractHorse} — heuristically livestock. */
    BREEDABLE,
    /** Neither, or the type could not be probed. */
    NONE
}
