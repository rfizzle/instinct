package com.rfizzle.instinct.whistle;

/**
 * The command whistle's selection and targeting logic ({@code design/SPEC.md} §6), as pure boolean
 * functions with no Minecraft types — the {@code mc-mod-testing} Tier-1 seam. The live handlers in
 * {@link WhistleActions} read the entity state (tamed, owner, downed, coverage, attributes, PvP) and
 * pass the booleans through these rules, so the direction and filter decisions are unit-testable
 * without a server.
 */
public final class WhistleRules {

    private WhistleRules() {
    }

    /**
     * The Stay/Follow toggle direction: any commandable pet currently standing means the whole pack
     * sits (Stay); only when all are already sitting does the pack stand (Follow). One press always
     * yields one coherent pack state.
     */
    public static boolean shouldSitAll(boolean anyStanding) {
        return anyStanding;
    }

    /** Whether a pet answers the owner's whistle: tamed, owned by this player, not downed, and a
     *  covered pets-set animal. */
    public static boolean isCommandablePet(boolean tame, boolean ownedByPlayer, boolean downed,
                                           boolean petCovered) {
        return tame && ownedByPlayer && !downed && petCovered;
    }

    /** Whether a commandable pet can be sent to attack — it must carry an attack-damage attribute
     *  (wolves and most modded fighters; cats and parrots have none). */
    public static boolean isCombatCapable(boolean hasAttackDamageAttribute) {
        return hasAttackDamageAttribute;
    }

    /**
     * Whether a raycast entity is a valid whistle attack target. Excludes the user, the user's own
     * pets, covered livestock (those order a round-up instead), downed entities, and spectator or
     * creative players; a player target is valid only when server PvP is enabled.
     */
    public static boolean isValidAttackTarget(boolean isSelf, boolean isOwnPet, boolean isCoveredLivestock,
                                              boolean isDowned, boolean isSpectator, boolean isCreativePlayer,
                                              boolean isPlayer, boolean pvpAllowed) {
        if (isSelf || isOwnPet || isCoveredLivestock || isDowned || isSpectator || isCreativePlayer) {
            return false;
        }
        return !isPlayer || pvpAllowed;
    }

    /**
     * Whether a guarding pet engages an entity that entered its post: hostile monsters only, and only
     * while alive. Players and animals — the user's own pets, another player's pets, and every head of
     * livestock — are never guard targets, so a guard holds a pen without ever turning on its charges.
     */
    public static boolean isGuardTarget(boolean isEnemy, boolean isAlive) {
        return isEnemy && isAlive;
    }
}
