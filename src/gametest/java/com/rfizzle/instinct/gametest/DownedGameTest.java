package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.InstinctAnimalDownedCallback;
import com.rfizzle.instinct.api.InstinctAnimalRevivedCallback;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SPEC §7 Downed Pets & Revival: a lethal non-beyond-saving blow downs a pet (health 1.0,
 * invulnerable, AI stopped, untargetable, aggressors cleared) rather than killing it; fire/lava,
 * the void, and {@code /kill} kill for real even while downed; a revive item restores it at a
 * fraction of health, costing one veterancy rank; the state survives an NBT round trip; wrong-item
 * and empty-hand interactions are suppressed; the transitions fire the public callbacks and grant
 * Back from the Brink to the reviver. Each config-mutating test gets its own batch and restores in
 * {@code finally}.
 */
public class DownedGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void lethalBlowDownsWolfAndClearsAttackers(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 3));
        zombie.setTarget(wolf);

        downWithArrow(helper, wolf);

        helper.assertTrue(wolf.isAlive(), "a lethal arrow leaves the wolf alive");
        helper.assertTrue(wolf.getHealth() == 1.0F, "downed health is pinned to 1.0");
        helper.assertTrue(InstinctAPI.isDowned(wolf), "the wolf is downed");
        helper.assertTrue(wolf.isInvulnerable(), "a downed pet is invulnerable");
        helper.assertTrue(wolf.isNoAi(), "a downed pet's AI is stopped");
        helper.assertTrue(wolf.isOrderedToSit(), "a downed pet lies in the sitting pose");
        helper.assertFalse(wolf.canBeSeenAsEnemy(), "a downed pet is untargetable by hostiles");
        helper.assertTrue(zombie.getTarget() == null, "a mob targeting the pet retargets on down");

        wolf.discard();
        zombie.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void goldenAppleRevivesAtHalfHealthAndDropsOneRank(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        VeterancyHandler.setAccruedDays(wolf, 60.0); // rank 3 at default thresholds
        helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 3, "precondition: rank 3");
        downWithArrow(helper, wolf);

        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            InteractionResult result = revive(helper, reviver, wolf);

            helper.assertTrue(result == InteractionResult.CONSUME, "the revive consumes the interaction");
            helper.assertFalse(InstinctAPI.isDowned(wolf), "the wolf is no longer downed");
            helper.assertValueEqual(InstinctAPI.getVeterancyRank(wolf), 2, "revival costs exactly one rank");
            float expected = 0.5F * wolf.getMaxHealth();
            helper.assertTrue(Math.abs(wolf.getHealth() - expected) < 0.01F,
                    "revived to reviveHealthFraction × max health (post-demotion)");
            helper.assertTrue(wolf.hasEffect(MobEffects.REGENERATION), "revival grants Regeneration");
            helper.assertTrue(wolf.isInvulnerable(), "the post-revive invulnerability window is active");
            helper.assertTrue(wolf.isOrderedToSit(), "a revived pet stands in Stay");
            wolf.discard();
            helper.succeed();
        } finally {
            reviver.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void revivedPetDoesNotPersistInvulnerability(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        downWithArrow(helper, wolf);
        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            revive(helper, reviver, wolf);
            helper.assertTrue(wolf.isInvulnerable(), "precondition: inside the post-revive window");
            // Leaving the loaded set within the window (chunk unload, or shutdown, which clears the
            // window before the world save) must strip the transient invulnerability — otherwise a
            // revived, no-longer-downed pet would persist a permanent Invulnerable flag to disk.
            ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(wolf, helper.getLevel());
            helper.assertFalse(wolf.isInvulnerable(),
                    "a revived pet's transient invulnerability is cleared, never persisted");
            wolf.discard();
            helper.succeed();
        } finally {
            reviver.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void vetKitRevivesToo(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        downWithArrow(helper, wolf);
        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            reviver.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(com.rfizzle.instinct.registry.InstinctItems.VET_KIT));
            InteractionResult result = revive(helper, reviver, wolf);
            helper.assertTrue(result == InteractionResult.CONSUME, "the vet kit revives");
            helper.assertFalse(InstinctAPI.isDowned(wolf), "the wolf is revived");
            wolf.discard();
            helper.succeed();
        } finally {
            reviver.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void fireLavaVoidAndKillProduceRealDeaths(GameTestHelper helper) {
        // A healthy pet dies outright to lava (beyond saving) — never downs.
        Wolf lava = spawnTamedWolf(helper, new BlockPos(1, 2, 1));
        lava.hurt(helper.getLevel().damageSources().lava(), 1000.0F);
        helper.assertFalse(lava.isAlive(), "lava is beyond saving — a real death, no down");
        helper.assertFalse(InstinctAPI.isDowned(lava), "no downed attachment for a lava death");

        // /kill kills a healthy pet outright.
        Wolf killed = spawnTamedWolf(helper, new BlockPos(2, 2, 1));
        killed.kill();
        helper.assertFalse(killed.isAlive(), "/kill is beyond saving");

        // An already-downed pet still dies to /kill...
        Wolf downedThenKilled = spawnTamedWolf(helper, new BlockPos(3, 2, 1));
        downWithArrow(helper, downedThenKilled);
        helper.assertTrue(InstinctAPI.isDowned(downedThenKilled), "precondition: downed");
        // Clear the transient hurt-cooldown the downing blow left, as it would have elapsed by the
        // time a downed pet reaches /kill or the void; the beyond-saving verdict is what we test.
        downedThenKilled.invulnerableTime = 0;
        downedThenKilled.kill();
        helper.assertFalse(downedThenKilled.isAlive(), "/kill kills even a downed pet");

        // ...and to the void.
        Wolf downedThenVoid = spawnTamedWolf(helper, new BlockPos(4, 2, 1));
        downWithArrow(helper, downedThenVoid);
        downedThenVoid.invulnerableTime = 0;
        downedThenVoid.hurt(helper.getLevel().damageSources().fellOutOfWorld(), 1000.0F);
        helper.assertFalse(downedThenVoid.isAlive(), "the void kills even a downed pet");

        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void downedStateSurvivesNbtRoundTrip(GameTestHelper helper) {
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        downWithArrow(helper, wolf);

        CompoundTag saved = new CompoundTag();
        wolf.saveWithoutId(saved);
        wolf.discard();

        Wolf loaded = EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(loaded != null, "wolf entity should be created");
        loaded.load(saved);

        helper.assertTrue(InstinctAPI.isDowned(loaded), "downed flag survives save/load");
        helper.assertTrue(loaded.isInvulnerable(), "invulnerability survives save/load");
        helper.assertTrue(loaded.isNoAi(), "the AI-stop survives save/load");
        helper.assertTrue(loaded.isOrderedToSit(), "the sit pose survives save/load");
        loaded.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void interactionsAreSuppressedWhileDowned(GameTestHelper helper) {
        Wolf healthy = spawnTamedWolf(helper, new BlockPos(1, 2, 1));
        Wolf downed = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        downWithArrow(helper, downed);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            // A wrong item on a downed pet: no swing, no consume — the interaction is cancelled.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            helper.assertTrue(interact(player, helper, downed) == InteractionResult.FAIL,
                    "a wrong item on a downed pet is suppressed");

            // An empty hand (would toggle sit) is likewise suppressed.
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(interact(player, helper, downed) == InteractionResult.FAIL,
                    "an empty-hand interaction on a downed pet is suppressed");
            helper.assertTrue(InstinctAPI.isDowned(downed), "the pet stays downed through suppressed interactions");

            // A healthy pet is never intercepted — vanilla interactions pass through.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            helper.assertTrue(interact(player, helper, healthy) == InteractionResult.PASS,
                    "a healthy pet is not intercepted");
            healthy.discard();
            downed.discard();
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void transitionsFireThePublicCallbacks(GameTestHelper helper) {
        AtomicBoolean downedFired = new AtomicBoolean(false);
        AtomicBoolean revivedFired = new AtomicBoolean(false);
        Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
        AtomicBoolean revivedCarriedPlayerAndItem = new AtomicBoolean(false);
        // Match on UUID, not the entity: a registered listener can never be unregistered, so
        // capturing the entity would pin it and its level for the rest of the run.
        UUID wolfId = wolf.getUUID();
        InstinctAnimalDownedCallback downedListener = (animal, source) -> {
            if (animal.getUUID().equals(wolfId)) {
                downedFired.set(true);
            }
        };
        InstinctAnimalRevivedCallback revivedListener = (animal, reviver, item) -> {
            if (animal.getUUID().equals(wolfId)) {
                revivedFired.set(true);
                revivedCarriedPlayerAndItem.set(reviver != null && !item.isEmpty());
            }
        };
        InstinctAnimalDownedCallback.EVENT.register(downedListener);
        InstinctAnimalRevivedCallback.EVENT.register(revivedListener);

        downWithArrow(helper, wolf);
        helper.assertTrue(downedFired.get(), "InstinctAnimalDownedCallback fires on down");

        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            revive(helper, reviver, wolf);
            helper.assertTrue(revivedFired.get(), "InstinctAnimalRevivedCallback fires on revival");
            helper.assertTrue(revivedCarriedPlayerAndItem.get(),
                    "item revival carries the reviving player and the item used");
            wolf.discard();
            helper.succeed();
        } finally {
            reviver.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void revivingGrantsBackFromTheBrinkToTheReviverOnly(GameTestHelper helper) {
        ServerPlayer reviver = spawnListeningPlayer(helper);
        ServerPlayer bystander = spawnListeningPlayer(helper);
        try {
            Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
            downWithArrow(helper, wolf);
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            revive(helper, reviver, wolf);

            helper.assertTrue(isGranted(helper, reviver), "the reviver earns Back from the Brink");
            helper.assertFalse(isGranted(helper, bystander), "a bystander does not");
            wolf.discard();
            helper.succeed();
        } finally {
            reviver.discard();
            bystander.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctDownedDisabled")
    public void disabledConfigIsVanillaDeath(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableDownedState;
        try {
            InstinctConfig.get().enableDownedState = false;
            Wolf wolf = spawnTamedWolf(helper, new BlockPos(3, 2, 3));
            wolf.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
            helper.assertFalse(wolf.isAlive(), "with the feature off, a lethal blow kills vanilla-style");
            helper.assertFalse(InstinctAPI.isDowned(wolf), "no new downs occur while disabled");
            helper.succeed();
        } finally {
            InstinctConfig.get().enableDownedState = saved;
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    /** Applies a lethal arrow hit (not beyond saving), downing a healthy tamed pet. */
    private static void downWithArrow(GameTestHelper helper, Wolf wolf) {
        AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
        if (arrow == null) {
            throw new IllegalStateException("could not create an arrow");
        }
        wolf.hurt(helper.getLevel().damageSources().arrow(arrow, null), 1000.0F);
        arrow.discard();
    }

    private static InteractionResult revive(GameTestHelper helper, ServerPlayer reviver, Wolf wolf) {
        return interact(reviver, helper, wolf);
    }

    private static InteractionResult interact(ServerPlayer player, GameTestHelper helper, Wolf wolf) {
        return UseEntityCallback.EVENT.invoker()
                .interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, wolf, null);
    }

    private static ServerPlayer spawnListeningPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        return player;
    }

    private static boolean isGranted(GameTestHelper helper, ServerPlayer player) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(Instinct.id("back_from_the_brink"));
        helper.assertTrue(holder != null, "back_from_the_brink advancement is loaded (datagen output present)");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static Wolf spawnTamedWolf(GameTestHelper helper, BlockPos rel) {
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        if (wolf == null) {
            throw new IllegalStateException("could not create a wolf");
        }
        BlockPos abs = helper.absolutePos(rel);
        wolf.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        helper.getLevel().addFreshEntity(wolf);
        return wolf;
    }
}
