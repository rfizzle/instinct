package com.rfizzle.instinct.predatorwatch;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;

import java.util.EnumSet;
import java.util.List;

/**
 * The guardian watch goal ({@code design/SPEC.md} §8). Idle unless its pet is tamed, a pets-set
 * member, and <em>on Stay</em> (ordered to sit) with covered livestock and a wild predator both
 * inside {@code predatorWatchRadiusBlocks}. When one presses in, the goal stands the sitting pet
 * and, each repath, does two things at once: it <em>deters</em> every predator in radius — clearing
 * any covered-livestock attack target and driving it away from the pasture — and <em>intercepts</em>
 * the nearest one, pathing the pet to a blocking point between that predator and its nearest prey.
 * When no predator or no livestock remains in radius the pet re-sits where it stands.
 *
 * <p>Self-preservation always wins: the goal never engages while a creeper is swelling within the
 * berth's awareness radius, and it stands down the moment one appears mid-watch, freeing the move
 * slot for {@link com.rfizzle.instinct.selfpreservation.CreeperBerthGoal} (which shares this
 * priority). Every gate is re-read live, so a config toggle or a fresh tame takes effect at once.
 */
public class PredatorWatchGoal extends Goal {

    /** Priority 1 — above {@code SitWhenOrderedToGoal} (2 on wolf/cat/parrot) so a Stay pet stands
     *  to guard, and above {@code HerdWorkGoal} (5) / {@code FollowOwnerGoal} (6). It ties
     *  {@code CreeperBerthGoal} (1): rather than lean on priority to let the berth preempt (equal
     *  priorities never preempt each other mid-run), this goal yields to a swelling creeper itself —
     *  {@link #canUse()} never engages while one is near and {@link #tick()} drops the watch when one
     *  appears — so the berth always gets the slot within a scan of ignition, well inside the fuse. */
    static final int PRIORITY = 1;

    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int REPATH_INTERVAL_TICKS = 10;
    /** Blocks past the berth distance in which a swelling creeper still makes the guardian stand down. */
    private static final int CREEPER_AWARENESS_MARGIN = 3;
    /** How far along the predator→prey axis, on the prey side, the guardian plants to block. */
    private static final double INTERCEPT_STANDOFF = 1.5;
    private static final double INTERCEPT_SPEED = 1.3;
    /** How far from the guardian a deterred predator is driven — its "path away from the pasture". */
    private static final double FLEE_DISTANCE = 8.0;
    private static final double FLEE_SPEED = 1.2;

    private final TamableAnimal pet;
    private Animal threat;
    private boolean wasSitting;
    private int scanCooldown;
    private int repathCooldown;

    public PredatorWatchGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = adjustedTickDelay(SCAN_INTERVAL_TICKS);
        InstinctConfig config = InstinctConfig.get();
        if (!config.enablePredatorWatch || !eligible() || creeperThreatNear()) {
            return false;
        }
        double radius = config.predatorWatchRadiusBlocks;
        Animal predator = nearestPredatorInRadius(radius);
        // A predator with a pasture to guard: no livestock in range means no watch to keep, so a
        // wolf merely passing a lone stationed pet is never harassed.
        if (predator == null || nearestLivestock(predator, radius) == null) {
            return false;
        }
        this.threat = predator;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enablePredatorWatch || threat == null || !threat.isAlive() || !eligible()) {
            return false;
        }
        double radius = config.predatorWatchRadiusBlocks;
        return pet.distanceToSqr(threat) <= radius * radius;
    }

    @Override
    public void start() {
        // Stand a sitting pet to guard, restoring the Stay order at its new spot on stop — stay
        // means stay, minus the predator. wasSitting is always true here (canUse engages only a
        // sitting pet), but track it so the re-sit is unconditional on the recorded state.
        wasSitting = pet.isOrderedToSit();
        if (wasSitting) {
            pet.setOrderedToSit(false);
        }
        repathCooldown = 0;
    }

    @Override
    public void tick() {
        if (threat != null) {
            pet.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        }
        if (--repathCooldown > 0) {
            return;
        }
        repathCooldown = adjustedTickDelay(REPATH_INTERVAL_TICKS);
        // Self-preservation trumps the watch: a swelling creeper ends the guard so the berth (same
        // priority) can take the move slot.
        if (creeperThreatNear()) {
            threat = null;
            return;
        }
        double radius = InstinctConfig.get().predatorWatchRadiusBlocks;
        Animal nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Animal predator : predatorsInRadius(radius)) {
            deter(predator);
            double distSq = pet.distanceToSqr(predator);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = predator;
            }
        }
        if (nearest == null) {
            threat = null;
            return;
        }
        Animal prey = nearestLivestock(nearest, radius);
        if (prey == null) {
            threat = null;
            return;
        }
        threat = nearest;
        double[] block = PredatorWatch.interceptPoint(nearest.getX(), nearest.getZ(),
                prey.getX(), prey.getZ(), INTERCEPT_STANDOFF);
        pet.getNavigation().moveTo(block[0], nearest.getY(), block[1], INTERCEPT_SPEED);
    }

    @Override
    public void stop() {
        threat = null;
        pet.getNavigation().stop();
        if (wasSitting) {
            pet.setOrderedToSit(true);
        }
        wasSitting = false;
    }

    /** Drops a predator's hunt and drives it off: clears a covered-livestock target and paths it
     *  {@link #FLEE_DISTANCE} away from the guardian, re-issued each repath so it keeps its distance. */
    private void deter(Animal predator) {
        LivingEntity target = predator.getTarget();
        if (target instanceof Animal prey && AnimalCoverage.membershipOf(prey).livestock()) {
            predator.setTarget(null);
        }
        double[] away = PredatorWatch.fleePoint(predator.getX(), predator.getZ(),
                pet.getX(), pet.getZ(), FLEE_DISTANCE);
        predator.getNavigation().moveTo(away[0], predator.getY(), away[1], FLEE_SPEED);
    }

    /** True when the pet is a valid guardian: tamed, not downed, on Stay (or already standing to
     *  guard from a Stay), and a pets-set member. */
    private boolean eligible() {
        if (!pet.isTame() || InstinctAPI.isDowned(pet)) {
            return false;
        }
        if (!pet.isOrderedToSit() && !wasSitting) {
            return false;
        }
        return AnimalCoverage.membershipOf(pet).pet();
    }

    private boolean creeperThreatNear() {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableSelfPreservation) {
            return false;
        }
        double awareness = config.creeperBerthBlocks + CREEPER_AWARENESS_MARGIN;
        return !pet.level().getEntitiesOfClass(Creeper.class, pet.getBoundingBox().inflate(awareness),
                creeper -> creeper.isAlive() && creeper.getSwellDir() > 0).isEmpty();
    }

    private List<Animal> predatorsInRadius(double radius) {
        return pet.level().getEntitiesOfClass(Animal.class, pet.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && PredatorWatch.isPredator(candidate));
    }

    private Animal nearestPredatorInRadius(double radius) {
        Animal nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Animal predator : predatorsInRadius(radius)) {
            double distSq = pet.distanceToSqr(predator);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = predator;
            }
        }
        return nearest;
    }

    private Animal nearestLivestock(Animal predator, double radius) {
        Animal nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Animal animal : pet.level().getEntitiesOfClass(Animal.class, pet.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && AnimalCoverage.membershipOf(candidate).livestock())) {
            double distSq = predator.distanceToSqr(animal);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = animal;
            }
        }
        return nearest;
    }
}
