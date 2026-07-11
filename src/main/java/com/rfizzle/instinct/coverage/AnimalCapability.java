package com.rfizzle.instinct.coverage;

/**
 * What an entity type's Java class can do, as observed on an instance — the input to the
 * Animal Coverage heuristic. {@code TAMABLE} and {@code BREEDABLE} are mutually exclusive by
 * construction (a {@code TamableAnimal} is classified {@code TAMABLE}, never {@code BREEDABLE}),
 * which is what makes "a pet type is never heuristically livestock" structural.
 */
public enum AnimalCapability {
    /** Extends {@code TamableAnimal} — heuristically a pet. */
    TAMABLE,
    /** Extends {@code Animal} but not {@code TamableAnimal} — heuristically livestock. */
    BREEDABLE,
    /** Neither, or the type could not be probed. */
    NONE
}
