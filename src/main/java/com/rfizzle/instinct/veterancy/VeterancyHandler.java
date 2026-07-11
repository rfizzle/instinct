package com.rfizzle.instinct.veterancy;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.VeterancyData;
import com.rfizzle.instinct.mixin.LivingEntitySoundInvoker;
import com.rfizzle.instinct.mixin.ParrotSoundInvoker;
import com.rfizzle.instinct.registry.InstinctCriteria;
import com.rfizzle.instinct.registry.InstinctSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pet veterancy engine ({@code design/SPEC.md} §2): lazy day accrual on a 200-tick cadence
 * (with a rate-1.0 catch-up on entity load so unloaded gaps never take the live multiplier), the
 * rank-up moment, the fixed-id attribute bonuses, and the three rank behaviors — warning (40-tick
 * sweep), "knows your swing" (consulted by {@code PlayerMixin}), and the mentor aura (folded into
 * the accrual rate). Rank itself is never stored: every pass re-derives it from accrued days
 * against the current thresholds, so config edits promote or demote on the next derivation.
 *
 * <p>All state here is server-side and transient: the tracked-pet set mirrors loaded entities,
 * and the warning dedupe forgets on restart by design (a repeated warning after a reboot is
 * harmless). Everything clears on {@code SERVER_STOPPED}.
 */
public final class VeterancyHandler {

    public static final ResourceLocation HEALTH_MODIFIER_ID = Instinct.id("veterancy_health");
    public static final ResourceLocation ATTACK_MODIFIER_ID = Instinct.id("veterancy_attack");

    static final int ACCRUAL_INTERVAL_TICKS = 200;
    static final int WARNING_INTERVAL_TICKS = 40;
    static final double RANK_UP_LINE_RADIUS_BLOCKS = 32.0;
    static final int RANK_UP_HEART_PARTICLES = 7;

    /** Every loaded {@link TamableAnimal}, membership-agnostic — coverage is config-reloadable,
     * so the sweeps re-filter instead of the tracker deciding once at load. */
    private static final Set<TamableAnimal> TRACKED = new LinkedHashSet<>();
    private static final WarningTracker WARNINGS = new WarningTracker();
    private static int tickCounter;

