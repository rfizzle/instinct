package com.rfizzle.instinct.guard;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.GuardData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.whistle.WhistleRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * The guard-stance goal ({@code design/SPEC.md} §6). Idle unless its pet carries a {@code GuardData}
 * order (written by the whistle). While posted it does one of two things each scan: with a hostile
 * inside {@code guardRadiusBlocks} of the post it sets that hostile as the pet's target and hands the
 * fight to the pet's vanilla melee goal — exactly as the whistle attack order does — then yields the
 * move slot; with none it pins the pet to its anchor, pathing back only once the pet has drifted
 * past a small hold radius, so the pack "holds this ground" rather than trailing its owner.
 *
 * <p>Targets are hostile monsters only ({@link WhistleRules#isGuardTarget}) — never players, never
 * any animal — so a guard keeps a pen without ever turning on its own livestock or another player's
 * pets. Self-preservation always wins: the goal yields the move slot the instant the pet acquires any
 * target (vanilla combat takes over) and stands down while a creeper swells within the berth's
 * awareness or while the pet is on fire or in lava, freeing the slot for
 * {@link com.rfizzle.instinct.selfpreservation.CreeperBerthGoal} and the vanilla panic goal, both of
 * which share this goal's priority. Every gate is re-read live, so a config toggle, a new whistle
 * order, or a fresh tame takes effect at once.
 */
public class GuardGoal extends Goal {

    /** Priority 1 — it outranks {@code FollowOwnerGoal} (6) and the stroll goals, so a posted pet
     *  holds its ground instead of trailing its owner, and it preempts {@code SitWhenOrderedToGoal}
     *  (2) should a posted pet ever be sitting. It ties {@code CreeperBerthGoal} and
     *  {@code TamableAnimalPanicGoal} (both 1); equal priorities never preempt each other mid-run, so
     *  this goal yields to both by standing down. It never fights the vanilla melee goal for the move
     *  slot: the moment it assigns a target it stops running (a target means combat, and combat is
     *  vanilla's job), so melee (5) is free to pursue. */
    static final int PRIORITY = 1;

    private static final int SCAN_INTERVAL_TICKS = 10;
    /** Blocks past the berth distance in which a swelling creeper still makes the guard stand down. */
    private static final int CREEPER_AWARENESS_MARGIN = 3;
    /** How far a posted pet may drift from its anchor before it paths back. */
    private static final double HOLD_RADIUS = 2.0;
    private static final double RETURN_SPEED = 1.1;

    private final TamableAnimal pet;
    private int scanCooldown;

    public GuardGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return active();
    }

    @Override
    public boolean canContinueToUse() {
        return active();
    }

    /**
     * True while the pet should be actively holding its post: the whistle is enabled, the pet carries
     * a guard order and is a valid guardian, it has no combat target (a target hands the move slot to
     * vanilla melee), and no creeper is swelling nearby (self-preservation takes the slot instead).
     * A non-guarding pet fails the {@link #guardData()} null check first, so it pays only that.
     */
    private boolean active() {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableWhistle || guardData() == null || !eligible()) {
            return false;
        }
        if (pet.getTarget() != null) {
            return false;
        }
        return !standDownForCreeper(config);
    }

    @Override
    public void start() {
        scanCooldown = 0;
    }

    @Override
    public void tick() {
        GuardData data = guardData();
        if (data == null) {
            return;
        }
        BlockPos anchor = data.anchor();
        if (--scanCooldown > 0) {
            holdOrReturn(anchor);
            return;
        }
        scanCooldown = adjustedTickDelay(SCAN_INTERVAL_TICKS);
        LivingEntity hostile = nearestHostile(anchor, InstinctConfig.get().guardRadiusBlocks);
        if (hostile != null) {
            // Hand the fight to vanilla melee; active() then yields the move slot so the pet pursues.
            pet.setTarget(hostile);
            return;
        }
        holdOrReturn(anchor);
    }

    @Override
    public void stop() {
        pet.getNavigation().stop();
    }

    /** Path back to the post once the pet has drifted past the hold radius; otherwise stand still. */
    private void holdOrReturn(BlockPos anchor) {
        double cx = anchor.getX() + 0.5;
        double cy = anchor.getY();
        double cz = anchor.getZ() + 0.5;
        if (Guard.shouldReturnToPost(pet.distanceToSqr(cx, cy, cz), HOLD_RADIUS)) {
            pet.getNavigation().moveTo(cx, cy, cz, RETURN_SPEED);
        } else {
            pet.getNavigation().stop();
        }
    }

    private GuardData guardData() {
        return pet.getAttached(InstinctAttachments.GUARD);
    }

    /** A valid guardian: tamed, not downed, not fleeing a hazard, and a pets-set member. */
    private boolean eligible() {
        if (!pet.isTame() || InstinctAPI.isDowned(pet)) {
            return false;
        }
        if (pet.isOnFire() || pet.isInLava()) {
            return false;
        }
        return AnimalCoverage.membershipOf(pet).pet();
    }

    private boolean standDownForCreeper(InstinctConfig config) {
        if (!config.enableSelfPreservation) {
            return false;
        }
        double awareness = config.creeperBerthBlocks + CREEPER_AWARENESS_MARGIN;
        return !pet.level().getEntitiesOfClass(Creeper.class, pet.getBoundingBox().inflate(awareness),
                creeper -> creeper.isAlive() && creeper.getSwellDir() > 0).isEmpty();
    }

    /** The nearest live hostile monster within {@code radius} blocks of the post, or {@code null}. */
    private LivingEntity nearestHostile(BlockPos anchor, double radius) {
        double cx = anchor.getX() + 0.5;
        double cy = anchor.getY() + 0.5;
        double cz = anchor.getZ() + 0.5;
        double radiusSq = radius * radius;
        LivingEntity nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Mob mob : pet.level().getEntitiesOfClass(Mob.class, new AABB(anchor).inflate(radius),
                candidate -> WhistleRules.isGuardTarget(candidate instanceof Enemy, candidate.isAlive())
                        && candidate.distanceToSqr(cx, cy, cz) <= radiusSq)) {
            double distSq = pet.distanceToSqr(mob);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = mob;
            }
        }
        return nearest;
    }
}
