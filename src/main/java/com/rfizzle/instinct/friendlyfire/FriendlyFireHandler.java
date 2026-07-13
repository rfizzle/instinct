package com.rfizzle.instinct.friendlyfire;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

/**
 * Owner friendly-fire protection ({@code design/SPEC.md} §1): an owner's own damage never lands on
 * their own pets, whatever the source — melee, sweep, arrows, splash and lingering potions, the
 * explosion the owner set off. All of these credit the owning player as the damage's causing entity
 * ({@link DamageSource#getEntity()}), so one {@link ServerLivingEntityEvents#ALLOW_DAMAGE} listener
 * covers every path: cancel the blow when the victim is a pets-set tamed animal owned by that player.
 *
 * <p>Scope is deliberate: only the <b>pets set</b> — livestock and mounts stay vulnerable to their
 * keeper (a cow is not a {@link TamableAnimal}, so the type gate excludes it), and another player's
 * damage is untouched (this is about your own hand, not invulnerability). The rank-2 "knows your
 * swing" sweep-dodge ({@code PlayerMixin}) is the narrower mechanic that still governs a veteran when
 * this protection is switched off.
 *
 * <p>The listener fires for every damage source, so the config gate runs first and the common
 * no-pet case does no work. It fails <b>open</b> — a broken check returns "allow", never a pet made unhittable.
 */
public final class FriendlyFireHandler {

    private FriendlyFireHandler() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(FriendlyFireHandler::onAllowDamage);
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        try {
            return !cancels(entity, source);
        } catch (Exception e) {
            // Fail open: a broken friendly-fire check must never leave a pet unhittable.
            Instinct.LOGGER.error("Friendly-fire check failed for {}", entity.getType(), e);
            return true;
        }
    }

    /**
     * Whether this blow is an owner striking their own pets-set pet. Cheapest tests first: the config
     * gate, then the pet/ownership resolution only if the protection is on.
     */
    static boolean cancels(LivingEntity victim, DamageSource source) {
        boolean protectionOn = InstinctConfig.get().enableOwnerFriendlyFireProtection;
        // Resolve ownership only when the protection is on — the config gate skips all per-hit work.
        boolean ownedPet = protectionOn && ownedPetOfAttacker(victim, source);
        return FriendlyFire.blocks(protectionOn, ownedPet);
    }

    private static boolean ownedPetOfAttacker(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof TamableAnimal pet) || !pet.isTame()
                || !(source.getEntity() instanceof Player attacker) || !pet.isOwnedBy(attacker)) {
            return false;
        }
        return AnimalCoverage.membershipOf(pet).pet();
    }
}
