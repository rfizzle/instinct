package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.advancement.PetRankTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Instinct's custom advancement criteria ({@code design/SPEC.md} §Advancements). Registered from
 * {@code onInitialize}, before any advancement JSON referencing the trigger ids is deserialized.
 */
public final class InstinctCriteria {

    /** Fires with the pet's derived rank whenever it is (re-)asserted for an online owner (§2). */
    public static final PetRankTrigger PET_RANK = new PetRankTrigger();

    private static boolean registered = false;

    private InstinctCriteria() {
    }

    /** Idempotent: datagen bootstrap and test setup may reach this beside {@code onInitialize}. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Instinct.id("pet_rank"), PET_RANK);
    }
}
