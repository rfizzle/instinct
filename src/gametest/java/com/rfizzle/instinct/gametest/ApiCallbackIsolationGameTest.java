package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.InstinctAnimalBredCallback;
import com.rfizzle.instinct.api.InstinctAnimalDownedCallback;
import com.rfizzle.instinct.api.InstinctAnimalRevivedCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * API-STANDARD §3.1/§6: each of Instinct's three callbacks isolates its listeners <em>inside</em> the
 * {@code createArrayBacked} invoker, so a listener that throws is caught, logged once, and skipped —
 * and every listener registered after it still gets its call.
 *
 * <p>These tests are the regression guard for the defect the isolation exists to prevent: a fire-site
 * wrap (or no wrap at all) lets one bad consumer deny the rest of the suite its events, or kill the
 * server tick outright. Each test registers a throwing listener followed by a sentinel and drives the
 * invoker directly, which exercises the invoker contract without needing to stage a real breeding.
 *
 * <p>Fabric events cannot be unregistered, so the throwing listeners stay registered for the rest of
 * the run. They are armed only for the duration of their own test — the {@code throwing} flag is
 * cleared in a {@code finally} — so they are inert everywhere else.
 */
public class ApiCallbackIsolationGameTest implements FabricGameTest {

    /** {@code Error}, not {@code Exception}: the shape a consumer compiled against an older signature throws. */
    private static AssertionError staleSignatureError() {
        return new AssertionError("simulated stale-signature listener failure");
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bredCallbackSkipsAThrowingListenerAndStillReachesTheNext(GameTestHelper helper) {
        AtomicBoolean throwing = new AtomicBoolean(true);
        AtomicBoolean reached = new AtomicBoolean(false);
        InstinctAnimalBredCallback.EVENT.register((a, b, child, grade) -> {
            if (throwing.get()) {
                throw staleSignatureError();
            }
        });
        InstinctAnimalBredCallback.EVENT.register((a, b, child, grade) -> reached.set(true));

        Cow parentA = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        Cow parentB = helper.spawn(EntityType.COW, new BlockPos(2, 2, 1));
        Cow child = helper.spawn(EntityType.COW, new BlockPos(3, 2, 1));
        try {
            InstinctAnimalBredCallback.EVENT.invoker()
                    .onAnimalBred(parentA, parentB, child, Grade.ORDINARY);
        } finally {
            throwing.set(false);
        }

        helper.assertTrue(reached.get(),
                "a listener registered after a throwing one must still receive InstinctAnimalBredCallback");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void downedCallbackSkipsAThrowingListenerAndStillReachesTheNext(GameTestHelper helper) {
        AtomicBoolean throwing = new AtomicBoolean(true);
        AtomicBoolean reached = new AtomicBoolean(false);
        InstinctAnimalDownedCallback.EVENT.register((animal, source) -> {
            if (throwing.get()) {
                throw staleSignatureError();
            }
        });
        InstinctAnimalDownedCallback.EVENT.register((animal, source) -> reached.set(true));

        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 2, 2));
        try {
            InstinctAnimalDownedCallback.EVENT.invoker()
                    .onAnimalDowned(wolf, wolf.damageSources().generic());
        } finally {
            throwing.set(false);
        }

        helper.assertTrue(reached.get(),
                "a listener registered after a throwing one must still receive InstinctAnimalDownedCallback");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void revivedCallbackSkipsAThrowingListenerAndStillReachesTheNext(GameTestHelper helper) {
        AtomicBoolean throwing = new AtomicBoolean(true);
        AtomicBoolean reached = new AtomicBoolean(false);
        InstinctAnimalRevivedCallback.EVENT.register((animal, reviver, item) -> {
            if (throwing.get()) {
                throw staleSignatureError();
            }
        });
        InstinctAnimalRevivedCallback.EVENT.register((animal, reviver, item) -> reached.set(true));

        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 2, 2));
        try {
            // The kennel-post shape: no reviver, no item — the path consumers must null-check.
            InstinctAnimalRevivedCallback.EVENT.invoker().onAnimalRevived(wolf, null, ItemStack.EMPTY);
        } finally {
            throwing.set(false);
        }

        helper.assertTrue(reached.get(),
                "a listener registered after a throwing one must still receive InstinctAnimalRevivedCallback");
        helper.succeed();
    }
}
