package com.rfizzle.instinct.boating;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * The boat-boarding goal ({@code design/SPEC.md} §4, "Water crossings"). Idle unless the pet's owner
 * is riding a boat with a spare seat. When one is, and this is the single nearest eligible following
 * pet ({@link Boating#chooseBoarder}), it swims/walks to the boat and boards with an <em>unforced</em>
 * {@link TamableAnimal#startRiding(net.minecraft.world.entity.Entity)} — unforced so the vanilla two-
 * seat cap arbitrates any race and the boat can never be overfilled. Once aboard the goal keeps
 * running, holding the {@code MOVE} flag so no sibling goal fights {@code Boat.positionRider}, and its
 * {@link #stop()} hops the pet back out the moment the owner leaves the boat.
 *
 * <p>Approach paths across water via the same water-malus zeroing {@code FlockingTemptGoal} and
 * vanilla {@code FollowOwnerGoal} use, restored in {@code stop()}. Every gate is re-read live, so the
 * {@code enablePetBoating} toggle takes effect without a reload.
 */
public class BoardBoatGoal extends Goal {

    /** A pet within this range of the owner's boat will move to board it. */
    private static final double BOARD_RADIUS = 8.0;
    /** Close enough to be seated. A vanilla boat is ~1.4 wide, so 2 blocks seats cleanly. */
    private static final double MOUNT_DISTANCE = 2.0;
    /** While approaching, a boat that rows beyond this is given up on — normal following (which also
     *  swims) resumes the chase. */
    private static final double GIVE_UP_RADIUS = 16.0;
    private static final double BOARD_SPEED = 1.1;
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int REPATH_INTERVAL_TICKS = 10;

    private final TamableAnimal pet;
    private Boat targetBoat;
    private boolean boarded;
    private int scanCooldown;
    private int repathCooldown;
    private float savedWaterMalus;

    public BoardBoatGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--scanCooldown > 0) {
            return false;
        }
        scanCooldown = adjustedTickDelay(SCAN_INTERVAL_TICKS);
        // Cheap self-gates first, before any world scan or membership resolve. A pet with a combat
        // target stands down so boarding never blocks self-defense: this goal ties LeapAtTargetGoal
        // (priority 4) and outranks MeleeAttackGoal (5) while holding MOVE, so the engine cannot
        // preempt it for combat — the stand-down does, exactly as HerdWorkGoal handles the same tie.
        if (!pet.isTame() || pet.isOrderedToSit() || InstinctAPI.isDowned(pet) || pet.getTarget() != null) {
            return false;
        }
        if (!(pet.getOwner() instanceof Player owner) || owner.level() != pet.level()) {
            return false;
        }
        // Adopt a pet already seated in its owner's boat — e.g. after a reload mid-voyage, where the
        // freshly added goal sees a passenger and would otherwise never run — so the disembark logic
        // still fires when the owner lands, instead of leaving the pet stuck aboard until the boat
        // breaks. start() reads the live ride state, so it resumes in the boarded phase.
        if (pet.isPassenger()) {
            if (InstinctConfig.get().enablePetBoating && pet.getVehicle() instanceof Boat seated
                    && owner.getVehicle() == seated) {
                this.targetBoat = seated;
                return true;
            }
            return false;
        }
        boolean ownerInBoat = owner.getVehicle() instanceof Boat;
        Boat boat = ownerInBoat ? (Boat) owner.getVehicle() : null;
        boolean seatFree = boat != null && boat.getPassengers().size() < boat.getMaxPassengers();
        if (!Boating.eligibleToBoard(InstinctConfig.get().enablePetBoating, pet.isTame(),
                !pet.isOrderedToSit(), InstinctAPI.isDowned(pet), pet.isPassenger(),
                ownerInBoat, seatFree)) {
            return false;
        }
        // Membership and the sibling scan are the costly checks; they run only once every cheaper
        // gate has passed (owner boating, a seat open), which is rare.
        if (!AnimalCoverage.isPet(pet)
                || pet.distanceToSqr(boat) > BOARD_RADIUS * BOARD_RADIUS
                || !winsSeat(owner, boat)) {
            return false;
        }
        this.targetBoat = boat;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!InstinctConfig.get().enablePetBoating || targetBoat == null
                || InstinctAPI.isDowned(pet) || pet.getTarget() != null) {
            return false;
        }
        if (!(pet.getOwner() instanceof Player owner) || owner.level() != pet.level()) {
            return false;
        }
        if (boarded) {
            // Seated: hold the seat (and the MOVE flag) until the owner leaves this boat; stop() then
            // disembarks the pet.
            return pet.getVehicle() == targetBoat && owner.getVehicle() == targetBoat;
        }
        // Still approaching: the owner must stay in this boat with a seat open, a stay order must not
        // have landed, and the boat must not have rowed out of reach.
        if (owner.getVehicle() != targetBoat || pet.isOrderedToSit()
                || targetBoat.getPassengers().size() >= targetBoat.getMaxPassengers()) {
            return false;
        }
        return pet.distanceToSqr(targetBoat) <= GIVE_UP_RADIUS * GIVE_UP_RADIUS;
    }

    @Override
    public void start() {
        // Zero the water malus for the approach so the pet swims to a boat on open water instead of
        // stalling at the bank; restored in stop().
        savedWaterMalus = pet.getPathfindingMalus(PathType.WATER);
        pet.setPathfindingMalus(PathType.WATER, 0.0F);
        // Usually false (approaching); true when canUse adopted a pet already seated after a reload.
        boarded = pet.getVehicle() == targetBoat;
        repathCooldown = 0;
    }

    @Override
    public void tick() {
        if (boarded || targetBoat == null) {
            return;
        }
        pet.getLookControl().setLookAt(targetBoat, 30.0F, 30.0F);
        if (pet.distanceToSqr(targetBoat) <= MOUNT_DISTANCE * MOUNT_DISTANCE) {
            // Unforced: the vanilla seat cap fails this cleanly if another pet took the seat first.
            if (pet.startRiding(targetBoat)) {
                boarded = true;
                pet.getNavigation().stop();
            }
            return;
        }
        if (--repathCooldown <= 0) {
            repathCooldown = REPATH_INTERVAL_TICKS;
            pet.getNavigation().moveTo(targetBoat, BOARD_SPEED);
        }
    }

    @Override
    public void stop() {
        pet.setPathfindingMalus(PathType.WATER, savedWaterMalus);
        if (pet.getVehicle() == targetBoat) {
            pet.stopRiding();
        }
        pet.getNavigation().stop();
        targetBoat = null;
        boarded = false;
    }

    /**
     * Whether this pet is the one that should walk up to the boat's single spare seat: the nearest
     * eligible following pet of the same owner within {@link #BOARD_RADIUS}, ties broken by UUID. The
     * seat cap still arbitrates the actual boarding, but this keeps the rest of the pack from
     * stampeding the boat.
     */
    private boolean winsSeat(Player owner, Boat boat) {
        Map<java.util.UUID, Double> candidates = new HashMap<>();
        double reachSq = BOARD_RADIUS * BOARD_RADIUS;
        for (TamableAnimal other : pet.level().getEntitiesOfClass(TamableAnimal.class,
                boat.getBoundingBox().inflate(BOARD_RADIUS),
                candidate -> candidate.isTame() && !candidate.isOrderedToSit()
                        && !candidate.isPassenger() && candidate.getOwner() == owner
                        && !InstinctAPI.isDowned(candidate)
                        && AnimalCoverage.isPet(candidate))) {
            double distSq = other.distanceToSqr(boat);
            if (distSq <= reachSq) {
                candidates.put(other.getUUID(), distSq);
            }
        }
        return pet.getUUID().equals(Boating.chooseBoarder(candidates));
    }
}
