package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.advancement.BredGradeTrigger;
import com.rfizzle.instinct.advancement.PetRankTrigger;
import com.rfizzle.instinct.advancement.PetRevivedTrigger;
import com.rfizzle.instinct.advancement.WhistlePackTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Instinct's custom advancement criteria ({@code design/SPEC.md} §Advancements). Registered from
 * {@code onInitialize}, before any advancement JSON referencing the trigger ids is deserialized.
 */
public final class InstinctCriteria {

    /** Fires with the pet's derived rank whenever it is (re-)asserted for an online owner (§2). */
    public static final PetRankTrigger PET_RANK = new PetRankTrigger();

    /** Fires with a newborn's grade for the breeder, after grade resolution at breeding (§3). */
    public static final BredGradeTrigger BRED_GRADE = new BredGradeTrigger();

    /** Fires for the player who revives a downed pet, at the revival transition (§7). */
    public static final PetRevivedTrigger PET_REVIVED = new PetRevivedTrigger();

    /** Fires with the pet count a single whistle press commanded, for the whistler (§6). */
    public static final WhistlePackTrigger WHISTLE_PACK = new WhistlePackTrigger();

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
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Instinct.id("bred_grade"), BRED_GRADE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Instinct.id("pet_revived"), PET_REVIVED);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, Instinct.id("whistle_pack"), WHISTLE_PACK);
    }
}