    private VeterancyHandler() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof TamableAnimal pet)) {
                return;
            }
            TRACKED.add(pet);
            try {
                onPetLoad(pet);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to apply veterancy on load to {}", entity.getType(), e);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof TamableAnimal pet) {
                TRACKED.remove(pet);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(VeterancyHandler::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                WARNINGS.forgetOwner(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            TRACKED.clear();
            WARNINGS.clear();
            tickCounter = 0;
        });
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % WARNING_INTERVAL_TICKS == 0) {
            try {
                warningPass();
            } catch (Exception e) {
                Instinct.LOGGER.error("Veterancy warning sweep failed", e);
            }
        }
        if (tickCounter % ACCRUAL_INTERVAL_TICKS == 0) {
            try {
                accrualPass();
            } catch (Exception e) {
                Instinct.LOGGER.error("Veterancy accrual sweep failed", e);
            }
        }
    }

    /**
     * The 200-tick live-accrual sweep: composes the provider rate with the mentor bonus, accrues,
     * and re-derives rank and bonuses — celebrating live crossings. With veterancy disabled the
     * pass only strips stale bonuses, so a toggle takes effect within one interval. Public
     * (internal, not API) so gametests can drive a deterministic pass.
     */
    public static void accrualPass() {
        InstinctConfig config = InstinctConfig.get();
        TRACKED.removeIf(pet -> pet.isRemoved());
        if (!config.enableVeterancy) {
            for (TamableAnimal pet : List.copyOf(TRACKED)) {
                applyBonuses(pet, 0);
            }
            return;
        }
        for (TamableAnimal pet : List.copyOf(TRACKED)) {
            try {
                accruePet(pet, config);
            } catch (Exception e) {
                Instinct.LOGGER.error("Veterancy accrual failed for {}", pet.getType(), e);
            }
        }
    }

    private static void accruePet(TamableAnimal pet, InstinctConfig config) {
        if (!pet.isTame() || !AnimalCoverage.membershipOf(pet).pet()) {
            // Untamed (or uncovered): accrual stops and bonuses drop; the attachment is retained
            // so re-taming the same animal resumes from its prior days.
            applyBonuses(pet, 0);
            return;
        }
        long now = pet.level().getGameTime();
        VeterancyData data = pet.getAttachedOrCreate(InstinctAttachments.VETERANCY);
        int oldRank = Veterancy.rankFor(data.accruedDays(), config.veterancyThresholdDays);
        double provider = InstinctAPI.resolveVeterancyRate(pet);
        boolean mentored = config.enableRankBehaviors && oldRank < 3 && mentorNearby(pet, config);
        VeterancyData updated = Veterancy.accrue(data, now,
                Veterancy.liveRate(provider, mentored, config.mentorRateBonus));
        pet.setAttached(InstinctAttachments.VETERANCY, updated);

        int newRank = Veterancy.rankFor(updated.accruedDays(), config.veterancyThresholdDays);
        applyBonuses(pet, newRank);
        if (newRank > oldRank) {
            celebrate(pet, newRank - oldRank, config);
        }
        fireRankCriterion(pet, newRank);
    }

    /**
     * Whether a mentor steadies this pet: any loaded, alive, tamed, non-downed pets-set animal at
     * rank 3 within {@code mentorRadiusBlocks} — any owner (a shared kennel benefits everyone's
     * pups). Non-stacking by construction: one match is all the rate ever uses.
     */
    private static boolean mentorNearby(TamableAnimal pet, InstinctConfig config) {
        double radiusSq = (double) config.mentorRadiusBlocks * config.mentorRadiusBlocks;
        for (TamableAnimal other : TRACKED) {
            if (other == pet || other.isRemoved() || !other.isAlive() || !other.isTame()
                    || other.level() != pet.level()
                    || other.distanceToSqr(pet) > radiusSq
                    || InstinctAPI.isDowned(other)) {
                continue;
            }
            if (InstinctAPI.getVeterancyRank(other) == 3 && AnimalCoverage.membershipOf(other).pet()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The rank-up moment (live crossings only — offline/unloaded crossings apply silently on the
     * next load): heal by the gained increment, 7 heart particles, the rank-up cue at the pet,
     * and the owner's ✦ action-bar line within 32 blocks.
     */
    private static void celebrate(TamableAnimal pet, int ranksGained, InstinctConfig config) {
        pet.heal((float) (config.healthPerRank * ranksGained));
        if (pet.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART,
                    pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(),
                    RANK_UP_HEART_PARTICLES, pet.getBbWidth() * 0.6, 0.3, pet.getBbWidth() * 0.6, 0.02);
            level.playSound(null, pet, InstinctSounds.RANK_UP, pet.getSoundSource(), 1.0F, 1.0F);
        }
        if (pet.getOwner() instanceof ServerPlayer owner
                && owner.level() == pet.level()
                && owner.distanceToSqr(pet) <= RANK_UP_LINE_RADIUS_BLOCKS * RANK_UP_LINE_RADIUS_BLOCKS) {
            owner.displayClientMessage(
                    Component.translatable("notification.instinct.rank_up", pet.getName()), true);
        }
    }

    /**
     * Load-time reconciliation: catch up the unloaded gap at rate 1.0 (provider and mentor rates
     * apply only to live accrual), then re-derive rank and bonuses silently — threshold config
     * edits promote or demote here, and no moment plays for gaps crossed while unloaded.
     */
    private static void onPetLoad(TamableAnimal pet) {
        if (!InstinctConfig.get().enableVeterancy || !pet.isTame()
                || !AnimalCoverage.membershipOf(pet).pet()) {
            applyBonuses(pet, 0);
            return;
        }
        VeterancyData data = pet.getAttachedOrCreate(InstinctAttachments.VETERANCY);
        pet.setAttached(InstinctAttachments.VETERANCY,
                Veterancy.accrue(data, pet.level().getGameTime(), 1.0));
        reassert(pet);
    }

    /**
     * The shared re-derive choke point ({@code /instinct set veterancy}, entity load): re-asserts
     * the fixed-id attribute bonuses from the current derived rank (stripping them when veterancy
     * is disabled, the pet untamed, or the type uncovered) and fires the {@code instinct:pet_rank}
     * criterion for an online owner. Returns the derived rank. Public (internal, not API) so
     * gametests can drive it directly.
     */
    public static int reassert(TamableAnimal pet) {
        boolean active = InstinctConfig.get().enableVeterancy && pet.isTame()
                && AnimalCoverage.membershipOf(pet).pet();
        int rank = InstinctAPI.getVeterancyRank(pet);
        applyBonuses(pet, active ? rank : 0);
        if (active) {
            fireRankCriterion(pet, rank);
        }
        return rank;
    }

    /**
     * Sets accrued days outright (the {@code /instinct set veterancy} core) and re-derives rank
     * and bonuses immediately. Returns the new derived rank.
     */
    public static int setAccruedDays(TamableAnimal pet, double days) {
        pet.setAttached(InstinctAttachments.VETERANCY,
                new VeterancyData(days, pet.level().getGameTime()));
        return reassert(pet);
    }

    /**
     * Applies (or strips, at rank 0) the two fixed-id bonuses. {@code addOrReplacePermanentModifier}
     * recomputes from the current rank so re-applying replaces, never stacks, and the modifiers
     * persist in vanilla's own entity NBT. Pets without an attack attribute get the health bonus
     * only. Current health is clamped to the (possibly lowered) max on demotion.
     */
    static void applyBonuses(TamableAnimal pet, int rank) {
        InstinctConfig config = InstinctConfig.get();
        AttributeInstance health = pet.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            if (rank <= 0) {
                health.removeModifier(HEALTH_MODIFIER_ID);
            } else {
                health.addOrReplacePermanentModifier(new AttributeModifier(
                        HEALTH_MODIFIER_ID, config.healthPerRank * rank,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance attack = pet.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            if (rank <= 0) {
                attack.removeModifier(ATTACK_MODIFIER_ID);
            } else {
                attack.addOrReplacePermanentModifier(new AttributeModifier(
                        ATTACK_MODIFIER_ID, config.damagePerRank * rank,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (pet.getHealth() > pet.getMaxHealth()) {
            pet.setHealth(pet.getMaxHealth());
        }
    }

    private static void fireRankCriterion(TamableAnimal pet, int rank) {
        if (rank > 0 && pet.getOwner() instanceof ServerPlayer owner) {
            InstinctCriteria.PET_RANK.trigger(owner, rank);
        }
    }

    /**
     * The sweep-attack guard ({@code PlayerMixin}): whether this victim is the attacker's own
     * rank-2+ pets-set pet, which learned to duck the arc. Direct hits are unaffected — this is
     * only consulted for the sweep's area-damage victims. Cheapest tests first: this sits on the
     * melee hot path.
     */
    public static boolean ducksSweep(LivingEntity victim, Player attacker) {
        if (!(victim instanceof TamableAnimal pet) || !pet.isTame() || !pet.isOwnedBy(attacker)) {
            return false;
        }
        if (!AnimalCoverage.membershipOf(pet).pet()) {
            return false;
        }
        return Veterancy.ducksSweep(InstinctAPI.getVeterancyRank(pet), true);
    }

    /**
     * The 40-tick warning sweep: one AABB monster query per online owner with loaded rank-1+
     * pets; each threat targeting that owner gets one warning per 300 ticks, voiced by the
     * nearest eligible pet (which faces it — a sitting pet warns without standing). Returns the
     * number of warnings fired. Public (internal, not API) so gametests can drive a
     * deterministic pass.
     */
    public static int warningPass() {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableVeterancy || !config.enableRankBehaviors) {
            return 0;
        }
        TRACKED.removeIf(pet -> pet.isRemoved());
        Map<ServerPlayer, List<TamableAnimal>> byOwner = new LinkedHashMap<>();
        for (TamableAnimal pet : TRACKED) {
            if (!pet.isAlive() || !pet.isTame() || InstinctAPI.isDowned(pet)
                    || InstinctAPI.getVeterancyRank(pet) < 1
                    || !AnimalCoverage.membershipOf(pet).pet()
                    || !(pet.getOwner() instanceof ServerPlayer owner)
                    || owner.level() != pet.level()) {
                continue;
            }
            byOwner.computeIfAbsent(owner, o -> new ArrayList<>()).add(pet);
        }
        int fired = 0;
        for (Map.Entry<ServerPlayer, List<TamableAnimal>> entry : byOwner.entrySet()) {
            try {
                fired += warnOwner(entry.getKey(), entry.getValue(), config);
            } catch (Exception e) {
                Instinct.LOGGER.error("Veterancy warning failed for {}'s pets",
                        entry.getKey().getGameProfile().getName(), e);
            }
        }
        return fired;
    }

    private static int warnOwner(ServerPlayer owner, List<TamableAnimal> pets, InstinctConfig config) {
        double radius = config.warningRadiusBlocks;
        AABB box = pets.get(0).getBoundingBox();
        for (TamableAnimal pet : pets) {
            box = box.minmax(pet.getBoundingBox());
        }
        ServerLevel level = (ServerLevel) owner.level();
        long now = level.getGameTime();
        int fired = 0;
        for (Mob threat : level.getEntitiesOfClass(Mob.class, box.inflate(radius),
                mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() == owner)) {
            if (WARNINGS.warnedRecently(owner.getUUID(), threat.getId(), now)) {
                continue;
            }
            TamableAnimal nearest = null;
            double best = radius * radius;
            for (TamableAnimal pet : pets) {
                double distSq = pet.distanceToSqr(threat);
                if (distSq <= best) {
                    best = distSq;
                    nearest = pet;
                }
            }
            if (nearest == null) {
                continue;
            }
            warn(nearest, threat);
            WARNINGS.markWarned(owner.getUUID(), threat.getId(), now);
            fired++;
        }
        return fired;
    }

    /** Face the threat and speak — the pet's own voice is the whole message (no text, no particles). */
    private static void warn(TamableAnimal pet, Mob threat) {
        pet.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        SoundEvent voice = warningVoice(pet, threat);
        if (voice != null) {
            pet.level().playSound(null, pet, voice, pet.getSoundSource(), 1.0F, pet.getVoicePitch());
        }
    }

    /**
     * The warning voice, always the species' own: the wolf growls, the cat hisses, the parrot
     * imitates the threat, and every other species (modded included) uses its own hurt sound. A
     * species with no registered sound warns silently (faces the threat only) — never a
     * substituted foreign sound.
     */
    private static SoundEvent warningVoice(TamableAnimal pet, Mob threat) {
        if (pet instanceof Wolf) {
            return SoundEvents.WOLF_GROWL;
        }
        if (pet instanceof Cat) {
            return SoundEvents.CAT_HISS;
        }
        if (pet instanceof Parrot) {
            return ParrotSoundInvoker.instinct$getImitatedSound(threat.getType());
        }
        return ((LivingEntitySoundInvoker) pet).instinct$getHurtSound(
                pet.level().damageSources().generic());
    }
}
