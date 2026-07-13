package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.downed.CarryHandler;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/**
 * SPEC §7 Carry: a downed small pet — a cat, parrot, or pup — is scooped up by sneak + empty-hand
 * use and becomes a passenger of the rescuer, who is slowed by a transient movement modifier while
 * carrying. A full-size downed pet (an adult wolf) is not carryable and stays where it falls. Sneak
 * + empty hand on a block sets the pet down again (still downed); reviving a carried pet releases it
 * and clears the carrier's slowdown; the feature is inert when disabled.
 */
public class CarryGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void sneakUseScoopsUpADownedCatAndSlowsTheCarrier(GameTestHelper helper) {
        Cat cat = spawnTamed(helper, EntityType.CAT, new BlockPos(3, 2, 3));
        down(helper, cat);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            InteractionResult result = sneakUse(player, helper, cat);

            helper.assertTrue(result == InteractionResult.CONSUME, "the pick-up consumes the interaction");
            helper.assertTrue(cat.getVehicle() == player, "the downed cat now rides the rescuer");
            helper.assertTrue(player.getFirstPassenger() == cat, "the rescuer carries the cat");
            helper.assertTrue(InstinctAPI.isDowned(cat), "the carried cat is still downed");

            AttributeModifier mod = carryModifier(player);
            helper.assertTrue(mod != null, "the carrier gains the slowdown modifier");
            helper.assertTrue(Math.abs(mod.amount() + InstinctConfig.get().carrySlowdownFraction) < 1e-6,
                    "the slowdown is the negative of carrySlowdownFraction");
            helper.assertTrue(isTransient(player),
                    "the slowdown is transient — never a permanent, disk-persisted modifier");
            helper.succeed();
        } finally {
            player.discard();
            cat.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aDownedPupIsCarryableBySize(GameTestHelper helper) {
        Wolf pup = spawnTamed(helper, EntityType.WOLF, new BlockPos(3, 2, 3));
        pup.setBaby(true);
        down(helper, pup);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            InteractionResult result = sneakUse(player, helper, pup);
            helper.assertTrue(result == InteractionResult.CONSUME, "a downed pup is scooped up");
            helper.assertTrue(pup.getVehicle() == player, "the pup rides the rescuer");
            helper.succeed();
        } finally {
            player.discard();
            pup.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aFullSizeDownedWolfIsNotCarryable(GameTestHelper helper) {
        Wolf wolf = spawnTamed(helper, EntityType.WOLF, new BlockPos(3, 2, 3));
        down(helper, wolf);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            InteractionResult result = sneakUse(player, helper, wolf);

            // CarryHandler declines (not carryable) and DownedHandler suppresses the empty-hand use.
            helper.assertTrue(result == InteractionResult.FAIL, "an adult wolf is not scooped up");
            helper.assertTrue(wolf.getVehicle() == null, "the adult wolf stays where it fell");
            helper.assertTrue(carryModifier(player) == null, "no slowdown is applied");
            helper.succeed();
        } finally {
            player.discard();
            wolf.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void sneakUseOnABlockSetsTheCarriedPetDown(GameTestHelper helper) {
        Cat cat = spawnTamed(helper, EntityType.CAT, new BlockPos(3, 2, 3));
        down(helper, cat);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            sneakUse(player, helper, cat);
            helper.assertTrue(cat.getVehicle() == player, "precondition: the cat is being carried");

            InteractionResult result = sneakUseBlock(player, helper);

            helper.assertTrue(result == InteractionResult.SUCCESS, "the set-down consumes the block use");
            helper.assertTrue(cat.getVehicle() == null, "the cat dismounts on set-down");
            helper.assertTrue(InstinctAPI.isDowned(cat), "the set-down cat is still downed");
            helper.assertTrue(carryModifier(player) == null, "the carrier's slowdown is cleared");
            helper.succeed();
        } finally {
            player.discard();
            cat.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void revivingACarriedPetReleasesItAndClearsSlowdown(GameTestHelper helper) {
        Cat cat = spawnTamed(helper, EntityType.CAT, new BlockPos(3, 2, 3));
        down(helper, cat);
        ServerPlayer carrier = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer reviver = MockPlayers.serverPlayerInLevel(helper);
        try {
            sneakUse(carrier, helper, cat);
            helper.assertTrue(cat.getVehicle() == carrier, "precondition: the cat is carried");
            helper.assertTrue(carryModifier(carrier) != null, "precondition: the carrier is slowed");

            // A co-op partner revives the cat in the carrier's arms (a revive item, no sneak).
            reviver.setShiftKeyDown(false);
            reviver.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            InteractionResult result = UseEntityCallback.EVENT.invoker()
                    .interact(reviver, helper.getLevel(), InteractionHand.MAIN_HAND, cat, null);

            helper.assertTrue(result == InteractionResult.CONSUME, "the revive consumes the interaction");
            helper.assertFalse(InstinctAPI.isDowned(cat), "the cat is revived");
            helper.assertTrue(cat.getVehicle() == null, "a revived cat dismounts");
            helper.assertTrue(carryModifier(carrier) == null, "the carrier's slowdown is cleared on release");
            helper.succeed();
        } finally {
            carrier.discard();
            reviver.discard();
            cat.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aSecondPetCannotBePickedUpWhileHandsAreFull(GameTestHelper helper) {
        Cat first = spawnTamed(helper, EntityType.CAT, new BlockPos(2, 2, 3));
        Cat second = spawnTamed(helper, EntityType.CAT, new BlockPos(4, 2, 3));
        down(helper, first);
        down(helper, second);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            sneakUse(player, helper, first);
            helper.assertTrue(player.getFirstPassenger() == first, "precondition: carrying the first cat");

            InteractionResult result = sneakUse(player, helper, second);
            helper.assertTrue(result == InteractionResult.CONSUME, "the hands-full gesture is consumed, not passed");
            helper.assertTrue(second.getVehicle() == null, "the second cat is not picked up");
            helper.assertTrue(player.getFirstPassenger() == first, "the first cat is still the one carried");
            helper.succeed();
        } finally {
            player.discard();
            first.discard();
            second.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctCarryDisabled")
    public void disabledConfigNeverScoopsUp(GameTestHelper helper) {
        boolean saved = InstinctConfig.get().enableCarryDowned;
        try {
            InstinctConfig.get().enableCarryDowned = false;
            Cat cat = spawnTamed(helper, EntityType.CAT, new BlockPos(3, 2, 3));
            down(helper, cat);
            ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
            try {
                sneakUse(player, helper, cat);
                helper.assertTrue(cat.getVehicle() == null, "with carrying off, a downed cat is never lifted");
                helper.assertTrue(carryModifier(player) == null, "no slowdown is applied while disabled");
                helper.succeed();
            } finally {
                player.discard();
                cat.discard();
            }
        } finally {
            InstinctConfig.get().enableCarryDowned = saved;
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private static <T extends TamableAnimal> T spawnTamed(GameTestHelper helper, EntityType<T> type, BlockPos rel) {
        T animal = type.create(helper.getLevel());
        if (animal == null) {
            throw new IllegalStateException("could not create " + type);
        }
        BlockPos abs = helper.absolutePos(rel);
        animal.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
        animal.setTame(true, false);
        animal.setOwnerUUID(UUID.randomUUID());
        helper.getLevel().addFreshEntity(animal);
        return animal;
    }

    /** Applies a lethal arrow hit (not beyond saving), downing a healthy tamed pet. */
    private static void down(GameTestHelper helper, Animal animal) {
        AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
        if (arrow == null) {
            throw new IllegalStateException("could not create an arrow");
        }
        animal.hurt(helper.getLevel().damageSources().arrow(arrow, null), 1000.0F);
        arrow.discard();
    }

    private static InteractionResult sneakUse(ServerPlayer player, GameTestHelper helper, Animal target) {
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return UseEntityCallback.EVENT.invoker()
                .interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, target, null);
    }

    private static InteractionResult sneakUseBlock(ServerPlayer player, GameTestHelper helper) {
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockHitResult hit = new BlockHitResult(player.position(), Direction.UP, player.blockPosition(), false);
        return UseBlockCallback.EVENT.invoker()
                .interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
    }

    private static AttributeModifier carryModifier(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed == null ? null : speed.getModifier(CarryHandler.CARRY_SLOW_ID);
    }

    /**
     * Whether the carry slowdown is a transient modifier — active on the live attribute but absent
     * from {@link AttributeInstance#save()}, which serializes only permanent modifiers. That is the
     * property that keeps a logout from stranding a slowed player.
     */
    private static boolean isTransient(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getModifier(CarryHandler.CARRY_SLOW_ID) == null) {
            return false;
        }
        CompoundTag saved = speed.save();
        if (!saved.contains("modifiers")) {
            return true;
        }
        ListTag modifiers = saved.getList("modifiers", Tag.TAG_COMPOUND);
        String id = CarryHandler.CARRY_SLOW_ID.toString();
        for (int i = 0; i < modifiers.size(); i++) {
            if (id.equals(modifiers.getCompound(i).getString("id"))) {
                return false;
            }
        }
        return true;
    }
}
