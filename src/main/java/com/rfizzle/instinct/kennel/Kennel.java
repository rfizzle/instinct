package com.rfizzle.instinct.kennel;

/**
 * Pure kennel-post geometry and pacing ({@code design/SPEC.md} §9). Game-instance-free so it
 * unit-tests without a world — the {@link KennelHandler} shell and the downed recovery sweep resolve
 * the game state (positions, block, config) and pass plain numbers here.
 */
public final class Kennel {

    /** Ticks per second — recovery time is configured in seconds, tracked in ticks. */
    static final int TICKS_PER_SECOND = 20;

    /** How close a recalling pet must get to its post before it counts as home and settles. */
    public static final double ARRIVE_RADIUS = 2.0;

    private Kennel() {
    }

    /** Whether a downed pet is close enough to a kennel post to recover beside it. Both sides squared. */
    public static boolean withinRecoveryRadius(double distanceToPostSq, int radiusBlocks) {
        return distanceToPostSq <= (double) radiusBlocks * radiusBlocks;
    }

    /** Whether a recalling pet has reached its home post (and should stop and sit). Both sides squared. */
    public static boolean arrivedHome(double distanceToHomeSq, double arriveRadius) {
        return distanceToHomeSq <= arriveRadius * arriveRadius;
    }

    /** Whether accumulated adjacent-to-post ticks have reached the recovery threshold. */
    public static boolean recoveryComplete(int accumulatedTicks, int thresholdTicks) {
        return accumulatedTicks >= thresholdTicks;
    }

    /** The recovery threshold in ticks for a configured number of seconds. */
    public static int recoveryThresholdTicks(int seconds) {
        return seconds * TICKS_PER_SECOND;
    }
}
