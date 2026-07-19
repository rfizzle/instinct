package com.rfizzle.instinct.herding;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.mixin.TemptGoalAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

/**
 * Flocking &amp; herding ({@code design/SPEC.md} §4). This class owns three things: the entity-load
 * wiring (swap an exact-class vanilla {@link TemptGoal} on covered livestock for a
 * {@link FlockingTemptGoal}, add a {@link HerdWorkGoal} to every tamed pet), the pure geometry the
 * drive assist and its tests share ({@link #pressPoint}, {@link #stragglersOf}, {@link #flockSpeed}),
 * and the transient <em>claim map</em> — the server-side record of which straggler each working pet
 * has claimed for the drive.
 *
 * <p>The claim map is confined to the server thread: every writer is a goal tick or a lifecycle
 * event, all of which run on the server thread. Claims carry a 200-tick expiry so an unreachable
 * straggler never stalls the drive; expired entries are pruned lazily on read and the whole map
 * clears on {@code SERVER_STOPPED} (and per-owner on disconnect), so nothing leaks across worlds or
 * players.
 */
public final class Herding {

    /** Priority of the pet's drive-work goal. Above {@code FollowOwnerGoal} (6 on wolf/cat) so
     *  working the drive preempts idle following, and above {@code WaterAvoidingRandomStrollGoal}
     *  (~8) so an idle pet stops wandering to work. It ties {@code MeleeAttackGoal} (5 on wolf): the
     *  engine can't have two equal-priority {@code MOVE} goals preempt each other, so combat winning
     *  is enforced not by priority but by {@code HerdWorkGoal.eligible()} standing the goal down the
     *  moment {@code getTarget() != null} — and {@code targetSelector} ticks before {@code
     *  goalSelector}, so that check is timely. Survival goals (float, panic, sit, the creeper berth)
     *  all sit below 5 and preempt it outright. Inert unless the owner is driving, so unrelated pets
     *  are untouched. */
    static final int HERD_WORK_PRIORITY = 5;

    /** A claim expires this many ticks after it was last refreshed (§4). */
    static final int CLAIM_EXPIRY_TICKS = 200;

    /** straggler UUID → its claim. Server-thread confined; see the class javadoc. */
    private static final Map<UUID, Claim> CLAIMS = new ConcurrentHashMap<>();

    /** An animal within 5 blocks of its orderer has arrived and drops out of the round-up (§6). */
    static final double ROUND_UP_ARRIVAL_DISTANCE = 5.0;

    /**
     * Live whistle round-up orders (§6): each ordered animal → the player it is being driven toward
     * and the tick the order expires. Server-thread confined like {@link #CLAIMS} — keyed by the
     * animal instance so the sweep and {@link FlockingTemptGoal} both look it up directly. Entries
     * drop on arrival, at the deadline, on animal removal, on {@code SERVER_STOPPED}, and per-orderer
     * on disconnect, so nothing leaks. The order is a second tempt source: an ordered animal reads as
     * tempted toward its orderer, so §4's press machinery (claims, behind-points, the {@code
     * herdingMaxPets} cap) presses it with no whistle-specific goal.
     */
    private static final Map<Animal, RoundUp> ROUND_UPS = new ConcurrentHashMap<>();

    private record Claim(UUID pressingPet, UUID owner, long expiryTick, boolean pressing) {
    }

    private record RoundUp(UUID orderer, long deadlineTick) {
    }

