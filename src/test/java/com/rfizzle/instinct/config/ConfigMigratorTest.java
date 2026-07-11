package com.rfizzle.instinct.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    @Test
    void unversionedFileMigratesToCurrentAndKeepsItsValues() {
        JsonObject json = JsonParser.parseString("""
                { "creeperBerthBlocks": 6 }
                """).getAsJsonObject();

        assertTrue(ConfigMigrator.migrate(json), "a v0 file should report migration");
        assertEquals(ConfigMigrator.CURRENT_VERSION, json.get("configVersion").getAsInt());
        assertEquals(6, json.get("creeperBerthBlocks").getAsInt(), "existing values carry forward");
    }

    @Test
    void currentVersionPassesThroughUntouched() {
        JsonObject json = JsonParser.parseString("""
                { "configVersion": %d, "creeperBerthBlocks": 6 }
                """.formatted(ConfigMigrator.CURRENT_VERSION)).getAsJsonObject();
        JsonObject before = json.deepCopy();

        assertFalse(ConfigMigrator.migrate(json), "a current file should not migrate");
        assertEquals(before, json);
    }

    @Test
    void migrationIsIdempotent() {
        JsonObject json = new JsonObject();
        assertTrue(ConfigMigrator.migrate(json));
        JsonObject afterFirst = json.deepCopy();

        assertFalse(ConfigMigrator.migrate(json), "second migration should be a no-op");
        assertEquals(afterFirst, json);
    }

    @Test
    void futureVersionPassesThrough() {
        JsonObject json = JsonParser.parseString("""
                { "configVersion": %d }
                """.formatted(ConfigMigrator.CURRENT_VERSION + 5)).getAsJsonObject();

        assertFalse(ConfigMigrator.migrate(json), "a newer file is never downgraded");
        assertEquals(ConfigMigrator.CURRENT_VERSION + 5, json.get("configVersion").getAsInt());
    }
}
