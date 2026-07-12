package com.rfizzle.instinct.herding;

import com.rfizzle.instinct.config.InstinctConfig;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * The flocking tempt goal ({@code design/SPEC.md} §4): a {@link TemptGoal} replacement that, while
 * flocking is enabled, moves a player-tempted livestock animal at {@code flockSpeedMultiplier} its
 * base tempt speed, blends in a gentle separation preference to keep {@code flockSpacingBlocks} from
 * other flock members, and holds the flock together over a drive by tempting out to
 * {@link #FLOCK_RANGE} instead of vanilla's 10. Vanilla's own 2.5-block standoff (facing the player)
 * is inherited unchanged. A pressed straggler (§4 drive assist) hustles at {@link #PRESS_HUSTLE}.
 *
 * <p>With {@code enableFlocking} off — read live every tick — {@code canUse} and {@code tick}
 * delegate straight to the vanilla goal, so a mid-world toggle makes the animal behave exactly as
 * the vanilla tempt goal it replaced, with no reload. ({@code stop} always also arms this goal's own
 * calm-down alongside the vanilla one; the disabled path never reads it, so behavior is unchanged.)
 */
public class FlockingTemptGoal extends TemptGoal {

    /** Flock acquire/hold range while flocking (vanilla tempt is 10) — twice the 8-block straggler
     *  threshold, so a lagging animal stays tempted while a pet presses it back in. */
    static final double FLOCK_RANGE = 16.0;

    /** A pressed straggler moves toward the player at this fraction of its flock speed (§4). */
    static final double PRESS_HUSTLE = 1.2;

    /** Vanilla's own stop distance: {@code distanceToSqr < 6.25} is 2.5 blocks — the standoff. */
    private static final double STANDOFF_SQ = 6.25;

    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final int LOOK_YAW_MARGIN = 20;

    private final double baseSpeed;
    private final Predicate<ItemStack> foodPredicate;
    private TargetingConditions flockTargeting;
    private int flockCalmDown;
    private int repathCooldown;

    public FlockingTemptGoal(PathfinderMob mob, double speedModifier, Predicate<ItemStack> items, boolean canScare) {
        super(mob, speedModifier, items, canScare);
        this.baseSpeed = speedModifier;
        this.foodPredicate = items;
    }

    /** The player this animal is currently tempted toward while flocking, or {@code null} when idle. */
    public Player getTemptedPlayer() {
        return isRunning() ? this.player : null;
    }

    @Override
    public boolean canUse() {
        if (!InstinctConfig.get().enableFlocking) {
            return super.canUse();
        }
        if (this.flockCalmDown > 0) {
            this.flockCalmDown--;
            return false;
        }
        this.player = this.mob.level().getNearestPlayer(flockTargeting(), this.mob);
        return this.player != null;
    }

    @Override
    public void stop() {
        super.stop();
        this.flockCalmDown = reducedTickDelay(100);
    }

    @Override
    public void tick() {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableFlocking) {
            super.tick();
            return;
        }
        this.mob.getLookControl().setLookAt(this.player,
                (float) (this.mob.getMaxHeadYRot() + LOOK_YAW_MARGIN), (float) this.mob.getMaxHeadXRot());
        if (this.mob.distanceToSqr(this.player) < STANDOFF_SQ) {
            this.mob.getNavigation().stop();
            return;
        }
        if (--this.repathCooldown > 0) {
            return;
        }
        this.repathCooldown = REPATH_INTERVAL_TICKS;
        boolean pressed = Herding.isPressed(this.mob.getUUID(), this.mob.level().getGameTime());
        double speed = Herding.flockSpeed(this.baseSpeed, config.flockSpeedMultiplier, pressed);
        double[] separation = separation(config.flockSpacingBlocks);
        this.mob.getNavigation().moveTo(
                this.player.getX() + separation[0], this.player.getY(), this.player.getZ() + separation[1], speed);
    }

    private TargetingConditions flockTargeting() {
        if (this.flockTargeting == null) {
            this.flockTargeting = TargetingConditions.forNonCombat().range(FLOCK_RANGE).ignoreLineOfSight()
                    .selector(candidate -> this.foodPredicate.test(candidate.getMainHandItem())
                            || this.foodPredicate.test(candidate.getOffhandItem()));
        }
        return this.flockTargeting;
    }

    /**
     * A capped separation offset in the X/Z plane, summed from other members of this animal's flock
     * (same tempt target) within twice the spacing, each contribution weighted stronger the closer
     * the neighbor. The result is a gentle steering preference blended into the path — its magnitude
     * is capped at the spacing distance, so it never overrides the pull toward the player.
     */
    private double[] separation(double spacing) {
        double reach = spacing * 2.0;
        List<Animal> neighbors = this.mob.level().getEntitiesOfClass(Animal.class,
                this.mob.getBoundingBox().inflate(reach),
                other -> other != this.mob && Herding.temptTargetOf(other) == this.player);
        double sx = 0.0;
        double sz = 0.0;
        for (Animal neighbor : neighbors) {
            double dx = this.mob.getX() - neighbor.getX();
            double dz = this.mob.getZ() - neighbor.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.0 && distance < spacing) {
                double weight = (spacing - distance) / spacing;
                sx += (dx / distance) * weight;
                sz += (dz / distance) * weight;
            }
        }
        double magnitude = Math.sqrt(sx * sx + sz * sz);
        if (magnitude > spacing) {
            sx = sx / magnitude * spacing;
            sz = sz / magnitude * spacing;
        }
        return new double[]{sx, sz};
    }
}
