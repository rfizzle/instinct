package com.rfizzle.instinct.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.rfizzle.instinct.Instinct;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The mod's JSON config ({@code config/instinct.json}), per {@code design/SPEC.md} §Configuration.
 * All keys sit flat at the top level so the JSON matches the spec's table verbatim; every key is
 * server-authoritative (Instinct has no client config — no HUD, no client rendering toggles;
 * inspection lines are server-driven), so no server→client sync view exists.
 */
public class InstinctConfig {
    private static final String CONFIG_FILENAME = "instinct.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile InstinctConfig INSTANCE;

    public int configVersion = ConfigMigrator.CURRENT_VERSION;

    // Animal Coverage
    public boolean autoDetectAnimals = true;
    public List<String> petsInclude = new ArrayList<>();
    public List<String> petsExclude = new ArrayList<>();
    public List<String> livestockInclude = new ArrayList<>();
    public List<String> livestockExclude = new ArrayList<>();
    public List<String> mountsInclude = new ArrayList<>();
    public List<String> mountsExclude = new ArrayList<>();

    // §1 Pet Self-Preservation
    public boolean enableSelfPreservation = true;
    public int creeperBerthBlocks = 4;
    public double teleportSuppressFallDistance = 3.0;

    // §2 Pet Veterancy
    public boolean enableVeterancy = true;
    public List<Integer> veterancyThresholdDays = defaultVeterancyThresholds();
    public double healthPerRank = 2.0;
    public double damagePerRank = 1.0;
    public boolean enableRankBehaviors = true;
    public int warningRadiusBlocks = 16;
    public int mentorRadiusBlocks = 16;
    public double mentorRateBonus = 0.25;

    // §3 Quality Genetics
    public boolean enableGenetics = true;
    public boolean enableGenericDropMirror = true;
    public int hayRadiusBlocks = 8;
    public int crowdingThreshold = 12;
    public int crowdingRadiusBlocks = 8;
    public double gradeUpgradeChance = 0.5;
    public double gradeDowngradeChance = 0.5;

    // §4 Flocking & Herding
    public boolean enableFlocking = true;
    public double flockSpeedMultiplier = 1.15;
    public double flockSpacingBlocks = 2.0;
    public boolean enableHerding = true;
    public int herdingMaxPets = 2;

    // §5 Feeding Trough
    public boolean enableTrough = true;
    public int troughRadiusBlocks = 10;
    public int troughFeedIntervalTicks = 40;
    public int troughPopulationCap = 16;

    // §6 Command Whistle
    public boolean enableWhistle = true;
    public int whistleRadiusBlocks = 20;
    public int whistleTargetRangeBlocks = 24;
    public int whistleCooldownTicks = 20;
    public int roundUpGroupRadiusBlocks = 8;

    // §7 Downed Pets & Revival
    public boolean enableDownedState = true;
    public double reviveHealthFraction = 0.5;
    public boolean downedRankPenalty = true;

    // §2/§3 shared
    public boolean enableInspection = true;

    // Derived membership sets, rebuilt by validate() from the id lists above (canonical
    // "namespace:path" form). Transient so GSON never serializes them.
    public transient Set<String> petsIncludeSet = Set.of();
    public transient Set<String> petsExcludeSet = Set.of();
    public transient Set<String> livestockIncludeSet = Set.of();
    public transient Set<String> livestockExcludeSet = Set.of();
    public transient Set<String> mountsIncludeSet = Set.of();
    public transient Set<String> mountsExcludeSet = Set.of();

    private static List<Integer> defaultVeterancyThresholds() {
        return new ArrayList<>(List.of(10, 30, 60));
    }

