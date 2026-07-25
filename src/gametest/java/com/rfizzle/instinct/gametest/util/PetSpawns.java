package com.rfizzle.instinct.gametest.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Introduces an entity into a gametest structure under the conditions the assertions about it
 * actually depend on. Every spawn here forces its chunk and asserts the result is enumerable;
 * suites compose their own variants on {@link #spawnAt} rather than reimplementing it, so a
 * hardening fix lands once and reaches all of them. {@code GameTestHelpersGameTest} guards this.
 *
 * <p>This is the near boundary: a raw {@code helper.spawn} stands its entity wherever the caller's
 * floor reaches, with no chunk forced. That is sound while the position sits inside the
 * structure's own force-loaded box, which is why a raw spawn is still correct there. A spawn
 * placed beyond that box, or one a test later enumerates rather than holding a reference to,
 * belongs here instead.
 *
 * <p>And this is the far one: enumerability is asserted for a spawn whose chunk is already
 * accessible — inside the structure, or in the neighbourhood the batch has promoted around it,
 * which covers every position a suite spawns at. A spawn into a chunk the server has never loaded
 * fails that assertion even though the spawn itself was fine. A chunk turns enumerable only once
 * it and its surrounding ring reach full status, and that promotion runs across tick boundaries —
 * around forty of them for a chunk generated from scratch. Neither forcing the chunk nor blocking
 * on a chunk load pulls it forward, so no synchronous helper can wait for it. A test that wants a
 * pet that far out passes its own post-load check to the five-argument {@link #spawnAt} — the
 * default check fails at that distance and discards the entity before the caller ever sees it —
 * and then waits for the census itself, through {@code helper.succeedWhen}. These helpers stay
 * synchronous so their hundred-odd call sites keep their straight-line shape rather than paying
 * for a promise none of them need.
 */
public final class PetSpawns {

    private PetSpawns() {
    }

    /**
     * Makes a spawn position one a locate census can see a pet on. Forcing the chunk is the
     * load-bearing step: a pet enters the level's entity lookup — the map a census iterates —
     * only once its chunk is accessible, and that promotion is queued on the server executor
     * rather than applied inline, so writing blocks nearby does not reliably bring it about. That
     * makes forcing insufficient on its own: it settles the bookkeeping in the same tick, while
     * the accessibility the lookup keys off arrives ticks later. For a chunk the batch has
     * already promoted the difference never shows, which is the case at every position a suite
     * spawns at; for one the server has never loaded it is the whole story. The framework releases
     * every forced chunk when the batch ends, so this needs no teardown. The pad is what keeps a
     * pet that outlives its spawn tick from falling.
     *
     * <p>The pad extends one block out on each horizontal axis, so callers spawn at a relative x
     * and z of at least 1. Below that it writes outside the structure, into the gap between grid
     * slots where no floor exists and the write is not the no-op it is over a built floor.
     */
    public static void prepareSpawn(GameTestHelper helper, BlockPos rel) {
        ChunkPos chunk = new ChunkPos(helper.absolutePos(rel));
        helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(rel.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
    }

    /**
     * Asserts the condition a census actually depends on, rather than a proxy for it. A failure
     * for a spawn far from the structure reports a chunk that has not become accessible yet, not
     * a spawn that went wrong — the message says so, because the two look identical from the
     * caller's side and only one of them is a bug worth chasing.
     */
    public static void assertEnumerable(GameTestHelper helper, Entity entity) {
        helper.assertTrue(helper.getLevel().getEntity(entity.getId()) != null,
                "a spawned pet is in the level's entity lookup, where a locate census finds it "
                        + "(far from the structure this reads as the chunk not being accessible "
                        + "yet rather than as a failed spawn — see PetSpawns' class javadoc)");
    }

    /** An entity introduced at {@code rel} with no state set beyond its position. */
    public static <T extends Entity> T spawnAt(GameTestHelper helper, EntityType<T> type, BlockPos rel) {
        return spawnAt(helper, type, rel, entity -> {
        });
    }

    /**
     * The spawn every other helper here builds on. {@code beforeLoad} runs after the entity is
     * positioned but before {@code addFreshEntity}, because that ordering is load-bearing rather
     * than stylistic: {@code ENTITY_LOAD} injects Instinct's goals off the entity's state at load,
     * so anything a test needs the injection to see — tamed, owned, sitting — has to be set inside
     * the hook. State set after the call is invisible to it.
     *
     * <p>A mob is marked persistence-required, matching {@code GameTestHelper#spawn}: without it
     * vanilla despawns a mob whose nearest player is beyond its category distance, which for a test
     * holding one across ticks reads as the entity vanishing for no reason.
     */
    public static <T extends Entity> T spawnAt(GameTestHelper helper, EntityType<T> type, BlockPos rel,
                                               Consumer<T> beforeLoad) {
        return spawnAt(helper, type, rel, beforeLoad, PetSpawns::assertEnumerable);
    }

    /**
     * The same spawn with its post-load check as a parameter. Suites take the four-argument form,
     * which passes {@link #assertEnumerable}; this one serves the two cases that check cannot.
     * {@code GameTestHelpersGameTest} substitutes a check that always fails, to reach the discard
     * path below deterministically — provoking the real one needs a chunk the server has never
     * loaded, and which chunks are still cold depends on what the rest of the batch touched first,
     * so that guard would go quiet on exactly the runs it is meant to catch. A test spawning past
     * the far boundary in the class javadoc substitutes a check that does not assert, since the
     * default one would discard the pet it means to keep.
     *
     * <p>{@code verify} signals failure by throwing a {@link RuntimeException} — that is what the
     * discard below catches, and a check throwing anything else leaks its entity into the batch.
     */
    public static <T extends Entity> T spawnAt(GameTestHelper helper, EntityType<T> type, BlockPos rel,
                                               Consumer<T> beforeLoad,
                                               BiConsumer<GameTestHelper, Entity> verify) {
        prepareSpawn(helper, rel);
        T entity = type.create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException("could not create " + type);
        }
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        BlockPos abs = helper.absolutePos(rel);
        entity.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        beforeLoad.accept(entity);
        helper.getLevel().addFreshEntity(entity);
        // Discard before rethrowing: the entity is already in the level but the caller never
        // receives it, so this is the only place that can still clean it up. A spawn outside every
        // structure box would otherwise tick on unreferenced for the rest of the run.
        try {
            verify.accept(helper, entity);
        } catch (RuntimeException e) {
            entity.discard();
            throw e;
        }
        return entity;
    }

    /**
     * The tamed-pet spawn. Taming inside the before-load hook is the production path for a pet
     * loading in, and is what makes {@code ENTITY_LOAD} inject the tamed-only goals (flocking
     * swap, berth, predator watch) — a pet tamed afterwards loads as a wild animal and never
     * receives them.
     */
    public static <T extends TamableAnimal> T spawnTamed(GameTestHelper helper, EntityType<T> type,
                                                        BlockPos rel, UUID owner) {
        return spawnAt(helper, type, rel, animal -> {
            animal.setTame(true, false);
            animal.setOwnerUUID(owner);
        });
    }

    /** A tamed wolf owned by an arbitrary player, for suites that never inspect the owner. */
    public static Wolf spawnTamedWolf(GameTestHelper helper, BlockPos rel) {
        return spawnTamedWolf(helper, rel, UUID.randomUUID());
    }

    public static Wolf spawnTamedWolf(GameTestHelper helper, BlockPos rel, UUID owner) {
        return spawnTamed(helper, EntityType.WOLF, rel, owner);
    }

    /** A tamed parrot — a pets-set animal with no attack-damage attribute, so never combat-capable. */
    public static Parrot spawnTamedParrot(GameTestHelper helper, BlockPos rel, UUID owner) {
        return spawnTamed(helper, EntityType.PARROT, rel, owner);
    }

    /**
     * A tamed horse. {@link Horse} is an {@code AbstractHorse}, not a {@link TamableAnimal}, and
     * tames through single-arg {@code setTamed}, so it cannot ride {@link #spawnTamed} — but it
     * needs the same chunk forcing and load ordering.
     */
    public static Horse spawnTamedHorse(GameTestHelper helper, BlockPos rel) {
        return spawnAt(helper, EntityType.HORSE, rel, horse -> {
            horse.setTamed(true);
            horse.setOwnerUUID(UUID.randomUUID());
        });
    }

    /**
     * A creeper that can fuse but not hurt the test: NoAI keeps it in place (the swell counter
     * runs in {@code Creeper#tick()}, not its AI), ExplosionRadius 0 makes any detonation
     * harmless, and a 400-tick fuse outlives the test timeout so the fuse can never end (and stop
     * the berth goal early) before the assertions are met. Tests must discard it.
     */
    public static Creeper spawnFuseOnlyCreeper(GameTestHelper helper, BlockPos rel) {
        Creeper creeper = spawnAt(helper, EntityType.CREEPER, rel);
        creeper.setNoAi(true);
        CompoundTag tag = creeper.saveWithoutId(new CompoundTag());
        tag.putByte("ExplosionRadius", (byte) 0);
        tag.putShort("Fuse", (short) 400);
        creeper.load(tag);
        return creeper;
    }
}
