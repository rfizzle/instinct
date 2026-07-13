package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.gametest.util.MockPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * SPEC §1 owner friendly-fire protection: an owner's own damage never lands on their own pets,
 * whatever the source — melee, arrows, potions, an explosion they set off — while another player's
 * damage, and any damage to livestock, is untouched. Default-on; off restores vanilla. Each source
 * exercises the real {@code ServerLivingEntityEvents.ALLOW_DAMAGE} path against a real pet.
 */
public class FriendlyFireGameTest implements FabricGameTest {

    private static final double EPSILON = 1e-3;

    @GameTest(template = EMPTY_STRUCTURE)
    public void ownerMeleeSparesOwnPet(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));
            owner.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            owner.setOnGround(true);
            chargeAttack(helper, owner);

            Wolf pet = ownedWolf(helper, owner, new BlockPos(3, 2, 2));
            float before = pet.getHealth();

            owner.attack(pet);

            helper.assertTrue(Math.abs(pet.getHealth() - before) < EPSILON,
                    "the owner's own melee never lands on their own pet");
            pet.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ownerArrowSparesOwnPet(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            Wolf pet = ownedWolf(helper, owner, new BlockPos(3, 2, 3));
            float before = pet.getHealth();

            AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
            helper.assertTrue(arrow != null, "could not create an arrow");
            pet.hurt(helper.getLevel().damageSources().arrow(arrow, owner), 1000.0F);
            arrow.discard();

            helper.assertTrue(Math.abs(pet.getHealth() - before) < EPSILON,
                    "the owner's own arrow never lands on their own pet");
            pet.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ownerPotionSparesOwnPet(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            Wolf pet = ownedWolf(helper, owner, new BlockPos(3, 2, 3));
            float before = pet.getHealth();

            pet.hurt(helper.getLevel().damageSources().indirectMagic(owner, owner), 1000.0F);

            helper.assertTrue(Math.abs(pet.getHealth() - before) < EPSILON,
                    "the owner's own splash potion never lands on their own pet");
            pet.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ownerExplosionSparesOwnPet(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            Wolf pet = ownedWolf(helper, owner, new BlockPos(3, 2, 3));
            float before = pet.getHealth();

            pet.hurt(helper.getLevel().damageSources().explosion(null, owner), 1000.0F);

            helper.assertTrue(Math.abs(pet.getHealth() - before) < EPSILON,
                    "an explosion the owner set off never lands on their own pet");
            pet.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void anotherPlayersDamageStillLands(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        ServerPlayer other = MockPlayers.serverPlayerInLevel(helper);
        try {
            Wolf pet = ownedWolf(helper, owner, new BlockPos(3, 2, 3));
            float before = pet.getHealth();

            pet.hurt(helper.getLevel().damageSources().playerAttack(other), 2.0F);

            helper.assertTrue(pet.getHealth() < before,
                    "another player's damage is untouched — this is about your own hand, not invulnerability");
            pet.discard();
            helper.succeed();
        } finally {
            owner.discard();
            other.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ownerDamageStillLandsOnLivestock(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        try {
            Cow cow = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(cow != null, "could not create a cow");
            BlockPos abs = helper.absolutePos(new BlockPos(3, 2, 3));
            cow.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
            cow.setNoAi(true);
            helper.getLevel().addFreshEntity(cow);
            float before = cow.getHealth();

            cow.hurt(helper.getLevel().damageSources().playerAttack(owner), 2.0F);

            helper.assertTrue(cow.getHealth() < before, "livestock stay vulnerable to their keeper");
            cow.discard();
            helper.succeed();
        } finally {
            owner.discard();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void protectionOffRestoresVanilla(GameTestHelper helper) {
        ServerPlayer owner = MockPlayers.serverPlayerInLevel(helper);
        boolean saved = InstinctConfig.get().enableOwnerFriendlyFireProtection;
        try {
            InstinctConfig.get().enableOwnerFriendlyFireProtection = false;
            Wolf pet = ownedWolf(helper, owner, new BlockPos(3, 2, 3));
            float before = pet.getHealth();

            pet.hurt(helper.getLevel().damageSources().playerAttack(owner), 2.0F);

            helper.assertTrue(pet.getHealth() < before,
                    "with the protection off, the owner's own damage lands vanilla-style");
            pet.discard();
            helper.succeed();
        } finally {
            InstinctConfig.get().enableOwnerFriendlyFireProtection = saved;
            owner.discard();
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private static Wolf ownedWolf(GameTestHelper helper, ServerPlayer owner, BlockPos rel) {
        Wolf pet = VeterancyGameTest.spawnTamedWolf(helper, rel);
        pet.setOwnerUUID(owner.getUUID());
        pet.setHealth(pet.getMaxHealth());
        return pet;
    }

    private static void chargeAttack(GameTestHelper helper, ServerPlayer player) {
        try {
            var field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, 1000);
        } catch (ReflectiveOperationException e) {
            helper.fail("LivingEntity.attackStrengthTicker not found — signature changed? " + e);
        }
    }
}
