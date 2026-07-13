package com.rfizzle.instinct.friendlyfire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendlyFireTest {

    @Test
    void blocksOnlyOwnerHitsOnOwnPetWhileProtectionOn() {
        assertTrue(FriendlyFire.blocks(true, true), "owner's own hit on their own pet is cancelled");
        assertFalse(FriendlyFire.blocks(true, false), "a hit that isn't the pet's owner still lands");
        assertFalse(FriendlyFire.blocks(false, true), "protection off — even the owner's hit lands");
        assertFalse(FriendlyFire.blocks(false, false), "nothing to block");
    }
}