    /**
     * Loads the config and wires the lifecycle reset; called once from {@code onInitialize}. The
     * singleton drops on server stop so a file edited between two singleplayer sessions is
     * re-read at the next world start instead of waiting for {@code /instinct reload}.
     */
    public static void init() {
        get();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> invalidate());
    }

    /** Drops the in-memory singleton so the next {@link #get()} re-reads the file. */
    static void invalidate() {
        synchronized (InstinctConfig.class) {
            INSTANCE = null;
        }
    }

    public static InstinctConfig get() {
        InstinctConfig local = INSTANCE;
        if (local == null) {
            synchronized (InstinctConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    INSTANCE = local = load(configPath());
                }
            }
        }
        return local;
    }

    /**
     * Reloads the config from disk, swapping the active singleton, and returns the number of
     * leaf-level keys whose effective (post-validation) value changed — the count {@code /instinct
     * reload} reports.
     */
    public static int reload() {
        synchronized (InstinctConfig.class) {
            InstinctConfig previous = INSTANCE != null ? INSTANCE : new InstinctConfig();
            InstinctConfig next = load(configPath());
            int changed = ConfigDiff.countChangedKeys(
                    GSON.toJsonTree(previous).getAsJsonObject(),
                    GSON.toJsonTree(next).getAsJsonObject());
            INSTANCE = next;
            return changed;
        }
    }

    /**
     * A deep working copy for the Cloth Config screen, built through the same JSON round-trip
     * GSON uses on disk. It is already at {@link ConfigMigrator#CURRENT_VERSION}, so it must never
     * be re-migrated; the screen edits it and hands it to {@link #publish}.
     */
    public InstinctConfig copy() {
        return GSON.fromJson(GSON.toJson(this), InstinctConfig.class);
    }

    /**
     * Publishes an edited working copy as the active config — null-healed, clamped, persisted, then
     * atomically swapped in, so a concurrent reader only ever observes a fully-applied edit. The
     * Cloth screen's save seam; it goes through the same single {@code volatile} store as
     * {@link #reload()} (the two are op-only / single-session in practice and never race).
     */
    public static void publish(InstinctConfig next) {
        next.fillDefaults();
        next.validate();
        next.save(configPath());
        synchronized (InstinctConfig.class) {
            INSTANCE = next;
        }
    }

    static InstinctConfig load(Path path) {
        if (!Files.exists(path)) {
            Instinct.LOGGER.info("Config file missing; creating default at {}", path);
            InstinctConfig config = new InstinctConfig();
            config.validate();
            config.save(path);
            return config;
        }
        try {
            JsonElement element = JsonParser.parseString(Files.readString(path));
            if (element == null || !element.isJsonObject()) {
                Instinct.LOGGER.warn("Config file at {} was empty or not a JSON object; using defaults (existing file left untouched)", path);
                return validatedDefaults();
            }
            // Migrate the raw JSON tree before deserialize so a renamed key survives (a lenient
            // Gson deserialize would drop it). A file without configVersion is treated as v0.
            JsonObject raw = element.getAsJsonObject();
            boolean migrated = ConfigMigrator.migrate(raw);
            InstinctConfig config = GSON.fromJson(raw, InstinctConfig.class);
            if (config == null) {
                return validatedDefaults();
            }
            config.fillDefaults();
            config.validate();
            if (migrated) {
                config.save(path);
            }
            return config;
        } catch (JsonSyntaxException e) {
            Instinct.LOGGER.error("Failed to parse config at {}; using defaults (existing file left untouched)", path, e);
            return validatedDefaults();
        } catch (IOException e) {
            Instinct.LOGGER.error("Failed to read config at {}; using defaults", path, e);
            return validatedDefaults();
        }
    }

    private static InstinctConfig validatedDefaults() {
        InstinctConfig fallback = new InstinctConfig();
        fallback.fillDefaults();
        fallback.validate();
        return fallback;
    }

    void save(Path path) {
        // Write to a sibling temp file then atomically rename, so a crash or kill mid-write can
        // never leave a truncated/corrupt config in place. Fall back to a plain move where the
        // filesystem can't do an atomic rename, and clean up the orphan temp on failure.
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Instinct.LOGGER.error("Failed to save config to {}", path, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                Instinct.LOGGER.warn("Failed to clean up orphan temp config {}", tmp, cleanup);
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILENAME);
    }

    /** Null-heals every list a partial hand-edited file may have left absent. */
    private void fillDefaults() {
        if (petsInclude == null) petsInclude = new ArrayList<>();
        if (petsExclude == null) petsExclude = new ArrayList<>();
        if (livestockInclude == null) livestockInclude = new ArrayList<>();
        if (livestockExclude == null) livestockExclude = new ArrayList<>();
        if (mountsInclude == null) mountsInclude = new ArrayList<>();
        if (mountsExclude == null) mountsExclude = new ArrayList<>();
        if (veterancyThresholdDays == null || veterancyThresholdDays.isEmpty()) {
            veterancyThresholdDays = defaultVeterancyThresholds();
        }
    }

    /**
     * Warn-and-clamp every ranged field, logging each correction, and rebuild the derived
     * membership sets. Ranges come from {@code design/SPEC.md} §Configuration, mirrored on
     * {@code site/pages/config.json}. Runs on every population path.
     */
    public void validate() {
        creeperBerthBlocks = clampInt("creeperBerthBlocks", creeperBerthBlocks, 2, 8);
        teleportSuppressFallDistance = clampDouble("teleportSuppressFallDistance", teleportSuppressFallDistance, 0.5, 10.0);
        veterancyThresholdDays = sanitizeThresholds(veterancyThresholdDays);
        healthPerRank = clampDouble("healthPerRank", healthPerRank, 0.0, 20.0);
        damagePerRank = clampDouble("damagePerRank", damagePerRank, 0.0, 10.0);
        warningRadiusBlocks = clampInt("warningRadiusBlocks", warningRadiusBlocks, 8, 24);
        mentorRadiusBlocks = clampInt("mentorRadiusBlocks", mentorRadiusBlocks, 8, 32);
        mentorRateBonus = clampDouble("mentorRateBonus", mentorRateBonus, 0.0, 1.0);
        hayRadiusBlocks = clampInt("hayRadiusBlocks", hayRadiusBlocks, 2, 16);
        crowdingThreshold = clampInt("crowdingThreshold", crowdingThreshold, 4, 64);
        crowdingRadiusBlocks = clampInt("crowdingRadiusBlocks", crowdingRadiusBlocks, 4, 16);
        gradeUpgradeChance = clampDouble("gradeUpgradeChance", gradeUpgradeChance, 0.0, 1.0);
        gradeDowngradeChance = clampDouble("gradeDowngradeChance", gradeDowngradeChance, 0.0, 1.0);
        flockSpeedMultiplier = clampDouble("flockSpeedMultiplier", flockSpeedMultiplier, 1.0, 1.5);
        flockSpacingBlocks = clampDouble("flockSpacingBlocks", flockSpacingBlocks, 1.0, 4.0);
        herdingMaxPets = clampInt("herdingMaxPets", herdingMaxPets, 1, 4);
        troughRadiusBlocks = clampInt("troughRadiusBlocks", troughRadiusBlocks, 4, 24);
        troughFeedIntervalTicks = clampInt("troughFeedIntervalTicks", troughFeedIntervalTicks, 10, 200);
        troughPopulationCap = clampInt("troughPopulationCap", troughPopulationCap, 0, 64);
        whistleRadiusBlocks = clampInt("whistleRadiusBlocks", whistleRadiusBlocks, 8, 48);
        whistleTargetRangeBlocks = clampInt("whistleTargetRangeBlocks", whistleTargetRangeBlocks, 8, 64);
        whistleCooldownTicks = clampInt("whistleCooldownTicks", whistleCooldownTicks, 0, 100);
        roundUpGroupRadiusBlocks = clampInt("roundUpGroupRadiusBlocks", roundUpGroupRadiusBlocks, 4, 16);
        reviveHealthFraction = clampDouble("reviveHealthFraction", reviveHealthFraction, 0.1, 1.0);

        petsInclude = sanitizeEntityIds("petsInclude", petsInclude);
        petsExclude = sanitizeEntityIds("petsExclude", petsExclude);
        livestockInclude = sanitizeEntityIds("livestockInclude", livestockInclude);
        livestockExclude = sanitizeEntityIds("livestockExclude", livestockExclude);
        mountsInclude = sanitizeEntityIds("mountsInclude", mountsInclude);
        mountsExclude = sanitizeEntityIds("mountsExclude", mountsExclude);
        petsIncludeSet = Set.copyOf(petsInclude);
        petsExcludeSet = Set.copyOf(petsExclude);
        livestockIncludeSet = Set.copyOf(livestockInclude);
        livestockExcludeSet = Set.copyOf(livestockExclude);
        mountsIncludeSet = Set.copyOf(mountsInclude);
        mountsExcludeSet = Set.copyOf(mountsExclude);
    }

    /**
     * Rank thresholds: each clamped to 1–1000, sorted ascending, deduplicated, at most 3 entries;
     * an empty result falls back to the defaults. Every fix is logged.
     */
    private static List<Integer> sanitizeThresholds(List<Integer> raw) {
        Set<Integer> cleaned = new LinkedHashSet<>();
        for (Integer entry : raw) {
            if (entry == null) {
                Instinct.LOGGER.warn("veterancyThresholdDays: dropped null entry");
                continue;
            }
            int clamped = Math.clamp(entry, 1, 1000);
            if (clamped != entry) {
                Instinct.LOGGER.warn("veterancyThresholdDays: clamped {} to {}", entry, clamped);
            }
            if (!cleaned.add(clamped)) {
                Instinct.LOGGER.warn("veterancyThresholdDays: dropped duplicate {}", clamped);
            }
        }
        List<Integer> sorted = new ArrayList<>(cleaned);
        sorted.sort(null);
        if (sorted.size() > 3) {
            Instinct.LOGGER.warn("veterancyThresholdDays: truncated {} entries to 3", sorted.size());
            sorted = new ArrayList<>(sorted.subList(0, 3));
        }
        if (sorted.isEmpty()) {
            Instinct.LOGGER.warn("veterancyThresholdDays: no valid entries; using defaults");
            return defaultVeterancyThresholds();
        }
        return sorted;
    }

    /** Entity-id lists: invalid ids dropped with a warn, valid ids normalized to "ns:path" form. */
    private static List<String> sanitizeEntityIds(String name, List<String> raw) {
        List<String> cleaned = new ArrayList<>(raw.size());
        for (String entry : raw) {
            ResourceLocation id = entry == null ? null : ResourceLocation.tryParse(entry);
            if (id == null) {
                Instinct.LOGGER.warn("{}: dropped invalid entity id {}", name, entry);
                continue;
            }
            String canonical = id.toString();
            if (!cleaned.contains(canonical)) {
                cleaned.add(canonical);
            }
        }
        return cleaned;
    }

    private static int clampInt(String name, int value, int min, int max) {
        if (value < min) {
            Instinct.LOGGER.warn("clamped {} from {} to {}", name, value, min);
            return min;
        }
        if (value > max) {
            Instinct.LOGGER.warn("clamped {} from {} to {}", name, value, max);
            return max;
        }
        return value;
    }

    private static double clampDouble(String name, double value, double min, double max) {
        if (!(value >= min)) { // also catches NaN
            Instinct.LOGGER.warn("clamped {} from {} to {}", name, value, min);
            return min;
        }
        if (value > max) {
            Instinct.LOGGER.warn("clamped {} from {} to {}", name, value, max);
            return max;
        }
        return value;
    }
}
