package com.rfizzle.instinct.coverage;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Tame/owner resolution across the two owned-animal shapes Instinct's care features cover — pets
 * ({@link TamableAnimal}) for self-preservation (§1) and downed/revival (§7), and mounts
 * ({@link AbstractHorse}) for the same two. The two class hierarchies implement taming
 * independently — an {@code AbstractHorse} is not a {@code TamableAnimal} and has no shared
 * supertype exposing {@code isTamed}/{@code getOwnerUUID} — so a common cast is impossible; this is
 * the single place that {@code instanceof} branch lives.
 */
public final class OwnedAnimals {

    private OwnedAnimals() {
    }

    /** Whether the animal is tamed and owned — a pet's {@code isTame()} or a mount's {@code isTamed()}. */
    public static boolean isTamed(Mob animal) {
        if (animal instanceof TamableAnimal pet) {
            return pet.isTame();
        }
        if (animal instanceof AbstractHorse mount) {
            return mount.isTamed();
        }
        return false;
    }

    /** The owner's UUID, or {@code null} when untamed/ownerless or not an owned-animal shape. */
    @Nullable
    public static UUID ownerUUID(Mob animal) {
        if (animal instanceof TamableAnimal pet) {
            return pet.getOwnerUUID();
        }
        if (animal instanceof AbstractHorse mount) {
            return mount.getOwnerUUID();
        }
        return null;
    }
}
