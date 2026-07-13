package com.rfizzle.instinct.selfpreservation;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.OwnedAnimals;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The creeper berth ({@code design/SPEC.md} §1 protection 2). A creeper whose fuse is actively
 * counting up — {@code getSwellDir() > 0}, the one signal shared by natural swelling,
 * flint-and-steel ignition, and command triggers — closer than {@code creeperBerthBlocks} sends
 * the animal away from the nearest such creeper at 1.4x speed until it is at least the berth
 * distance clear, then the animal resumes what it was doing: a sitting pet stands, steps clear,
 * and re-sits at its new position (a mount has no sit pose, so it simply resumes). While any
 * swelling creeper is inside the awareness radius (berth + 3), an animal targeting it breaks off;
 * the target is re-cleared on every scan, so the break-off holds for the fuse duration and vanilla
 * targeting may re-acquire once the fuse resets.
 *
 * <p>Covers both pets ({@link TamableAnimal}) and mounts ({@link net.minecraft.world.entity.animal.horse.AbstractHorse}).
 * Inert unless the animal is tamed and {@code enableSelfPreservation} is on — both re-read live,
 * so a fresh tame or a config toggle takes effect without a chunk reload — and inert while the
 * animal is being ridden, since a rider is in control (SPEC §1: mounts flee only while riderless).
 * Downed animals (SPEC §7) run with {@code setNoAi(true)}, which stops the goal selector entirely.
 */
public class CreeperBerthGoal extends Goal {

    /** Blocks past the berth distance in which fuses are still tracked for attack break-off. */
    private static final int AWARENESS_MARGIN_BLOCKS = 3;
    private static final double FLEE_SPEED_MODIFIER = 1.4;
    // Both intervals pass through adjustedTickDelay, which halves them while
    // requiresUpdateEveryTick() is false (the default) — effective cadence: scan every 3 game
    // ticks, repath every 5. Both are well inside a creeper's 30-tick fuse.
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final PathfinderMob mob;
    private Creeper threat;
    private boolean wasSitting;
    private int scanCooldown;
    private int repathCooldown;

    public CreeperBerthGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = adjustedTickDelay(SCAN_INTERVAL_TICKS);
        InstinctConfig config = InstinctConfig.get();
        // A ridden mount is steered by its rider (SPEC §1: flee only while riderless). A pet seated in
        // a boat is likewise not free to path away — its position is the boat's — so a boated pet holds
        // its seat rather than spinning a moveAway the boat would override every tick.
        if (!config.enableSelfPreservation || !OwnedAnimals.isTamed(mob)
                || mob.isVehicle() || mob.isPassenger()) {
            return false;
        }
        // Deliberate side effect in an engagement check: the attack break-off must fire for any
        // swelling creeper inside the awareness radius even when the animal is already berth-clear
        // and the goal never engages — canUse's scan is the only hook with that reach.
        Creeper nearest = scanAndBreakOffAttack(config.creeperBerthBlocks);
        if (nearest == null || mob.distanceToSqr(nearest) >= squared(config.creeperBerthBlocks)) {
            return false;
        }
        threat = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return InstinctConfig.get().enableSelfPreservation
                && !mob.isVehicle()
                && !mob.isPassenger()
                && threat != null
                && threat.isAlive()
                && threat.getSwellDir() > 0
                && mob.distanceToSqr(threat) < squared(InstinctConfig.get().creeperBerthBlocks);
    }

    @Override
    public void start() {
        wasSitting = mob instanceof TamableAnimal pet && pet.isOrderedToSit();
        if (wasSitting) {
            ((TamableAnimal) mob).setOrderedToSit(false);
        }
        moveAway();
        repathCooldown = adjustedTickDelay(REPATH_INTERVAL_TICKS);
    }

    @Override
    public void tick() {
        if (--repathCooldown > 0) {
            return;
        }
        repathCooldown = adjustedTickDelay(REPATH_INTERVAL_TICKS);
        // Re-scan while fleeing: a creeper igniting mid-flight becomes the new nearest threat,
        // and an animal re-targeting the fusing creeper is broken off again.
        Creeper nearest = scanAndBreakOffAttack(InstinctConfig.get().creeperBerthBlocks);
        if (nearest != null) {
            threat = nearest;
        }
        if (threat != null && (nearest != null || mob.getNavigation().isDone())) {
            moveAway();
        }
    }

    @Override
    public void stop() {
        threat = null;
        mob.getNavigation().stop();
        if (wasSitting && mob instanceof TamableAnimal pet) {
            // Stay means stay — minus the blast radius: the sit order is restored at the new
            // position and SitWhenOrderedToGoal re-seats the pet.
            pet.setOrderedToSit(true);
        }
        wasSitting = false;
    }

    /**
     * Finds the nearest swelling creeper inside the awareness radius (berth + margin) and clears
     * the pet's attack target when that target is one of them. Runs on the scan/repath cadence,
     * never every tick.
     */
    private Creeper scanAndBreakOffAttack(int berthBlocks) {
        double awareness = berthBlocks + AWARENESS_MARGIN_BLOCKS;
        List<Creeper> swelling = mob.level().getEntitiesOfClass(Creeper.class,
                mob.getBoundingBox().inflate(awareness),
                creeper -> creeper.isAlive() && creeper.getSwellDir() > 0);
        if (swelling.isEmpty()) {
            return null;
        }
        if (mob.getTarget() instanceof Creeper target && swelling.contains(target)) {
            mob.setTarget(null);
        }
        Creeper nearest = null;
        double nearestDistSqr = Double.MAX_VALUE;
        for (Creeper creeper : swelling) {
            double distSqr = mob.distanceToSqr(creeper);
            if (distSqr < nearestDistSqr) {
                nearestDistSqr = distSqr;
                nearest = creeper;
            }
        }
        return nearest;
    }

    private void moveAway() {
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, threat.position());
        if (away != null) {
            mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED_MODIFIER);
        }
    }

    private static double squared(int blocks) {
        return (double) blocks * blocks;
    }
}
