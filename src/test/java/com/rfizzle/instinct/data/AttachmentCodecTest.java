package com.rfizzle.instinct.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rfizzle.instinct.api.Perk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Round-trip and forward-compatibility contracts for the three persistent attachment codecs. The
 * codecs are static fields on plain records, so they test without attachment registration (which
 * only happens in {@code onInitialize}).
 */
class AttachmentCodecTest {

    @Test
    void veterancyDataRoundTrips() {
        VeterancyData data = new VeterancyData(42.5, 123456L);
        JsonObject encoded = VeterancyData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow().getAsJsonObject();
        VeterancyData decoded = VeterancyData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(data, decoded);
    }

    @Test
    void geneticsDataRoundTrips() {
        GeneticsData data = new GeneticsData(2, Perk.FLEET, true, 9000L);
        JsonObject encoded = GeneticsData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow().getAsJsonObject();
        GeneticsData decoded = GeneticsData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(data, decoded);
    }

    @Test
    void downedDataRoundTrips() {
        DownedData data = new DownedData(777L);
        JsonObject encoded = DownedData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow().getAsJsonObject();
        DownedData decoded = DownedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(data, decoded);
    }

    @Test
    void absentFieldsDecodeToDefaults() {
        JsonObject empty = new JsonObject();

        VeterancyData veterancy = VeterancyData.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow();
        assertEquals(new VeterancyData(), veterancy);

        GeneticsData genetics = GeneticsData.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow();
        assertEquals(new GeneticsData(), genetics);
        assertEquals(Perk.NONE, genetics.perk());

        DownedData downed = DownedData.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow();
        assertEquals(new DownedData(), downed);
    }

    @Test
    void hostileSaveDataHealsOnDecode() {
        JsonObject tampered = JsonParser.parseString("""
                { "grade": 99, "perk": "fleet" }
                """).getAsJsonObject();
        GeneticsData genetics = GeneticsData.CODEC.parse(JsonOps.INSTANCE, tampered).getOrThrow();
        assertEquals(2, genetics.grade(), "an out-of-range grade heals to the prime cap");

        JsonObject negativeDays = JsonParser.parseString("""
                { "accruedDays": -12.0 }
                """).getAsJsonObject();
        VeterancyData veterancy = VeterancyData.CODEC.parse(JsonOps.INSTANCE, negativeDays).getOrThrow();
        assertEquals(0.0, veterancy.accruedDays(), "negative days heal to zero");
    }

    @Test
    void unknownPerkFallsBackToDefault() {
        JsonObject unknownPerk = JsonParser.parseString("""
                { "grade": 1, "perk": "bogus_perk" }
                """).getAsJsonObject();
        // optionalFieldOf treats an unparseable field as absent — the record still decodes.
        GeneticsData genetics = GeneticsData.CODEC.parse(JsonOps.INSTANCE, unknownPerk).getOrThrow();
        assertEquals(Perk.NONE, genetics.perk());
        assertEquals(1, genetics.grade());
        assertFalse(genetics.primeNextOffspring());
    }
}
