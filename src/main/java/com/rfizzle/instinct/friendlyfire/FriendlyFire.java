package com.rfizzle.instinct.friendlyfire;

/**
 * Pure core of owner friendly-fire protection ({@code design/SPEC.md} §1): the boolean verdict for
 * whether a blow should be cancelled, stripped of every Minecraft type so it unit-tests without a
 * world. The shell ({@link FriendlyFireHandler}) resolves the two facts this needs — the protection
 * toggle and whether the victim is a pets-set pet owned by the attacking player — and this decides.
 */
public final class FriendlyFire {

    private FriendlyFire() {
    }

    /**
     * Whether an owner's own damage on their own pet is cancelled. Only when the protection is on
     * and the blow came from the pet's own owner's hand; every other blow (protection off, an
     * unowned victim, another player, a non-player source) lands exactly as vanilla.
     */
    public static boolean blocks(boolean protectionOn, boolean ownedPetOfAttacker) {
        return protectionOn && ownedPetOfAttacker;
    }
}
