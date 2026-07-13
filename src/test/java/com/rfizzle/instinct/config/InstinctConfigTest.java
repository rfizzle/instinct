package com.rfizzle.instinct.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstinctConfigTest {

    /** Every key the SPEC Configuration table requires the file to carry. */
    private static final List<String> SPEC_KEYS = List.of(
            "autoDetectAnimals", "petsInclude", "petsExclude", "livestockInclude", "livestockExclude",
            "mountsInclude", "mountsExclude",
            "enableSelfPreservation", "creeperBerthBlocks", "teleportSuppressFallDistance",
            "enableVeterancy", "veterancyThresholdDays", "healthPerRank", "damagePerRank",
            "enableRankBehaviors", "warningRadiusBlocks", "mentorRadiusBlocks", "mentorRateBonus",
            "enableGenetics", "enableGenericDropMirror", "hayRadiusBlocks", "crowdingThreshold",
            "crowdingRadiusBlocks", "gradeUpgradeChance", "gradeDowngradeChance",
            "enableFlocking", "flockSpeedMultiplier", "flockSpacingBlocks", "enableHerding", "herdingMaxPets",
            "enableTrough", "troughRadiusBlocks", "troughFeedIntervalTicks", "troughPopulationCap",
            "enableWhistle", "whistleRadiusBlocks", "whistleTargetRangeBlocks", "whistleCooldownTicks",
            "roundUpGroupRadiusBlocks",
            "enableDownedState", "reviveHealthFraction", "downedRankPenalty",
            "enableCarryDowned", "carrySlowdownFraction",
            "enableInspection");

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve("instinct.json");
    }

    @Test
    void firstLaunchWritesEverySpecKeyAtDefaults(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("instinct.json");
        InstinctConfig config = InstinctConfig.load(path);

        assertTrue(Files.exists(path), "first launch should write the config file");
        JsonObject written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        for (String key : SPEC_KEYS) {
            assertTrue(written.has(key), "config file should carry SPEC key " + key);
        }
        assertEquals(1, written.get("configVersion").getAsInt());
        assertTrue(config.autoDetectAnimals);
        assertEquals(4, config.creeperBerthBlocks);
        assertEquals(List.of(10, 30, 60), config.veterancyThresholdDays);
    }

    @Test
    void outOfRangeValuesClampAfterLoad() throws IOException {
        Files.writeString(configFile(), """
                {
                  "configVersion": 1,
                  "creeperBerthBlocks": 99,
                  "teleportSuppressFallDistance": -3.0,
                  "flockSpeedMultiplier": 9.9,
                  "troughPopulationCap": -5,
                  "reviveHealthFraction": 2.0,
                  "whistleCooldownTicks": 101
                }
                """);
        InstinctConfig config = InstinctConfig.load(configFile());

        assertEquals(8, config.creeperBerthBlocks);
        assertEquals(0.5, config.teleportSuppressFallDistance);
        assertEquals(1.5, config.flockSpeedMultiplier);
        assertEquals(0, config.troughPopulationCap);
        assertEquals(1.0, config.reviveHealthFraction);
        assertEquals(100, config.whistleCooldownTicks);
    }

    @Test
    void corruptedFileFallsBackToDefaultsWithoutOverwriting() throws IOException {
        String garbage = "{ this is not json";
        Files.writeString(configFile(), garbage);
        InstinctConfig config = InstinctConfig.load(configFile());

        assertEquals(4, config.creeperBerthBlocks, "corrupted file should yield defaults");
        assertEquals(garbage, Files.readString(configFile()), "corrupted file must be left untouched");
    }

    @Test
    void nonObjectJsonFallsBackToDefaultsWithoutOverwriting() throws IOException {
        Files.writeString(configFile(), "[1, 2, 3]");
        InstinctConfig config = InstinctConfig.load(configFile());

        assertEquals(4, config.creeperBerthBlocks);
        assertEquals("[1, 2, 3]", Files.readString(configFile()), "non-object file must be left untouched");
    }

    @Test
    void missingKeysFillWithDefaultsAndMigratedFilePersists() throws IOException {
        Files.writeString(configFile(), "{}");
        InstinctConfig config = InstinctConfig.load(configFile());

        assertEquals(16, config.troughPopulationCap);
        assertEquals(List.of(10, 30, 60), config.veterancyThresholdDays);
        JsonObject written = JsonParser.parseString(Files.readString(configFile())).getAsJsonObject();
        assertEquals(1, written.get("configVersion").getAsInt(),
                "a v0 file should be migrated and persisted back at v1");
    }

    @Test
    void entityIdListsNormalizeAndDropInvalidEntries() throws IOException {
        Files.writeString(configFile(), """
                {
                  "configVersion": 1,
                  "livestockExclude": ["cow", "minecraft:sheep", "not a##valid id", "modid:otter", "cow"]
                }
                """);
        InstinctConfig config = InstinctConfig.load(configFile());

        assertEquals(List.of("minecraft:cow", "minecraft:sheep", "modid:otter"), config.livestockExclude);
        assertTrue(config.livestockExcludeSet.contains("minecraft:cow"));
        assertFalse(config.livestockExcludeSet.contains("not a##valid id"));
    }

    @Test
    void thresholdListSanitizes() throws IOException {
        Files.writeString(configFile(), """
                {
                  "configVersion": 1,
                  "veterancyThresholdDays": [60, 10, 10, 2000, -5, 30]
                }
                """);
        InstinctConfig config = InstinctConfig.load(configFile());

        // clamp (2000→1000, -5→1), dedupe, sort ascending, truncate to 3
        assertEquals(List.of(1, 10, 30), config.veterancyThresholdDays);
    }

    @Test
    void emptyThresholdListFallsBackToDefaults() throws IOException {
        Files.writeString(configFile(), """
                { "configVersion": 1, "veterancyThresholdDays": [] }
                """);
        InstinctConfig config = InstinctConfig.load(configFile());

        assertEquals(List.of(10, 30, 60), config.veterancyThresholdDays);
    }
}
