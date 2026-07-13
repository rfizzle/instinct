package com.rfizzle.instinct.herding;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.selfpreservation.CreeperBerthGoal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The pet drive-assist goal ({@code design/SPEC.md} §4). Idle unless the pet's owner is running a
 * <em>drive</em> — tempting at least {@link #DRIVE_MIN_ANIMALS} covered animals. When one is, and
 * this is an eligible working pet (tamed, owned by the driver within {@link #OWNER_REACH} blocks,
 * following rather than sitting, not downed, not in combat, not fleeing a creeper), it claims the
 * nearest unclaimed <em>straggler</em> (a flock member more than {@link #STRAGGLER_THRESHOLD} blocks
 * out), paths to the point 2 blocks behind it on the straggler→player axis, and holds there. While
 * the pet is within {@link #IN_POSITION} blocks of that point the straggler is marked pressed, so it
 * hustles toward the player. The pet presses; it never attacks.
 *
 * <p>Every gate is re-read live: {@code enableFlocking}/{@code enableHerding} toggles, coverage
 * edits, and {@code herdingMaxPets} all take effect without a reload. The per-owner working-pet cap
 * is enforced through the shared claim map, so at most {@code herdingMaxPets} of a driver's pets ever
 * work at once.
 */
public class HerdWorkGoal extends Goal {

    private static final int DRIVE_MIN_ANIMALS = 3;
    private static final double STRAGGLER_THRESHOLD = 8.0;
    private static final double OWNER_REACH = 12.0;
    private static final double BEHIND_DISTANCE = 2.0;
    private static final double IN_POSITION = 3.0;
    private static final double PRESS_END_DISTANCE = 5.0;
    private static final double PET_WORK_SPEED = 1.3;
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final TamableAnimal pet;
    private Animal straggler;
    private Player driver;
    private int scanCooldown;
    private int repathCooldown;
    private double behindX;
    private double behindZ;
    private float savedWaterMalus;

    public HerdWorkGoal(TamableAnimal pet) {
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
        if (!config.enableFlocking || !config.enableHerding || !eligible()) {
            return false;
        }
        Player owner = (Player) pet.getOwner();
        long now = pet.level().getGameTime();
        // The per-owner cap first: a pet whose driver already has its full complement of workers
        // stands down before paying for the flock scan.
        if (Herding.activeClaimsForOwner(owner.getUUID(), now) >= config.herdingMaxPets) {
            return false;
        }
        List<Animal> flock = flockOf(owner);
        if (flock.size() < DRIVE_MIN_ANIMALS) {
            return false;
        }
        List<Animal> stragglers = Herding.stragglersOf(flock, Animal::getX, Animal::getZ,
                owner.getX(), owner.getZ(), STRAGGLER_THRESHOLD);
        if (stragglers.isEmpty()) {
            return false;
        }
        Animal target = nearestUnclaimed(stragglers, now);
        if (target == null || !Herding.tryClaim(target.getUUID(), pet.getUUID(), owner.getUUID(), now)) {
            return false;
        }
        this.straggler = target;
        this.driver = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableFlocking || !config.enableHerding
                || straggler == null || driver == null || !straggler.isAlive() || !eligible()) {
            return false;
        }
        if (pet.getOwner() != driver) {
            return false;
        }
        long now = pet.level().getGameTime();
        // The claim is ours until it expires; the straggler must still be this driver's flock member,
        // and the press ends the moment it is within 5 blocks of the driver.
        return Herding.isClaimed(straggler.getUUID(), now)
                && Herding.temptTargetOf(straggler) == driver
                && straggler.distanceToSqr(driver) > PRESS_END_DISTANCE * PRESS_END_DISTANCE;
    }

    @Override
    public void start() {
        // Zero the water malus for the goal's lifetime so a working pet swims a straggler back across a
        // river instead of stalling at the bank — the same trick vanilla FollowOwnerGoal uses, and the
        // same one FlockingTemptGoal applies to the flock it presses. canUse() already gated on the
        // flocking/herding toggles, so no re-check is needed here; stop() always restores.
        savedWaterMalus = pet.getPathfindingMalus(PathType.WATER);
        pet.setPathfindingMalus(PathType.WATER, 0.0F);
        recomputeBehindPoint();
        repathCooldown = 0;
    }

    @Override
    public void tick() {
        if (straggler == null || driver == null) {
            return;
        }
        pet.getLookControl().setLookAt(straggler, 30.0F, 30.0F);
        if (--repathCooldown <= 0) {
            repathCooldown = REPATH_INTERVAL_TICKS;
            recomputeBehindPoint();
            pet.getNavigation().moveTo(behindX, straggler.getY(), behindZ, PET_WORK_SPEED);
        }
        boolean inPosition = distanceToBehindSq() <= IN_POSITION * IN_POSITION;
        Herding.refreshClaim(straggler.getUUID(), pet.getUUID(), driver.getUUID(),
                pet.level().getGameTime(), inPosition);
    }

    @Override
    public void stop() {
        if (straggler != null) {
            Herding.release(straggler.getUUID());
        }
        straggler = null;
        driver = null;
        pet.setPathfindingMalus(PathType.WATER, savedWaterMalus);
        pet.getNavigation().stop();
    }

    /** True when this pet is a valid worker: tamed, following an online owner within reach in the
     *  same level, not seated in a boat, not downed, not in combat, and not fleeing a creeper (§1
     *  wins over the drive). */
    private boolean eligible() {
        if (!pet.isTame() || pet.isOrderedToSit() || pet.isPassenger()
                || InstinctAPI.isDowned(pet) || pet.getTarget() != null) {
            return false;
        }
        if (!AnimalCoverage.membershipOf(pet).pet() || fleeingCreeper()) {
            return false;
        }
        return pet.getOwner() instanceof Player owner
                && owner.level() == pet.level()
                && pet.distanceToSqr(owner) <= OWNER_REACH * OWNER_REACH;
    }

    private boolean fleeingCreeper() {
        for (WrappedGoal wrapped : pet.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof CreeperBerthGoal && wrapped.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /** This driver's flock: covered livestock within the flock range whose tempt target is the driver. */
    private List<Animal> flockOf(Player owner) {
        AABB box = owner.getBoundingBox().inflate(FlockingTemptGoal.FLOCK_RANGE);
        List<Animal> flock = new ArrayList<>();
        for (Animal animal : pet.level().getEntitiesOfClass(Animal.class, box,
                candidate -> AnimalCoverage.membershipOf(candidate).livestock())) {
            if (Herding.temptTargetOf(animal) == owner) {
                flock.add(animal);
            }
        }
        return flock;
    }

    private Animal nearestUnclaimed(List<Animal> stragglers, long now) {
        Animal nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Animal candidate : stragglers) {
            if (Herding.isClaimed(candidate.getUUID(), now)) {
                continue;
            }
            double distSq = pet.distanceToSqr(candidate);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private void recomputeBehindPoint() {
        double[] point = Herding.pressPoint(straggler.getX(), straggler.getZ(),
                driver.getX(), driver.getZ(), BEHIND_DISTANCE);
        behindX = point[0];
        behindZ = point[1];
    }

    private double distanceToBehindSq() {
        double dx = pet.getX() - behindX;
        double dz = pet.getZ() - behindZ;
        return dx * dx + dz * dz;
    }
}
