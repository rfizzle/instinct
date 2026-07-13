package com.rfizzle.instinct.kennel;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.data.HomeData;
import com.rfizzle.instinct.data.InstinctAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The go-home goal ({@code design/SPEC.md} §9). Idle unless its pet is <em>recalling</em> — a
 * transient order the whistle raises (a Stay press for a homed pet, or the assign-home gesture). While
 * recalling it walks the pet toward its {@link HomeData} post and, on arrival, sits it and ends the
 * recall; a recall that can't finish (post in another dimension, walled off, too far) gives up at a
 * deadline and sits the pet where it stands, so a recalled pet always settles rather than pathing
 * forever.
 *
 * <p>Cost is kept off the tick like the guard goal: the repath toward home runs only once per
 * {@link #SCAN_INTERVAL_TICKS}, never a per-tick A* search.
 */
public class HomeGoal extends Goal {

    /** Priority 1 — it outranks {@code FollowOwnerGoal} (6) so a recalled pet heads to its post rather
     *  than trailing its owner. It shares this priority with {@code GuardGoal}, but the two are mutually
     *  exclusive: any whistle order clears the other's state, so only one is ever engaged. */
    static final int PRIORITY = 1;

    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final double WALK_SPEED = 1.0;

    private final TamableAnimal pet;
    private int scanCooldown;

    public HomeGoal(TamableAnimal pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return active();
    }

    @Override
    public boolean canContinueToUse() {
        return active();
    }

    /** The cheap keep-alive gate: the pet is recalling and is a valid, non-downed walker. A pet that
     *  isn't recalling fails the map lookup first, so it pays only that. */
    private boolean active() {
        return KennelHandler.isRecalling(pet) && pet.isTame() && !InstinctAPI.isDowned(pet);
    }

    @Override
    public void start() {
        scanCooldown = 0;
    }

    @Override
    public void tick() {
        if (--scanCooldown > 0) {
            return;
        }
        scanCooldown = adjustedTickDelay(SCAN_INTERVAL_TICKS);
        HomeData home = pet.getAttached(InstinctAttachments.HOME);
        // Home vanished, moved dimensions, or the recall ran out of time: settle where the pet stands.
        if (home == null || !pet.level().dimension().equals(home.dimension())
                || pet.level().getGameTime() >= KennelHandler.recallDeadline(pet)) {
            settle();
            return;
        }
        BlockPos post = home.post();
        // The post was mined out from under the pet: once we can see the spot (its chunk is loaded) and
        // the post is gone, settle where we stand rather than trek to nothing. A far, unloaded post can't
        // be checked yet, so the pet heads that way and this check applies as its chunk comes into view.
        if (pet.level().isLoaded(post) && !KennelPosts.isPostAt(pet.level(), post)) {
            settle();
            return;
        }
        double cx = post.getX() + 0.5;
        double cy = post.getY();
        double cz = post.getZ() + 0.5;
        if (Kennel.arrivedHome(pet.distanceToSqr(cx, cy, cz), Kennel.ARRIVE_RADIUS)) {
            settle();
            return;
        }
        // Repath toward the post. A transient failure (navigation not yet warmed, a momentary block)
        // is retried on the next scan; a post that stays unreachable is given up at the recall deadline.
        pet.getNavigation().moveTo(cx, cy, cz, WALK_SPEED);
    }

    @Override
    public void stop() {
        pet.getNavigation().stop();
    }

    /** End the recall and sit the pet in place — the settled Stay state. */
    private void settle() {
        pet.getNavigation().stop();
        pet.setOrderedToSit(true);
        KennelHandler.stopRecall(pet);
    }
}
