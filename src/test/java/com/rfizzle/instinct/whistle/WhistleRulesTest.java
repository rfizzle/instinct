package com.rfizzle.instinct.whistle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC §6 command-whistle selection logic as pure rules: the Stay/Follow toggle direction, the
 * commandable-pet filter (ownership/tamed/downed/coverage), combat-capability, and the attack-target
 * gate including the PvP rule. No Minecraft types — the Tier-1 seam under {@code WhistleActions}.
 */
class WhistleRulesTest {

    @Test
    void anyStandingPetSitsTheWholePack() {
        assertTrue(WhistleRules.shouldSitAll(true), "any pet standing → all sit (Stay)");
    }

    @Test
    void allSittingStandsTheWholePack() {
        assertFalse(WhistleRules.shouldSitAll(false), "all sitting → all stand (Follow)");
    }

    @Test
    void commandablePetIsTamedOwnedLivePetCovered() {
        assertTrue(WhistleRules.isCommandablePet(true, true, false, true),
                "a tamed, owned, non-downed, pet-covered animal answers the whistle");
    }

    @Test
    void untamedOrUnownedOrDownedOrUncoveredIsNotCommandable() {
        assertFalse(WhistleRules.isCommandablePet(false, true, false, true), "untamed is excluded");
        assertFalse(WhistleRules.isCommandablePet(true, false, false, true), "another player's pet is excluded");
        assertFalse(WhistleRules.isCommandablePet(true, true, true, true), "a downed pet is excluded");
        assertFalse(WhistleRules.isCommandablePet(true, true, false, false), "a non-pet-covered animal is excluded");
    }

    @Test
    void combatCapabilityTracksTheAttackAttribute() {
        assertTrue(WhistleRules.isCombatCapable(true), "a pet with an attack-damage attribute can attack");
        assertFalse(WhistleRules.isCombatCapable(false), "a pet without one (cat, parrot) cannot");
    }

    @Test
    void aPlainHostileIsAValidAttackTarget() {
        assertTrue(WhistleRules.isValidAttackTarget(false, false, false, false, false, false, false, false),
                "a non-player, non-pet, non-livestock, live, visible entity is attackable");
    }

    @Test
    void selfOwnPetLivestockDownedSpectatorCreativeAreNeverTargets() {
        assertFalse(target(true, false, false, false, false, false), "the user is never a target");
        assertFalse(target(false, true, false, false, false, false), "the user's own pet is never a target");
        assertFalse(target(false, false, true, false, false, false), "covered livestock is never a target");
        assertFalse(target(false, false, false, true, false, false), "a downed entity is never a target");
        assertFalse(target(false, false, false, false, true, false), "a spectator is never a target");
        assertFalse(target(false, false, false, false, false, true), "a creative player is never a target");
    }

    @Test
    void aPlayerTargetNeedsPvpEnabled() {
        assertFalse(WhistleRules.isValidAttackTarget(false, false, false, false, false, false, true, false),
                "a player is not a target while PvP is off");
        assertTrue(WhistleRules.isValidAttackTarget(false, false, false, false, false, false, true, true),
                "a player is a valid target only when PvP is on");
    }

    private static boolean target(boolean self, boolean ownPet, boolean livestock, boolean downed,
                                  boolean spectator, boolean creativePlayer) {
        return WhistleRules.isValidAttackTarget(self, ownPet, livestock, downed, spectator, creativePlayer, false, true);
    }

    @Test
    void aLiveHostileIsAGuardTarget() {
        assertTrue(WhistleRules.isGuardTarget(true, true), "a live hostile monster is engaged from the post");
    }

    @Test
    void nonHostilesAndDeadHostilesAreNeverGuardTargets() {
        assertFalse(WhistleRules.isGuardTarget(false, true),
                "a non-hostile (a player, your livestock, another player's pet) is never engaged");
        assertFalse(WhistleRules.isGuardTarget(true, false), "a dead hostile is not engaged");
    }
}
