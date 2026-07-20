package com.rfizzle.instinct.gametest.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Introduces an entity into a gametest structure under the conditions the assertions about it
 * actually depend on. Every spawn here forces its chunk and asserts the result is enumerable;
 * suites compose their own variants on {@link #spawnTamed} rather than reimplementing it, so a
 * hardening fix lands once and reaches all of them. {@code GameTestHelpersGameTest} guards this.
 */
public final class PetSpawns {

    private PetSpawns() {
    }

    /**
     * Makes a spawn position one a locate census can see a pet on. Forcing the chunk is the
     * load-bearing step: a pet enters the level's entity lookup — the map a census iterates —
     * only once its chunk is accessible, and that promotion is queued on the server executor
     * rather than applied inline, so writing blocks nearby does not reliably bring it about. The
     * framework releases every forced chunk when the batch ends, so this needs no teardown. The
     * pad is what keeps a pet that outlives its spawn tick from falling.
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

    /** Asserts the condition a census actually depends on, rather than a proxy for it. */
    public static void assertEnumerable(GameTestHelper helper, Entity entity) {
        helper.assertTrue(helper.getLevel().getEntity(entity.getId()) != null,
                "a spawned pet is in the level's entity lookup, where a locate census finds it");
    }

    /**
     * The tamed-pet spawn every suite builds on. Taming before {@code addFreshEntity} is the
     * production path and is load-bearing: {@code ENTITY_LOAD} injects the tamed-only goals
     * (flocking swap, berth, predator watch) off the entity's state at load, so a pet tamed
     * afterwards loads as a wild animal and never receives them.
     */
    public static <T extends TamableAnimal> T spawnTamed(GameTestHelper helper, EntityType<T> type,
                                                        BlockPos rel, UUID owner) {
        prepareSpawn(helper, rel);
        T animal = type.create(helper.getLevel());
        if (animal == null) {
            throw new IllegalStateException("could not create " + type);
        }
        BlockPos abs = helper.absolutePos(rel);
        animal.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        animal.setTame(true, false);
        animal.setOwnerUUID(owner);
        helper.getLevel().addFreshEntity(animal);
        assertEnumerable(helper, animal);
        return animal;
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
        prepareSpawn(helper, rel);
        Horse horse = EntityType.HORSE.create(helper.getLevel());
        if (horse == null) {
            throw new IllegalStateException("could not create a horse");
        }
        BlockPos abs = helper.absolutePos(rel);
        horse.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        horse.setTamed(true);
        horse.setOwnerUUID(UUID.randomUUID());
        helper.getLevel().addFreshEntity(horse);
        assertEnumerable(helper, horse);
        return horse;
    }

    /**
     * A creeper that can fuse but not hurt the test: NoAI keeps it in place (the swell counter
     * runs in {@code Creeper#tick()}, not its AI), ExplosionRadius 0 makes any detonation
     * harmless, and a 400-tick fuse outlives the test timeout so the fuse can never end (and stop
     * the berth goal early) before the assertions are met. Tests must discard it.
     */
    public static Creeper spawnFuseOnlyCreeper(GameTestHelper helper, BlockPos rel) {
        prepareSpawn(helper, rel);
        Creeper creeper = helper.spawn(EntityType.CREEPER, rel);
        creeper.setNoAi(true);
        CompoundTag tag = creeper.saveWithoutId(new CompoundTag());
        tag.putByte("ExplosionRadius", (byte) 0);
        tag.putShort("Fuse", (short) 400);
        creeper.load(tag);
        assertEnumerable(helper, creeper);
        return creeper;
    }
}
