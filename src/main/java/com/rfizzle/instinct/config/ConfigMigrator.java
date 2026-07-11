package com.rfizzle.instinct.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rfizzle.instinct.Instinct;

/**
 * Versioned raw-JSON schema migration for {@link InstinctConfig}. Migrations run on the raw
 * {@link JsonObject} before Gson deserializes it, indexed by from-version, so a renamed or
 * restructured field is carried forward rather than silently dropped by a lenient deserialize.
 *
 * <p>To add a migration: bump {@link #CURRENT_VERSION}, keep the {@code configVersion} default in
 * {@link InstinctConfig} in lockstep, append the lambda (never reorder), and add a
 * {@code ConfigMigratorTest} case (legacy→new, idempotency, already-current passthrough).
 *
 * <p>Migration runs only on the file-load path — never on a config built from an in-memory JSON
 * tree, which is already at the current version.
 */
final class ConfigMigrator {
    static final int CURRENT_VERSION = 1;

    @FunctionalInterface
    interface Migration {
        void apply(JsonObject json);
    }

    // Index i = the v(i) → v(i+1) transition. Append only; never reorder.
    private static final Migration[] MIGRATIONS = {
            json -> {}, // v0 → v1: baseline tag — stamps configVersion onto a pre-versioned file
    };

    private ConfigMigrator() {
    }

    static boolean migrate(JsonObject json) {
        int version = readVersion(json);
        if (version >= CURRENT_VERSION) {
            return false;
        }
        boolean changed = false;
        for (int i = version; i < CURRENT_VERSION && i < MIGRATIONS.length; i++) {
            try {
                MIGRATIONS[i].apply(json);
                Instinct.LOGGER.info("Migrated config from v{} to v{}", i, i + 1);
                changed = true;
            } catch (Exception e) {
                Instinct.LOGGER.warn("Config migration v{} to v{} failed; skipping: {}", i, i + 1, e.getMessage());
            }
        }
        if (changed) {
            json.addProperty("configVersion", CURRENT_VERSION);
        }
        return changed;
    }

    /** The file's schema version; a missing or non-numeric {@code configVersion} reads as 0. */
    private static int readVersion(JsonObject json) {
        JsonElement version = json.get("configVersion");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return version.getAsInt();
    }
}
