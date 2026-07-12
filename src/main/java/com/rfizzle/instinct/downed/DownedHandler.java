package com.rfizzle.instinct.downed;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.InstinctPetDownedCallback;
import com.rfizzle.instinct.api.InstinctPetRevivedCallback;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.DownedData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.InstinctItemTagProvider;
import com.rfizzle.instinct.mixin.LivingEntitySoundInvoker;
import com.rfizzle.instinct.registry.InstinctCriteria;
import com.rfizzle.instinct.registry.InstinctSounds;
import com.rfizzle.instinct.veterancy.Veterancy;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The downed-pets engine ({@code design/SPEC.md} §7). A lethal blow to a tamed pets-set animal that
 * is not <b>beyond saving</b> (fire, lava, void, {@code /kill}) is cancelled into the downed state
 * — health pinned to 1.0, {@link Entity#setInvulnerable invulnerable} (which alone yields the
 * correct "immune to everything except void and {@code /kill}" semantics), AI stopped, and sat in
 * the best-effort vanilla pose. Invulnerability also makes the pet untargetable for free — vanilla
 * targeting drops any entity whose {@code canBeSeenAsEnemy()} is false — and an on-down sweep
 * clears the target of any mob already locked on. Any player revives it with a
 * {@code #instinct:revive_items} item through {@link UseEntityCallback}, before vanilla
 * interactions; wrong items and empty-hand interactions are suppressed while downed.
 *
 * <p>All state here is server-thread-confined and transient: {@code DOWNED} mirrors loaded downed
 * pets for the whine sweep, {@code POST_REVIVE_INVULN} tracks the brief post-revive grace window.
 * The downed flag itself is the persistent {@link DownedData} attachment; {@code setInvulnerable} /
 * {@code setNoAi} / the sit pose ride vanilla entity NBT. Everything clears on {@code
 * SERVER_STOPPED}.
 */
public final class DownedHandler {

    /** Whine + smoke cadence for a downed pet (SPEC §7). */
    static final int WHINE_INTERVAL_TICKS = 100;
    /** Post-revive invulnerability window (SPEC §7). */
    static final int POST_REVIVE_INVULN_TICKS = 60;
    /** Radius of the on-down sweep that clears mobs already targeting the pet. */
    static final double UNTARGET_RADIUS_BLOCKS = 16.0;
    static final int REVIVE_HEART_PARTICLES = 5;

    /** Loaded downed pets, driving the whine/smoke sweep. Membership-agnostic re-filter each pass. */
    private static final Set<TamableAnimal> DOWNED = new LinkedHashSet<>();
    /** Revived pets still inside their post-revive invulnerability window: pet → game time it ends. */
    private static final Map<TamableAnimal, Long> POST_REVIVE_INVULN = new LinkedHashMap<>();
    private static int tickCounter;

    private DownedHandler() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register(DownedHandler::onAllowDeath);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof TamableAnimal pet && InstinctAPI.isDowned(pet)) {
                DOWNED.add(pet);
                try {
                    reassertDownedState(pet);
                } catch (Exception e) {
                    Instinct.LOGGER.error("Failed to re-assert downed state on load for {}", entity.getType(), e);
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof TamableAnimal pet) {
                DOWNED.remove(pet);
                clearPostReviveInvuln(pet);
            }
        });
        UseEntityCallback.EVENT.register(DownedHandler::onUseEntity);
        ServerTickEvents.END_SERVER_TICK.register(DownedHandler::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (TamableAnimal pet : POST_REVIVE_INVULN.keySet()) {
                if (!pet.isRemoved() && !InstinctAPI.isDowned(pet)) {
                    pet.setInvulnerable(false);
                }
            }
            DOWNED.clear();
            POST_REVIVE_INVULN.clear();
            tickCounter = 0;
        });
    }

    // --- Going down -------------------------------------------------------------------------

    private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof TamableAnimal pet)) {
            return true;
        }
        try {
            return allowDeath(pet, source);
        } catch (Exception e) {
            // Fail open: a broken downed check must never leave a pet unkillable.
            Instinct.LOGGER.error("Downed death check failed for {}", pet.getType(), e);
            return true;
        }
    }

    /**
     * The death verdict. Beyond-saving blows always kill — downed or not. An already-downed pet
     * otherwise stays down (its invulnerability should have absorbed the blow; this is a defensive
     * re-pin). A healthy tamed covered pet goes down instead of dying while the feature is on;
     * with it off, deaths are exactly vanilla and no new downs occur.
     */
    static boolean allowDeath(TamableAnimal pet, DamageSource source) {
        if (Downed.beyondSaving(source)) {
            return true;
        }
        if (InstinctAPI.isDowned(pet)) {
            pet.setHealth(1.0F);
            return false;
        }
        if (!InstinctConfig.get().enableDownedState || !pet.isTame()
                || !AnimalCoverage.membershipOf(pet).pet()) {
            return true;
        }
        goDown(pet, source);
        return false;
    }

    private static void goDown(TamableAnimal pet, DamageSource source) {
        pet.setAttached(InstinctAttachments.DOWNED, new DownedData(pet.level().getGameTime()));
        pet.setHealth(1.0F);
        pet.setInvulnerable(true);
        pet.setNoAi(true);
        pet.setOrderedToSit(true);
        pet.getNavigation().stop();
        pet.setTarget(null);
        clearAttackers(pet);
        DOWNED.add(pet);
        POST_REVIVE_INVULN.remove(pet);
        notifyOwnerDown(pet);
        fireDownedCallback(pet, source);
    }

    /** Clears the target of every mob currently locked onto the pet, so aggressors visibly stand down. */
    private static void clearAttackers(TamableAnimal pet) {
        if (!(pet.level() instanceof ServerLevel level)) {
            return;
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                pet.getBoundingBox().inflate(UNTARGET_RADIUS_BLOCKS), m -> m.getTarget() == pet)) {
            mob.setTarget(null);
        }
    }

    /** The single owner chat line — sent to chat, not the action bar, so it cannot be missed (SPEC §7). */
    private static void notifyOwnerDown(TamableAnimal pet) {
        if (pet.getOwnerUUID() == null || !(pet.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(pet.getOwnerUUID());
        if (owner != null) {
            owner.sendSystemMessage(
                    Component.translatable("notification.instinct.pet_downed", pet.getName()));
        }
    }

    /** Idempotent load-time re-assertion in case a datapack or mod cleared a persisted downed flag. */
    private static void reassertDownedState(TamableAnimal pet) {
        pet.setInvulnerable(true);
        pet.setNoAi(true);
        pet.setOrderedToSit(true);
    }

    // --- Revival ----------------------------------------------------------------------------

    private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
                                                 Entity entity, @Nullable EntityHitResult hitResult) {
        if (!(entity instanceof TamableAnimal pet) || !InstinctAPI.isDowned(pet)) {
            return InteractionResult.PASS;
        }
        boolean reviveItem = player.getItemInHand(hand).is(InstinctItemTagProvider.REVIVE_ITEMS);
        if (level.isClientSide) {
            // Mirror the server's verdict so the client predicts no vanilla interaction.
            return reviveItem ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (!reviveItem) {
            // Wrong item or empty hand: every regular interaction is suppressed — no swing, no consume.
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        try {
            revive(pet, player, stack);
        } catch (Exception e) {
            Instinct.LOGGER.error("Revival failed for {}", pet.getType(), e);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Brings a downed pet back. The rank penalty is applied <b>first</b> so the restored health
     * fraction reflects the post-demotion max health (a demotion lowers max health); then health,
     * Regeneration II, the post-revive invulnerability window, the Stay pose, and the cue land.
     */
    static void revive(TamableAnimal pet, Player reviver, ItemStack stack) {
        InstinctConfig config = InstinctConfig.get();
        if (config.downedRankPenalty) {
            applyRankPenalty(pet, config);
        }
        pet.removeAttached(InstinctAttachments.DOWNED);
        DOWNED.remove(pet);
        pet.setNoAi(false);
        pet.setOrderedToSit(true);
        pet.setHealth((float) (config.reviveHealthFraction * pet.getMaxHealth()));
        pet.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        pet.setInvulnerable(true);
        POST_REVIVE_INVULN.put(pet, pet.level().getGameTime() + POST_REVIVE_INVULN_TICKS);
        reviveEffects(pet);
        if (reviver instanceof ServerPlayer serverReviver) {
            serverReviver.displayClientMessage(
                    Component.translatable("notification.instinct.pet_revived", pet.getName()), true);
            InstinctCriteria.PET_REVIVED.trigger(serverReviver);
        }
        fireRevivedCallback(pet, reviver, stack);
    }

    private static void applyRankPenalty(TamableAnimal pet, InstinctConfig config) {
        int rank = InstinctAPI.getVeterancyRank(pet);
        if (rank <= 0) {
            return;
        }
        VeterancyHandler.setAccruedDays(pet,
                Veterancy.daysForRankBelow(rank, config.veterancyThresholdDays));
    }

    private static void reviveEffects(TamableAnimal pet) {
        if (pet.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART,
                    pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(),
                    REVIVE_HEART_PARTICLES, pet.getBbWidth() * 0.6, 0.3, pet.getBbWidth() * 0.6, 0.02);
            level.playSound(null, pet, InstinctSounds.REVIVE, pet.getSoundSource(), 1.0F, 1.0F);
        }
    }

    // --- Cadence & lifecycle ----------------------------------------------------------------

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        expirePostReviveInvuln();
        if (tickCounter % WHINE_INTERVAL_TICKS == 0) {
            try {
                whinePass();
            } catch (Exception e) {
                Instinct.LOGGER.error("Downed whine sweep failed", e);
            }
        }
    }

    private static void expirePostReviveInvuln() {
        if (POST_REVIVE_INVULN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<TamableAnimal, Long>> it = POST_REVIVE_INVULN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TamableAnimal, Long> entry = it.next();
            TamableAnimal pet = entry.getKey();
            if (pet.isRemoved()) {
                it.remove();
                continue;
            }
            if (pet.level().getGameTime() >= entry.getValue()) {
                if (!InstinctAPI.isDowned(pet)) {
                    pet.setInvulnerable(false);
                }
                it.remove();
            }
        }
    }

    private static void whinePass() {
        DOWNED.removeIf(pet -> pet.isRemoved() || !InstinctAPI.isDowned(pet));
        for (TamableAnimal pet : DOWNED) {
            try {
                whine(pet);
            } catch (Exception e) {
                Instinct.LOGGER.error("Downed whine failed for {}", pet.getType(), e);
            }
        }
    }

    /** The species' own hurt sound at half volume plus one smoke wisp — the whole downed read. */
    private static void whine(TamableAnimal pet) {
        if (!(pet.level() instanceof ServerLevel level)) {
            return;
        }
        SoundEvent hurt = ((LivingEntitySoundInvoker) pet)
                .instinct$getHurtSound(pet.level().damageSources().generic());
        if (hurt != null) {
            level.playSound(null, pet, hurt, pet.getSoundSource(), 0.5F, pet.getVoicePitch());
        }
        level.sendParticles(ParticleTypes.SMOKE,
                pet.getX(), pet.getY() + pet.getBbHeight() + 0.2, pet.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void clearPostReviveInvuln(TamableAnimal pet) {
        if (POST_REVIVE_INVULN.remove(pet) != null && !InstinctAPI.isDowned(pet)) {
            // Clear before an unload persists a permanent-invulnerability flag to disk.
            pet.setInvulnerable(false);
        }
    }

    private static void fireDownedCallback(TamableAnimal pet, DamageSource source) {
        try {
            InstinctPetDownedCallback.EVENT.invoker().onPetDowned(pet, source);
        } catch (Exception e) {
            Instinct.LOGGER.error("A pet-downed listener threw", e);
        }
    }

    private static void fireRevivedCallback(TamableAnimal pet, Player reviver, ItemStack item) {
        try {
            InstinctPetRevivedCallback.EVENT.invoker().onPetRevived(pet, reviver, item);
        } catch (Exception e) {
            Instinct.LOGGER.error("A pet-revived listener threw", e);
        }
    }
}
