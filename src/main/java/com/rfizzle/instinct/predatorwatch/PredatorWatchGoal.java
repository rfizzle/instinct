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
 * When no predator or no livestock remains in radius the pet re-sits where it stands — on its own,
 * because the Stay order still holds; the goal never touches that order itself.
 *
 * <p>Self-preservation always wins. The goal never engages while a creeper is swelling within the
 * berth's awareness radius and drops the watch the moment one appears, freeing the move slot for
 * {@link com.rfizzle.instinct.selfpreservation.CreeperBerthGoal}; and it stands down while the pet
 * is on fire or in lava so the pet's vanilla panic goal can flee. Both {@code CreeperBerthGoal} and
 * the panic goal share this goal's priority, so it yields to them by standing down rather than by
 * priority. Every gate is re-read live, so a config toggle or a fresh tame takes effect at once.
 */
public class PredatorWatchGoal extends Goal {

    /** Priority 1 — it preempts {@code SitWhenOrderedToGoal} (2 on wolf/cat/parrot), so a Stay pet
     *  stands to guard <em>without this goal ever mutating the sit order</em>: the order stays the
     *  player's, so a whistle to Follow ends the watch at once and the pet is never re-sat against a
     *  command. It also outranks {@code HerdWorkGoal} (5) / {@code FollowOwnerGoal} (6). It ties
     *  {@code CreeperBerthGoal} and {@code TamableAnimalPanicGoal} (both 1); equal priorities never
     *  preempt each other mid-run, so this goal yields to both itself — never engaging while a
     *  creeper is swelling ({@link #canUse}), dropping the watch when one appears ({@link #tick}),
     *  and standing down while on fire or in lava ({@link #eligible}) — keeping self-preservation
     *  ahead of the watch within a scan. */
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
        if (!config.enablePredatorWatch || !eligible()) {
            return false;
        }
        double radius = config.predatorWatchRadiusBlocks;
        Animal predator = nearestPredatorInRadius(radius);
        // A predator with a pasture to guard: no livestock in range means no watch to keep, so a
        // wolf merely passing a lone stationed pet is never harassed.
        if (predator == null || nearestLivestock(predator, radius) == null) {
            return false;
        }
        // Pay the creeper scan only once there's actually a pasture to guard: an idle stationed pet
        // with no predator near never runs it, and self-preservation still wins here (the berth
        // takes the slot) and mid-watch (tick drops the threat).
        if (creeperThreatNear()) {
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
        // The pet stands purely by this goal preempting SitWhenOrderedToGoal (that goal clears the
        // sitting pose on stop); the sit order itself is left untouched, so the pet re-sits on its
        // own when the watch ends — and a whistle to Follow ends the watch instead of being ignored.
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
        // No sit restoration: the sit order was never cleared, so SitWhenOrderedToGoal re-seats the
        // pet on its own if the pet is still on Stay — and leaves it standing if the player commanded
        // Follow while it guarded.
    }

    /**
     * Drops a predator's hunt and drives it off: clears a covered-livestock target and paths it
     * {@link #FLEE_DISTANCE} away from the guardian, re-issued each repath so it keeps its distance.
     * A goal-driven predator (fox, wolf) obeys the navigation override; a brain-driven {@code Animal}
     * added via {@code predatorsInclude} would fight its own brain and be deterred only intermittently
     * — graceful degradation per Animal Coverage, never a crash. The shipped default set is goal-driven.
     */
    private void deter(Animal predator) {
        LivingEntity target = predator.getTarget();
        if (target instanceof Animal prey && AnimalCoverage.isLivestock(prey)) {
            predator.setTarget(null);
        }
        double[] away = PredatorWatch.fleePoint(predator.getX(), predator.getZ(),
                pet.getX(), pet.getZ(), FLEE_DISTANCE);
        predator.getNavigation().moveTo(away[0], predator.getY(), away[1], FLEE_SPEED);
    }

    /** True when the pet is a valid guardian: tamed, not downed, not fleeing a hazard, on Stay, and a
     *  pets-set member. The Stay check reads the live sit order (never a cached flag), so a whistle to
     *  Follow ends the watch at once; the fire/lava check stands the guard down so the pet's vanilla
     *  panic goal — which ties this goal's priority and so cannot preempt it — can take over and flee. */
    private boolean eligible() {
        if (!pet.isTame() || InstinctAPI.isDowned(pet)) {
            return false;
        }
        if (pet.isOnFire() || pet.isInLava()) {
            return false;
        }
        if (!pet.isOrderedToSit()) {
            return false;
        }
        return AnimalCoverage.isPet(pet);
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
                candidate -> candidate.isAlive() && AnimalCoverage.isLivestock(candidate))) {
            double distSq = predator.distanceToSqr(animal);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = animal;
            }
        }
        return nearest;
    }
}
