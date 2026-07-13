package com.rfizzle.instinct.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The keepsake engraving is the persisted memento format ({@code mc-persistence}): it must survive a
 * world save unchanged. The name rides a {@code Component}, so the codec bootstraps vanilla
 * registries once; rank and days are plain ints. Both a custom-named pet (a literal name) and an
 * unnamed one (a translatable species name) must round-trip intact.
 */
class KeepsakeEngravingCodecTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void customNamedRankedPetRoundTrips() {
        KeepsakeEngraving engraving = new KeepsakeEngraving(Component.literal("Rex"), 3, 63);
        JsonElement encoded = KeepsakeEngraving.CODEC.encodeStart(JsonOps.INSTANCE, engraving).getOrThrow();
        KeepsakeEngraving decoded = KeepsakeEngraving.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(engraving, decoded);
        assertEquals("Rex", decoded.petName().getString());
        assertEquals(3, decoded.rank());
        assertEquals(63, decoded.daysSeen());
    }

    @Test
    void unnamedUnrankedPetRoundTrips() {
        KeepsakeEngraving engraving =
                new KeepsakeEngraving(Component.translatable("entity.minecraft.wolf"), 0, 4);
        JsonElement encoded = KeepsakeEngraving.CODEC.encodeStart(JsonOps.INSTANCE, engraving).getOrThrow();
        KeepsakeEngraving decoded = KeepsakeEngraving.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(engraving, decoded);
        assertEquals(0, decoded.rank());
        assertEquals(4, decoded.daysSeen());
    }

    @Test
    void absentFieldsDecodeToDefaults() {
        // Every field is optional so a memento written before a field existed — or with one
        // sub-field it cannot decode — degrades to the default instead of losing the whole component.
        KeepsakeEngraving decoded = KeepsakeEngraving.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();

        assertEquals(Component.empty(), decoded.petName());
        assertEquals(0, decoded.rank());
        assertEquals(0, decoded.daysSeen());
    }
}
