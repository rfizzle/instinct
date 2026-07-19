package com.rfizzle.instinct.downed;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.InstinctAnimalDownedCallback;
import com.rfizzle.instinct.api.InstinctAnimalRevivedCallback;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.coverage.CoverageResolver;
import com.rfizzle.instinct.coverage.OwnedAnimals;
import com.rfizzle.instinct.data.DownedData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.data.InstinctItemTagProvider;
import com.rfizzle.instinct.kennel.Kennel;
import com.rfizzle.instinct.kennel.KennelPosts;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The downed engine ({@code design/SPEC.md} §7). A lethal blow to a tamed pets-set animal or a
 * tamed mounts-set animal (the horse family) that is not <b>beyond saving</b> (fire, lava, void,
 * {@code /kill}) is cancelled into the downed state — health pinned to 1.0,
 * {@link Entity#setInvulnerable invulnerable} (which alone yields the correct "immune to everything
 * except void and {@code /kill}" semantics), AI stopped, any rider ejected, and (for a pet) sat in
 * the best-effort vanilla pose. Invulnerability also makes the animal untargetable for free —
 * vanilla targeting drops any entity whose {@code canBeSeenAsEnemy()} is false — and an on-down
 * sweep clears the target of any mob already locked on. Any player revives it with a
 * {@code #instinct:revive_items} item through {@link UseEntityCallback}, before vanilla
 * interactions; wrong items and empty-hand interactions are suppressed while downed.
 *
 * <p>A mount has no sit pose and no veterancy: the AI-stop, whine, and particle carry the downed
 * read, and revival takes no rank penalty. Ejecting the rider matters only for mounts — a downed
 * mount kept mounted could still be steered, since {@code AbstractHorse} lets its rider drive it
 * regardless of {@code setNoAi} — and is a harmless no-op for a pet, which is never a vehicle. The
 * public callbacks ({@link InstinctAnimalDownedCallback}/{@link InstinctAnimalRevivedCallback})
 * fire for every covered animal, pet and mount alike, on every transition — including the
 * kennel-post recovery path, which carries no reviver and no item.
 *
 * <p>All state here is server-thread-confined and transient: {@code DOWNED} mirrors loaded downed
 * animals for the whine sweep, {@code POST_REVIVE_INVULN} tracks the brief post-revive grace
 * window. The downed flag itself is the persistent {@link DownedData} attachment;
 * {@code setInvulnerable} / {@code setNoAi} / the sit pose ride vanilla entity NBT. Everything
 * clears on {@code SERVER_STOPPED}.
 */
public final class DownedHandler {

    /** Whine + smoke cadence for a downed pet (SPEC §7). */
    static final int WHINE_INTERVAL_TICKS = 100;
    /** How often a downed pet checks for a nearby kennel post and advances its recovery (SPEC §9). */
    static final int RECOVERY_CHECK_INTERVAL_TICKS = 20;
    /** Post-revive invulnerability window (SPEC §7). */
    static final int POST_REVIVE_INVULN_TICKS = 60;
    /** Radius of the on-down sweep that clears mobs already targeting the pet. */
    static final double UNTARGET_RADIUS_BLOCKS = 16.0;
    static final int REVIVE_HEART_PARTICLES = 5;

    /** Loaded downed animals, driving the whine/smoke sweep. Membership-agnostic re-filter each pass. */
    private static final Set<Animal> DOWNED = new LinkedHashSet<>();
    /** Revived animals still inside their post-revive invulnerability window: animal → game time it ends. */
    private static final Map<Animal, Long> POST_REVIVE_INVULN = new LinkedHashMap<>();

    private DownedHandler() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register(DownedHandler::onAllowDeath);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Animal animal && InstinctAPI.isDowned(animal)) {
                DOWNED.add(animal);
                try {
                    reassertDownedState(animal);
                } catch (Exception e) {
                    Instinct.LOGGER.error("Failed to re-assert downed state on load for {}", entity.getType(), e);
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Animal animal) {
                DOWNED.remove(animal);
                clearPostReviveInvuln(animal);
            }
        });
        UseEntityCallback.EVENT.register(DownedHandler::onUseEntity);
        ServerTickEvents.END_SERVER_TICK.register(DownedHandler::onServerTick);
        // Clear the post-revive window BEFORE the world save. SERVER_STOPPING fires at the head of
        // the shutdown, ahead of the chunk save; SERVER_STOPPED fires after it. Clearing the
        // vanilla-NBT-backed invulnerable flag here keeps a pet revived just before /stop from
        // persisting permanent invulnerability to disk (a downed pet stays invulnerable by design).
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Animal animal : POST_REVIVE_INVULN.keySet()) {
                try {
                    if (!animal.isRemoved() && !InstinctAPI.isDowned(animal)) {
                        animal.setInvulnerable(false);
                    }
                } catch (Exception e) {
                    Instinct.LOGGER.error("Failed to clear post-revive invulnerability on stop for {}",
                            animal.getType(), e);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DOWNED.clear();
            POST_REVIVE_INVULN.clear();
        });
    }

    // --- Going down -------------------------------------------------------------------------

    private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Animal animal)) {
            return true;
        }
        try {
            return allowDeath(animal, source);
        } catch (Exception e) {
            // Fail open: a broken downed check must never leave an animal unkillable.
            Instinct.LOGGER.error("Downed death check failed for {}", animal.getType(), e);
            return true;
        }
    }

    /**
     * The death verdict. Beyond-saving blows always kill — downed or not. An already-downed animal
     * otherwise stays down (its invulnerability should have absorbed the blow; this is a defensive
     * re-pin). A healthy tamed covered animal — a pets-set pet or a mounts-set mount — goes down
     * instead of dying while the feature is on; with it off, deaths are exactly vanilla and no new
     * downs occur.
     */
    static boolean allowDeath(Animal animal, DamageSource source) {
        if (Downed.beyondSaving(source)) {
            return true;
        }
        if (InstinctAPI.isDowned(animal)) {
            animal.setHealth(1.0F);
            return false;
        }
        if (!InstinctConfig.get().enableDownedState || !OwnedAnimals.isTamed(animal)
                || !covered(animal)) {
            return true;
        }
        goDown(animal, source);
        return false;
    }

    /** Whether the animal is in a set the downed state covers — pets or mounts (SPEC §7). */
    private static boolean covered(Animal animal) {
        CoverageResolver.Membership membership = AnimalCoverage.membershipOf(animal);
        return membership.pet() || membership.mount();
    }

    private static void goDown(Animal animal, DamageSource source) {
        animal.setAttached(InstinctAttachments.DOWNED, new DownedData(animal.level().getGameTime()));
        animal.setHealth(1.0F);
        animal.setInvulnerable(true);
        animal.setNoAi(true);
        // A ridden mount stays steerable through setNoAi, so drop any rider; a pet is never a
        // vehicle, making this a no-op there.
        animal.ejectPassengers();
        if (animal instanceof TamableAnimal pet) {
            pet.setOrderedToSit(true);
        }
        animal.getNavigation().stop();
        animal.setTarget(null);
        clearAttackers(animal);
        DOWNED.add(animal);
        POST_REVIVE_INVULN.remove(animal);
        notifyOwnerDown(animal);
        fireDownedCallback(animal, source);
    }

    /** Clears the target of every mob currently locked onto the animal, so aggressors visibly stand down. */
    private static void clearAttackers(Animal animal) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return;
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                animal.getBoundingBox().inflate(UNTARGET_RADIUS_BLOCKS), m -> m.getTarget() == animal)) {
            mob.setTarget(null);
        }
    }

    /** The single owner chat line — sent to chat, not the action bar, so it cannot be missed (SPEC §7). */
    private static void notifyOwnerDown(Animal animal) {
        UUID ownerUUID = OwnedAnimals.ownerUUID(animal);
        if (ownerUUID == null || !(animal.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner != null) {
            owner.sendSystemMessage(
                    Component.translatable("notification.instinct.pet_downed", animal.getName()));
        }
    }

    /** The single owner chat line when a pet recovers on its own at its post (SPEC §9) — sent to chat,
     *  like the down notice, so an owner who wasn't watching still learns their pet is up. */
    private static void notifyOwnerRecovered(Animal animal) {
        UUID ownerUUID = OwnedAnimals.ownerUUID(animal);
        if (ownerUUID == null || !(animal.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner != null) {
            owner.sendSystemMessage(
                    Component.translatable("notification.instinct.pet_recovered", animal.getName()));
        }
    }

    /** Idempotent load-time re-assertion in case a datapack or mod cleared a persisted downed flag. */
    private static void reassertDownedState(Animal animal) {
        animal.setInvulnerable(true);
        animal.setNoAi(true);
        if (animal instanceof TamableAnimal pet) {
            pet.setOrderedToSit(true);
        }
    }

    // --- Revival ----------------------------------------------------------------------------

    private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand,
                                                 Entity entity, @Nullable EntityHitResult hitResult) {
        if (!(entity instanceof Animal animal) || !InstinctAPI.isDowned(animal)) {
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
            revive(animal, player, stack);
        } catch (Exception e) {
            Instinct.LOGGER.error("Revival failed for {}", animal.getType(), e);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Brings a downed animal back via a revival item: the shared state restore (with the config rank
     * penalty for pets), then the reviving player's feedback, the advancement, and the public callback.
     */
    static void revive(Animal animal, Player reviver, ItemStack stack) {
        restoreFromDowned(animal, InstinctConfig.get().downedRankPenalty);
        if (reviver instanceof ServerPlayer serverReviver) {
            serverReviver.displayClientMessage(
                    Component.translatable("notification.instinct.pet_revived", animal.getName()), true);
            InstinctCriteria.PET_REVIVED.trigger(serverReviver);
        }
        fireRevivedCallback(animal, reviver, stack);
    }

    /**
     * The shared "back on their feet" state restore, common to item revival and kennel-post recovery.
     * The rank penalty (pets only — a mount has no veterancy) is applied <b>first</b> so the restored
     * health fraction reflects the post-demotion max health (a demotion lowers max health); then AI,
     * the Stay pose (pets only), health, Regeneration II, the post-revive invulnerability window, and
     * the cue land. A carried pet dismounts first, so it comes back on the ground and its carrier's
     * slowdown clears.
     */
    private static void restoreFromDowned(Animal animal, boolean withRankPenalty) {
        CarryHandler.releaseIfCarried(animal);
        InstinctConfig config = InstinctConfig.get();
        if (withRankPenalty && animal instanceof TamableAnimal pet) {
            applyRankPenalty(pet, config);
        }
        animal.removeAttached(InstinctAttachments.DOWNED);
        DOWNED.remove(animal);
        animal.setNoAi(false);
        if (animal instanceof TamableAnimal pet) {
            pet.setOrderedToSit(true);
        }
        animal.setHealth((float) (config.reviveHealthFraction * animal.getMaxHealth()));
        animal.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        animal.setInvulnerable(true);
        POST_REVIVE_INVULN.put(animal, animal.level().getGameTime() + POST_REVIVE_INVULN_TICKS);
        reviveEffects(animal);
    }

    private static void applyRankPenalty(TamableAnimal pet, InstinctConfig config) {
        int rank = InstinctAPI.getVeterancyRank(pet);
        if (rank <= 0) {
            return;
        }
        VeterancyHandler.setAccruedDays(pet,
                Veterancy.daysForRankBelow(rank, config.veterancyThresholdDays));
    }

    private static void reviveEffects(Animal animal) {
        if (animal.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART,
                    animal.getX(), animal.getY() + animal.getBbHeight() + 0.3, animal.getZ(),
                    REVIVE_HEART_PARTICLES, animal.getBbWidth() * 0.6, 0.3, animal.getBbWidth() * 0.6, 0.02);
            level.playSound(null, animal, InstinctSounds.REVIVE, animal.getSoundSource(), 1.0F, 1.0F);
        }
    }

    // --- Cadence & lifecycle ----------------------------------------------------------------

    private static void onServerTick(MinecraftServer server) {
        try {
            expirePostReviveInvuln();
        } catch (Exception e) {
            Instinct.LOGGER.error("Post-revive invuln expiry failed", e);
        }
        try {
            whinePass();
        } catch (Exception e) {
            Instinct.LOGGER.error("Downed whine sweep failed", e);
        }
        try {
            recoveryPass();
        } catch (Exception e) {
            Instinct.LOGGER.error("Downed recovery sweep failed", e);
        }
    }

    /**
     * The kennel-post recovery sweep (SPEC §9): a downed pet beside a kennel post gets back up on its
     * own, slowly, without an item and without losing a rank. Reuses the bounded {@link #DOWNED} set,
     * runs only when the feature is on, and only advances each pet on its own staggered recovery beat —
     * so the per-post block scan touches only the rare downed pets, on a coarse cadence, over a
     * config-clamped radius. A downed mount is not the pack's — only pets-set animals recover here.
     */
    private static void recoveryPass() {
        if (DOWNED.isEmpty()) {
            return;
        }
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableKennelPost) {
            return;
        }
        int radius = config.kennelRecoveryRadiusBlocks;
        int threshold = Kennel.recoveryThresholdTicks(config.kennelRecoverySeconds);
        // Snapshot: recoverAtPost removes from DOWNED, so we can't iterate the live set.
        for (Animal animal : new ArrayList<>(DOWNED)) {
            try {
                if (isRecoveryTick(animal)) {
                    advanceRecovery(animal, radius, threshold);
                }
            } catch (Exception e) {
                Instinct.LOGGER.error("Downed recovery failed for {}", animal.getType(), e);
            }
        }
    }

    /** Whether this tick is a recovery beat for the animal — staggered off its own down time, like the
     *  whine, so a group of downed pets don't all scan for posts on the same tick. */
    private static boolean isRecoveryTick(Animal animal) {
        DownedData data = animal.getAttached(InstinctAttachments.DOWNED);
        if (data == null) {
            return false;
        }
        long elapsed = animal.level().getGameTime() - data.downedAtGameTime();
        return elapsed > 0 && elapsed % RECOVERY_CHECK_INTERVAL_TICKS == 0;
    }

    /** Advances one pet's recovery: paused unless it is a pets-set animal beside a kennel post, then
     *  accumulates a beat of progress and gets the pet up once the threshold is reached. */
    private static void advanceRecovery(Animal animal, int radius, int threshold) {
        DownedData data = animal.getAttached(InstinctAttachments.DOWNED);
        if (data == null || !AnimalCoverage.membershipOf(animal).pet()) {
            return;
        }
        if (!KennelPosts.hasPostWithin(animal.level(), animal.blockPosition(), radius)) {
            return; // not beside a post — a downed pet can't move itself, so recovery just waits
        }
        int progress = data.recoveryTicks() + RECOVERY_CHECK_INTERVAL_TICKS;
        if (Kennel.recoveryComplete(progress, threshold)) {
            recoverAtPost(animal);
        } else {
            animal.setAttached(InstinctAttachments.DOWNED, data.withRecoveryTicks(progress));
        }
    }

    /** Brings a pet back on its own beside its post: the shared state restore, always rank-free (SPEC
     *  §9 — the patient path keeps the rank the field item would spend), then the owner's notice. No
     *  reviving player, so no item feedback and no advancement — but the public revived callback
     *  still fires, with a null reviver and an empty stack, so a consumer sees every path back up. */
    private static void recoverAtPost(Animal animal) {
        restoreFromDowned(animal, false);
        notifyOwnerRecovered(animal);
        fireRevivedCallback(animal, null, ItemStack.EMPTY);
    }

    private static void expirePostReviveInvuln() {
        if (POST_REVIVE_INVULN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Animal, Long>> it = POST_REVIVE_INVULN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Animal, Long> entry = it.next();
            Animal animal = entry.getKey();
            if (animal.isRemoved()) {
                it.remove();
                continue;
            }
            if (animal.level().getGameTime() >= entry.getValue()) {
                if (!InstinctAPI.isDowned(animal)) {
                    animal.setInvulnerable(false);
                }
                it.remove();
            }
        }
    }

    private static void whinePass() {
        if (DOWNED.isEmpty()) {
            return;
        }
        DOWNED.removeIf(animal -> animal.isRemoved() || !InstinctAPI.isDowned(animal));
        for (Animal animal : DOWNED) {
            try {
                if (isWhineTick(animal)) {
                    whine(animal);
                }
            } catch (Exception e) {
                Instinct.LOGGER.error("Downed whine failed for {}", animal.getType(), e);
            }
        }
    }

    /**
     * Whether this tick is a whine beat for the animal — every {@code WHINE_INTERVAL_TICKS} ticks
     * on the animal's own clock, anchored to when it went down, so a group of downed animals
     * whimper out of sync rather than in unison.
     */
    private static boolean isWhineTick(Animal animal) {
        DownedData data = animal.getAttached(InstinctAttachments.DOWNED);
        if (data == null) {
            return false;
        }
        long elapsed = animal.level().getGameTime() - data.downedAtGameTime();
        return elapsed > 0 && elapsed % WHINE_INTERVAL_TICKS == 0;
    }

    /** The species' own hurt sound at half volume plus one smoke wisp — the whole downed read. */
    private static void whine(Animal animal) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return;
        }
        SoundEvent hurt = ((LivingEntitySoundInvoker) animal)
                .instinct$getHurtSound(animal.level().damageSources().generic());
        if (hurt != null) {
            level.playSound(null, animal, hurt, animal.getSoundSource(), 0.5F, animal.getVoicePitch());
        }
        level.sendParticles(ParticleTypes.SMOKE,
                animal.getX(), animal.getY() + animal.getBbHeight() + 0.2, animal.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void clearPostReviveInvuln(Animal animal) {
        if (POST_REVIVE_INVULN.remove(animal) != null && !InstinctAPI.isDowned(animal)) {
            // Clear before an unload persists a permanent-invulnerability flag to disk.
            animal.setInvulnerable(false);
        }
    }

    // Both fire sites catch Throwable, not Exception: this is the boundary where untrusted listener
    // code runs, and a consumer compiled against an older signature throws Error (AbstractMethodError,
    // NoClassDefFoundError), which an Exception catch would let escape and kill the server tick.
    private static void fireDownedCallback(Animal animal, DamageSource source) {
        try {
            InstinctAnimalDownedCallback.EVENT.invoker().onAnimalDowned(animal, source);
        } catch (Throwable t) {
            Instinct.LOGGER.error("An animal-downed listener threw", t);
        }
    }

    /** {@code reviver} is null and {@code item} empty on the kennel-post recovery path (SPEC §9). */
    private static void fireRevivedCallback(Animal animal, @Nullable Player reviver, ItemStack item) {
        try {
            InstinctAnimalRevivedCallback.EVENT.invoker().onAnimalRevived(animal, reviver, item);
        } catch (Throwable t) {
            Instinct.LOGGER.error("An animal-revived listener threw", t);
        }
    }
}