    private Herding() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            try {
                if (entity instanceof Animal animal && AnimalCoverage.isLivestock(animal)) {
                    swapTemptGoals(animal);
                }
                if (entity instanceof TamableAnimal pet) {
                    addHerdWorkGoal(pet);
                }
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to install herding on {}", entity.getType(), e);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Herding::sweepRoundUps);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Animal animal) {
                ROUND_UPS.remove(animal);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CLAIMS.clear();
            ROUND_UPS.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            releaseOwner(handler.player.getUUID());
            clearRoundUp(handler.player.getUUID());
        });
    }

    // ── Goal installation ───────────────────────────────────────────────────────────────────────

    /**
     * Replaces every exact-class vanilla {@link TemptGoal} on the animal with a
     * {@link FlockingTemptGoal} at the same priority, speed, items, and scare flag. Idempotent: a
     * re-load finds the already-installed subclass and bails. A modded {@code TemptGoal} subclass
     * (e.g. a cat's {@code CatTemptGoal}) is never an exact-class match, so it is left untouched.
     * All matches are collected before mutating the selector — an animal with two exact-class tempt
     * goals (a pig's food and carrot-on-a-stick goals) has both swapped, not just one.
     */
    private static void swapTemptGoals(Animal animal) {
        GoalSelector selector = animal.goalSelector;
        List<WrappedGoal> targets = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof FlockingTemptGoal) {
                return; // already swapped
            }
            if (wrapped.getGoal().getClass() == TemptGoal.class) {
                targets.add(wrapped);
            }
        }
        for (WrappedGoal target : targets) {
            TemptGoal goal = (TemptGoal) target.getGoal();
            TemptGoalAccessor access = (TemptGoalAccessor) goal;
            int priority = target.getPriority();
            FlockingTemptGoal replacement = new FlockingTemptGoal(animal,
                    access.instinct$getSpeedModifier(),
                    access.instinct$getItems(),
                    access.instinct$getCanScare());
            selector.removeGoal(goal);
            selector.addGoal(priority, replacement);
        }
    }

    /** Adds the drive-work goal to a pet once. Inert until the pet's owner is driving, so it is safe
     *  to add to every tamed animal regardless of live coverage or config. */
    private static void addHerdWorkGoal(TamableAnimal pet) {
        boolean present = pet.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof HerdWorkGoal);
        if (!present) {
            pet.goalSelector.addGoal(HERD_WORK_PRIORITY, new HerdWorkGoal(pet));
        }
    }

    /** The player an animal is currently flock-tempted toward, or {@code null} when it isn't. */
    static Player temptTargetOf(Animal animal) {
        for (WrappedGoal wrapped : animal.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof FlockingTemptGoal flocking) {
                Player target = flocking.getTemptedPlayer();
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    // ── Pure geometry (unit-tested; no Minecraft types) ─────────────────────────────────────────

    /**
     * The point {@code behind} blocks behind the straggler on the straggler→player axis, in the X/Z
     * plane. Returns {@code {x, z}}. When the straggler and player coincide the axis is undefined and
     * the straggler's own position is returned.
     */
    public static double[] pressPoint(double stragglerX, double stragglerZ,
                                      double playerX, double playerZ, double behind) {
        double dx = stragglerX - playerX;
        double dz = stragglerZ - playerZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            return new double[]{stragglerX, stragglerZ};
        }
        return new double[]{stragglerX + (dx / len) * behind, stragglerZ + (dz / len) * behind};
    }

    /**
     * The flock members strictly more than {@code thresholdBlocks} from the player, by X/Z distance.
     * Generic over the member type so the live drive passes animals and the unit test passes plain
     * coordinates through the same logic.
     */
    public static <T> List<T> stragglersOf(List<T> flock, ToDoubleFunction<T> xOf, ToDoubleFunction<T> zOf,
                                           double playerX, double playerZ, double thresholdBlocks) {
        double thresholdSq = thresholdBlocks * thresholdBlocks;
        List<T> out = new ArrayList<>();
        for (T member : flock) {
            double dx = xOf.applyAsDouble(member) - playerX;
            double dz = zOf.applyAsDouble(member) - playerZ;
            if (dx * dx + dz * dz > thresholdSq) {
                out.add(member);
            }
        }
        return out;
    }

    /**
     * A tempted animal's flock movement speed: its base tempt speed scaled by the flock multiplier,
     * and again by the press hustle while it is being pressed toward the player (§4).
     */
    public static double flockSpeed(double baseSpeed, double flockMultiplier, boolean pressed) {
        return baseSpeed * flockMultiplier * (pressed ? FlockingTemptGoal.PRESS_HUSTLE : 1.0);
    }

    // ── Claim map ───────────────────────────────────────────────────────────────────────────────

    /** Whether the straggler holds a live (unexpired) claim. */
    static boolean isClaimed(UUID straggler, long now) {
        Claim claim = CLAIMS.get(straggler);
        return claim != null && claim.expiryTick() > now;
    }

    /** Claims the straggler for a working pet if it is currently unclaimed; {@code true} on success. */
    static boolean tryClaim(UUID straggler, UUID pet, UUID owner, long now) {
        if (isClaimed(straggler, now)) {
            return false;
        }
        CLAIMS.put(straggler, new Claim(pet, owner, now + CLAIM_EXPIRY_TICKS, false));
        return true;
    }

    /** Refreshes a held claim's expiry and press state each tick the pet keeps working it. */
    static void refreshClaim(UUID straggler, UUID pet, UUID owner, long now, boolean pressing) {
        CLAIMS.put(straggler, new Claim(pet, owner, now + CLAIM_EXPIRY_TICKS, pressing));
    }

    /** Drops the straggler's claim (arrival, combat, drive end). */
    static void release(UUID straggler) {
        CLAIMS.remove(straggler);
    }

    /** Whether the straggler is being actively pressed — the signal a {@link FlockingTemptGoal} reads
     *  to hustle at {@link FlockingTemptGoal#PRESS_HUSTLE}. */
    static boolean isPressed(UUID straggler, long now) {
        Claim claim = CLAIMS.get(straggler);
        return claim != null && claim.expiryTick() > now && claim.pressing();
    }

    /** The number of live claims held by pets of the given owner, pruning expired entries as it goes. */
    static int activeClaimsForOwner(UUID owner, long now) {
        int count = 0;
        Iterator<Map.Entry<UUID, Claim>> it = CLAIMS.entrySet().iterator();
        while (it.hasNext()) {
            Claim claim = it.next().getValue();
            if (claim.expiryTick() <= now) {
                it.remove();
            } else if (claim.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    private static void releaseOwner(UUID owner) {
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    // ── Whistle round-up orders ───────────────────────────────────────────────────────────────────

    /**
     * Starts a whistle round-up: every animal in the drive group is ordered toward {@code orderer}
     * until {@code deadlineTicks} elapse. Replaces any round-up the same player already has running
     * (§6). The group animals then read as tempted toward the orderer, so §4's press machinery drives
     * them with no new goal.
     */
    public static void startRoundUp(List<? extends Animal> group, ServerPlayer orderer, long now, int deadlineTicks) {
        clearRoundUp(orderer.getUUID());
        RoundUp order = new RoundUp(orderer.getUUID(), now + deadlineTicks);
        for (Animal animal : group) {
            ROUND_UPS.put(animal, order);
        }
    }

    /** Drops every round-up order issued by the given player (a new order replaces a running one). */
    public static void clearRoundUp(UUID orderer) {
        ROUND_UPS.entrySet().removeIf(entry -> entry.getValue().orderer().equals(orderer));
    }

    /**
     * The player an animal is being rounded up toward, or {@code null} when it holds no live order.
     * The order alone doesn't guarantee the orderer is reachable — the sweep prunes expired and
     * unreachable orders each tick — but this read stays cheap for the tempt goal that calls it.
     */
    public static Player roundUpOrderer(PathfinderMob mob) {
        RoundUp order = ROUND_UPS.get(mob);
        if (order == null || mob.level().getGameTime() > order.deadlineTick()
                || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        Player orderer = level.getServer().getPlayerList().getPlayer(order.orderer());
        return orderer != null && orderer.level() == mob.level() ? orderer : null;
    }

    /** Prunes round-up orders each tick: arrived (within 5 blocks), expired, gone, or orphaned. */
    private static void sweepRoundUps(MinecraftServer server) {
        if (ROUND_UPS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Animal, RoundUp>> it = ROUND_UPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Animal, RoundUp> entry = it.next();
            Animal animal = entry.getKey();
            RoundUp order = entry.getValue();
            if (animal.isRemoved() || !animal.isAlive() || animal.level().getGameTime() > order.deadlineTick()) {
                it.remove();
                continue;
            }
            Player orderer = server.getPlayerList().getPlayer(order.orderer());
            if (orderer == null || orderer.level() != animal.level()
                    || animal.distanceToSqr(orderer) <= ROUND_UP_ARRIVAL_DISTANCE * ROUND_UP_ARRIVAL_DISTANCE) {
                it.remove();
            }
        }
    }

    /** Test hook: drops every claim. Mirrors the {@code SERVER_STOPPED} clear. */
    static void clearClaims() {
        CLAIMS.clear();
    }
}
