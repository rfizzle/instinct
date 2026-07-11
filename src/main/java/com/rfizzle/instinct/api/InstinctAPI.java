package com.rfizzle.instinct.api;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.DownedData;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.VeterancyData;
import com.rfizzle.instinct.veterancy.Veterancy;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

/**
 * Instinct's read-only public API (Concord API Standard). All reads are server-authoritative and
 * resolve from the persistent entity attachments and the live Animal Coverage resolution; an
 * animal without attachment data reads as its vanilla default ({@link Grade#ORDINARY},
 * {@link Perk#NONE}, 0 days, not downed, not trough-fed). Nothing here mutates Instinct's state.
 */
@Stable
public final class InstinctAPI {

    /** Trough feedings older than this many ticks no longer count as trough-fed (§3/§5). */
    private static final long TROUGH_FED_WINDOW_TICKS = 24_000L;

    private InstinctAPI() {
    }

    /** Pets-set membership after full Animal Coverage resolution (config → tag → heuristic). */
    public static boolean isPet(EntityType<?> type) {
        return AnimalCoverage.isPet(type);
    }

    /** Livestock-set membership after full Animal Coverage resolution (config → tag → heuristic). */
    public static boolean isLivestock(EntityType<?> type) {
        return AnimalCoverage.isLivestock(type);
    }

    /** The animal's bloodline grade; {@link Grade#ORDINARY} when uncovered or untracked. */
    public static Grade getGrade(Animal animal) {
        GeneticsData data = animal.getAttached(InstinctAttachments.GENETICS);
        return data == null ? Grade.ORDINARY : Grade.fromLevel(data.grade());
    }

    /** The animal's birth perk; {@link Perk#NONE} when uncovered or untracked. */
    public static Perk getPerk(Animal animal) {
        GeneticsData data = animal.getAttached(InstinctAttachments.GENETICS);
        return data == null ? Perk.NONE : data.perk();
    }

    /** The pet's accrued veterancy days; {@code 0.0} when untracked. */
    public static double getVeterancyDays(TamableAnimal pet) {
        VeterancyData data = pet.getAttached(InstinctAttachments.VETERANCY);
        return data == null ? 0.0 : data.accruedDays();
    }

    /** The pet's veterancy rank 0–3, derived from accrued days against the configured thresholds. */
    public static int getVeterancyRank(TamableAnimal pet) {
        return Veterancy.rankFor(getVeterancyDays(pet), InstinctConfig.get().veterancyThresholdDays);
    }

    /** Whether the entity is in the downed state (§7). */
    public static boolean isDowned(LivingEntity entity) {
        return entity.getAttached(InstinctAttachments.DOWNED) != null;
    }

    /** Whether the animal was trough-fed within the last 24000 ticks (§5). */
    public static boolean isTroughFed(Animal animal) {
        GeneticsData data = animal.getAttached(InstinctAttachments.GENETICS);
        if (data == null || data.lastTroughFeedTime() <= 0L) {
            return false;
        }
        long elapsed = animal.level().getGameTime() - data.lastTroughFeedTime();
        return elapsed >= 0 && elapsed <= TROUGH_FED_WINDOW_TICKS;
    }
}
