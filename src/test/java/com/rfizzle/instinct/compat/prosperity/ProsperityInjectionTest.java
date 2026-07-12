package com.rfizzle.instinct.compat.prosperity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped Prosperity loot-injection data (SPEC §Compat, consumer): it stays inert
 * without Prosperity (only Prosperity's reload listener reads {@code data/prosperity/}), it carries
 * the Fabric load-condition gating on {@code instinct}, and it references the vet kit and pedigree
 * treat at Prosperity's higher distance tiers — so a rename of either item id trips this test.
 */
class ProsperityInjectionTest {

    private static final String RESOURCE = "/data/prosperity/loot_injections/instinct_kit.json";
    private static final Path SOURCE =
            Path.of("src/main/resources/data/prosperity/loot_injections/instinct_kit.json");

    private static JsonObject injectionFile() {
        try (InputStream in = ProsperityInjectionTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load instinct_kit.json", e);
        }
    }

    @Test
    void gatedOnInstinctPresence() {
        JsonArray conditions = injectionFile().getAsJsonArray("fabric:load_conditions");
        assertTrue(conditions != null && !conditions.isEmpty(), "the file carries a Fabric load condition");
        JsonObject first = conditions.get(0).getAsJsonObject();
        assertEquals("fabric:all_mods_loaded", first.get("condition").getAsString());
        assertEquals("instinct", first.getAsJsonArray("values").get(0).getAsString());
    }

    @Test
    void injectsBothItemsAtHigherTiers() {
        JsonArray injections = injectionFile().getAsJsonArray("injections");
        assertTrue(injections != null && injections.size() == 2, "two injections: vet kit and pedigree treat");

        List<String> items = new ArrayList<>();
        List<String> tiers = new ArrayList<>();
        for (var element : injections) {
            JsonObject injection = element.getAsJsonObject();
            assertEquals("prosperity:all_chests", injection.get("target").getAsString(),
                    "injections target Prosperity's chest set, never a vanilla table Instinct owns");
            tiers.add(injection.get("min_tier").getAsString());
            for (var entry : injection.getAsJsonArray("entries")) {
                items.add(entry.getAsJsonObject().get("item").getAsString());
            }
        }
        assertTrue(items.contains("instinct:vet_kit"), "the vet kit is injected");
        assertTrue(items.contains("instinct:pedigree_treat"), "the pedigree treat is injected");
        assertTrue(tiers.contains("outlands") && tiers.contains("depths"),
                "both live at Prosperity's higher distance tiers");
    }
}
