package com.rfizzle.instinct.whistle;

import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.GuardData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.guard.Guard;
import com.rfizzle.instinct.herding.Herding;
import com.rfizzle.instinct.registry.InstinctCriteria;
import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.registry.InstinctSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The command whistle's server-side commands ({@code design/SPEC.md} §6). The core methods
 * ({@link #toggle}, {@link #attackOrder}, {@link #roundUp}, {@link #command}) mutate entity state and
 * return a {@link WhistleResult} for the caller and for gametests; the {@code perform*} wrappers add
 * the shared side effects every whistle action owes — the feature-disabled "silent" line, the
 * action-bar feedback and cue, the item cooldown, and the Pack Leader advancement. Pet selection,
 * ownership, and targeting all resolve here on the server against the server's radius and data;
 * nothing trusts the client.
 */
public final class WhistleActions {

    /** A round-up order runs at most this many ticks before the pets stand down (§6). */
    private static final int ROUND_UP_DEADLINE_TICKS = 600;

    private WhistleActions() {
    }

    /** The outcome of one whistle action, with the pet count that fills the {@code <n>} feedback. */
    public record WhistleResult(Outcome outcome, int count) {
        public enum Outcome {
            FOLLOW, STAY, NO_PETS, ATTACK, NO_TARGET, ROUND_UP, NOTHING_TO_ROUND_UP, GUARD, SILENT
        }

        /** Outcomes that actually command pets — the ones that can grant Pack Leader. */
        boolean commandsPets() {
            return count > 0 && (outcome == Outcome.FOLLOW || outcome == Outcome.STAY
                    || outcome == Outcome.ATTACK || outcome == Outcome.ROUND_UP || outcome == Outcome.GUARD);
        }
    }

    // ── Full entry points (item / payload / attack callbacks) ────────────────────────────────────

    /** Left-click: the Stay/Follow toggle, with feedback, cue, cooldown, and advancement. */
    public static void performToggle(ServerPlayer player) {
        if (silentIfDisabled(player)) {
            return;
        }
        present(player, toggle(player));
    }

    /** Right-click: raycast then attack-or-round-up, with feedback, cue, cooldown, and advancement. */
    public static void performCommand(ServerPlayer player) {
        if (silentIfDisabled(player)) {
            return;
        }
        present(player, command(player));
    }

    /** Sneak + right-click: post the pack to guard the looked-at spot, with feedback, cue, and cooldown. */
    public static void performGuard(ServerPlayer player) {
        if (silentIfDisabled(player)) {
            return;
        }
        present(player, guardOrder(player, guardAnchor(player)));
    }

    // ── Core commands (state changes only; unit/gametest seam) ───────────────────────────────────

    /** Toggles every commandable pet in range into one coherent Stay/Follow state. */
    public static WhistleResult toggle(ServerPlayer player) {
        List<TamableAnimal> pets = commandablePets(player);
        if (pets.isEmpty()) {
            return new WhistleResult(WhistleResult.Outcome.NO_PETS, 0);
        }
        boolean anyStanding = pets.stream().anyMatch(pet -> !pet.isOrderedToSit());
        boolean sit = WhistleRules.shouldSitAll(anyStanding);
        for (TamableAnimal pet : pets) {
            pet.removeAttached(InstinctAttachments.GUARD);
            pet.setOrderedToSit(sit);
        }
        return new WhistleResult(sit ? WhistleResult.Outcome.STAY : WhistleResult.Outcome.FOLLOW, pets.size());
    }

    /** Resolves the raycast target and dispatches to a round-up (covered livestock) or an attack. */
    public static WhistleResult command(ServerPlayer player) {
        LivingEntity target = livingOnCrosshair(player, InstinctConfig.get().whistleTargetRangeBlocks);
        if (target == null) {
            return new WhistleResult(WhistleResult.Outcome.NO_TARGET, 0);
        }
        // Covered livestock are never attack targets — they order a round-up (or nothing, if herding off).
        if (target instanceof Animal animal && AnimalCoverage.membershipOf(animal).livestock()) {
            if (!InstinctConfig.get().enableHerding) {
                return new WhistleResult(WhistleResult.Outcome.NOTHING_TO_ROUND_UP, 0);
            }
            return roundUp(player, animal);
        }
        MinecraftServer server = player.getServer();
        boolean pvpAllowed = server != null && server.isPvpAllowed();
        boolean valid = WhistleRules.isValidAttackTarget(
                target == player,
                isOwnPet(player, target),
                false,
                InstinctAPI.isDowned(target),
                target.isSpectator(),
                target instanceof Player p && p.isCreative(),
                target instanceof Player,
                pvpAllowed);
        if (!valid) {
            return new WhistleResult(WhistleResult.Outcome.NO_TARGET, 0);
        }
        return attackOrder(player, target);
    }

    /** Sends every combat-capable pet in range to attack the target (an attack overrides Stay). */
    public static WhistleResult attackOrder(ServerPlayer player, LivingEntity target) {
        int commanded = 0;
        for (TamableAnimal pet : commandablePets(player)) {
            pet.removeAttached(InstinctAttachments.GUARD);
            if (WhistleRules.isCombatCapable(pet.getAttribute(Attributes.ATTACK_DAMAGE) != null)) {
                pet.setOrderedToSit(false);
                pet.setTarget(target);
                commanded++;
            }
        }
        return new WhistleResult(WhistleResult.Outcome.ATTACK, commanded);
    }

    /**
     * Posts every commandable pet that can fight to guard {@code anchor}: it stands, drops any prior
     * target, and takes a persistent {@code GuardData} order (the {@code GuardGoal} reads it
     * from there). Only pets with a melee goal to act on a target ({@link Guard#canFight}) are posted;
     * a parrot or cat — which carries the attack-damage attribute in 1.21.1 yet has no such goal — is
     * left out, since a guard order works by handing that goal a target. The order lasts until any new
     * whistle order replaces it.
     */
    public static WhistleResult guardOrder(ServerPlayer player, BlockPos anchor) {
        int commanded = 0;
        for (TamableAnimal pet : commandablePets(player)) {
            if (Guard.canFight(pet)) {
                pet.setOrderedToSit(false);
                pet.setTarget(null);
                pet.setAttached(InstinctAttachments.GUARD, new GuardData(anchor));
                commanded++;
            }
        }
        return new WhistleResult(WhistleResult.Outcome.GUARD, commanded);
    }

    /**
     * Orders a round-up: builds the drive group (the target plus every covered same-species animal
     * within {@code roundUpGroupRadiusBlocks}, excluding leashed and in-vehicle animals) and hands it
     * to §4's press machinery via a whistle order toward the player. An empty group rounds up nothing.
     */
    public static WhistleResult roundUp(ServerPlayer player, Animal target) {
        for (TamableAnimal pet : commandablePets(player)) {
            pet.removeAttached(InstinctAttachments.GUARD);
        }
        ServerLevel level = player.serverLevel();
        double radius = InstinctConfig.get().roundUpGroupRadiusBlocks;
        List<Animal> group = new ArrayList<>();
        if (eligibleGroupMember(target)) {
            group.add(target);
        }
        AABB box = target.getBoundingBox().inflate(radius);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, box, candidate ->
                candidate != target
                        && candidate.getType() == target.getType()
                        && eligibleGroupMember(candidate)
                        && candidate.distanceToSqr(target) <= radius * radius
                        && AnimalCoverage.membershipOf(candidate).livestock())) {
            group.add(animal);
        }
        if (group.isEmpty()) {
            return new WhistleResult(WhistleResult.Outcome.NOTHING_TO_ROUND_UP, 0);
        }
        Herding.startRoundUp(group, player, level.getGameTime(), ROUND_UP_DEADLINE_TICKS);
        return new WhistleResult(WhistleResult.Outcome.ROUND_UP, commandablePets(player).size());
    }

    // ── Selection ────────────────────────────────────────────────────────────────────────────────

    /** Every owned, tamed, non-downed pets-set animal within {@code whistleRadiusBlocks}. */
    public static List<TamableAnimal> commandablePets(ServerPlayer player) {
        double radius = InstinctConfig.get().whistleRadiusBlocks;
        AABB box = player.getBoundingBox().inflate(radius);
        List<TamableAnimal> pets = new ArrayList<>();
        for (TamableAnimal pet : player.serverLevel().getEntitiesOfClass(TamableAnimal.class, box, candidate ->
                candidate.distanceToSqr(player) <= radius * radius
                        && WhistleRules.isCommandablePet(candidate.isTame(), candidate.isOwnedBy(player),
                        InstinctAPI.isDowned(candidate), AnimalCoverage.membershipOf(candidate).pet()))) {
            pets.add(pet);
        }
        return pets;
    }

    private static boolean eligibleGroupMember(Animal animal) {
        return animal.isAlive() && !animal.isLeashed() && !animal.isPassenger();
    }

    private static boolean isOwnPet(ServerPlayer player, LivingEntity target) {
        return target instanceof TamableAnimal pet && pet.isTame() && pet.isOwnedBy(player);
    }

    /**
     * Raycast from the player's eye along their view vector and return the first living entity within
     * range, or {@code null}. Entity bounding boxes only, mirroring {@code Inspection}'s crosshair.
     */
    private static LivingEntity livingOnCrosshair(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 view = player.getViewVector(1.0f);
        Vec3 end = eye.add(view.x * range, view.y * range, view.z * range);
        AABB box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                candidate -> candidate instanceof LivingEntity && candidate != player
                        && !candidate.isSpectator() && candidate.isAlive(),
                range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /**
     * The guard post: the block the player is looking at within {@code whistleTargetRangeBlocks},
     * falling back to the player's own feet when the crosshair rests on nothing — so a guard order
     * always has a spot, aimed at a fence post or dropped where you stand.
     */
    private static BlockPos guardAnchor(ServerPlayer player) {
        HitResult hit = player.pick(InstinctConfig.get().whistleTargetRangeBlocks, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block) {
            return block.getBlockPos();
        }
        return player.blockPosition();
    }

    // ── Shared side effects ──────────────────────────────────────────────────────────────────────

    /** Shows the inert "silent" line and burns the cooldown when the feature is off; {@code true} then. */
    private static boolean silentIfDisabled(ServerPlayer player) {
        if (InstinctConfig.get().enableWhistle) {
            return false;
        }
        present(player, new WhistleResult(WhistleResult.Outcome.SILENT, 0));
        return true;
    }

    /** Emits the localized feedback line and cue, applies the cooldown, and grants Pack Leader. */
    private static void present(ServerPlayer player, WhistleResult result) {
        player.displayClientMessage(Component.translatable(feedbackKey(result.outcome()), result.count()), true);
        SoundEvent cue = cueFor(result.outcome());
        if (cue != null) {
            player.serverLevel().playSound(null, player.blockPosition(), cue, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        cooldown(player);
        if (result.commandsPets()) {
            InstinctCriteria.WHISTLE_PACK.trigger(player, result.count());
        }
    }

    private static void cooldown(ServerPlayer player) {
        int ticks = InstinctConfig.get().whistleCooldownTicks;
        if (ticks > 0) {
            player.getCooldowns().addCooldown(InstinctItems.COMMAND_WHISTLE, ticks);
        }
    }

    private static String feedbackKey(WhistleResult.Outcome outcome) {
        return switch (outcome) {
            case FOLLOW -> "notification.instinct.whistle.follow";
            case STAY -> "notification.instinct.whistle.stay";
            case NO_PETS -> "notification.instinct.whistle.none";
            case ATTACK -> "notification.instinct.whistle.attack";
            case NO_TARGET -> "notification.instinct.whistle.no_target";
            case ROUND_UP -> "notification.instinct.whistle.round_up";
            case NOTHING_TO_ROUND_UP -> "notification.instinct.whistle.nothing";
            case GUARD -> "notification.instinct.whistle.guard";
            case SILENT -> "notification.instinct.whistle.silent";
        };
    }

    private static SoundEvent cueFor(WhistleResult.Outcome outcome) {
        return switch (outcome) {
            case FOLLOW -> InstinctSounds.WHISTLE_FOLLOW;
            case STAY -> InstinctSounds.WHISTLE_STAY;
            case ATTACK -> InstinctSounds.WHISTLE_ATTACK;
            case ROUND_UP -> InstinctSounds.WHISTLE_HERD;
            case GUARD -> InstinctSounds.WHISTLE_GUARD;
            default -> null;
        };
    }
}
