package com.rfizzle.instinct.compat.common;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.veterancy.Veterancy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

import java.util.ArrayList;
import java.util.List;

/**
 * The viewer-agnostic core for the covered-animal probe tooltip (Jade / WTHIT), per SPEC §Compat:
 * grade + perk on graded livestock, veterancy days + rank on pets, and downed status. A server-side
 * writer packs the state into the probe's {@link CompoundTag} under mod-namespaced keys, gated once
 * here; a pure formatter turns that tag back into lines. No Jade or WTHIT type is imported — the
 * thin per-viewer adapters delegate to this, so the two overlays are identical by construction and
 * neither viewer's absence class-loads anything here.
 */
public final class AnimalProbeTooltip {

    public static final String KEY_PRESENT = "instinct:present";
    public static final String KEY_GRADE = "instinct:grade";
    public static final String KEY_PERK = "instinct:perk";
    public static final String KEY_VET_DAYS = "instinct:vet_days";
    public static final String KEY_VET_RANK = "instinct:vet_rank";
    public static final String KEY_DOWNED = "instinct:downed";

    private AnimalProbeTooltip() {
    }

    /**
     * Server side. Writes each line's data behind its own feature toggle and coverage check, and a
     * single presence flag when at least one line was produced — so the broad {@code Animal.class}
     * registration stays inert on uncovered or ordinary animals (empty tooltip in every viewer).
     */
    public static void writeServerData(CompoundTag tag, Animal animal) {
        InstinctConfig config = InstinctConfig.get();
        boolean any = false;

        if (config.enableGenetics && AnimalCoverage.membershipOf(animal).livestock()) {
            Grade grade = InstinctAPI.getGrade(animal);
            if (grade != Grade.ORDINARY) {
                tag.putString(KEY_GRADE, grade.getSerializedName());
                tag.putString(KEY_PERK, InstinctAPI.getPerk(animal).getSerializedName());
                any = true;
            }
        }
        if (config.enableVeterancy && animal instanceof TamableAnimal pet && pet.isTame()
                && AnimalCoverage.membershipOf(pet).pet()) {
            tag.putLong(KEY_VET_DAYS, (long) InstinctAPI.getVeterancyDays(pet));
            tag.putInt(KEY_VET_RANK, InstinctAPI.getVeterancyRank(pet));
            any = true;
        }
        if (config.enableDownedState && InstinctAPI.isDowned(animal)) {
            tag.putBoolean(KEY_DOWNED, true);
            any = true;
        }
        if (any) {
            tag.putBoolean(KEY_PRESENT, true);
        }
    }

    /** Client side. Pure tag → lines; the tag crossed the network, so every read falls back. */
    public static List<Component> buildLines(CompoundTag tag) {
        List<Component> lines = new ArrayList<>();
        if (!tag.getBoolean(KEY_PRESENT)) {
            return lines;
        }
        if (tag.contains(KEY_GRADE)) {
            Component gradeName = Component.translatable(gradeFrom(tag.getString(KEY_GRADE)).translationKey());
            Perk perk = perkFrom(tag.getString(KEY_PERK));
            lines.add(perk == Perk.NONE
                    ? Component.translatable("tooltip.instinct.animal.grade", gradeName)
                    : Component.translatable("tooltip.instinct.animal.grade_perk", gradeName,
                            Component.translatable(perk.translationKey())));
        }
        if (tag.contains(KEY_VET_DAYS)) {
            long days = tag.getLong(KEY_VET_DAYS);
            int rank = tag.getInt(KEY_VET_RANK);
            lines.add(rank > 0
                    ? Component.translatable("tooltip.instinct.animal.veterancy_ranked", days,
                            Component.translatable(Veterancy.rankKey(rank)))
                    : Component.translatable("tooltip.instinct.animal.veterancy", days));
        }
        if (tag.getBoolean(KEY_DOWNED)) {
            lines.add(Component.translatable("tooltip.instinct.animal.downed"));
        }
        return lines;
    }

    /** Grade by serialized name, falling back to {@link Grade#ORDINARY} for an unknown value. */
    private static Grade gradeFrom(String name) {
        for (Grade grade : Grade.values()) {
            if (grade.getSerializedName().equals(name)) {
                return grade;
            }
        }
        return Grade.ORDINARY;
    }

    /** Perk by serialized name, falling back to {@link Perk#NONE} for an unknown value. */
    private static Perk perkFrom(String name) {
        for (Perk perk : Perk.values()) {
            if (perk.getSerializedName().equals(name)) {
                return perk;
            }
        }
        return Perk.NONE;
    }
}
