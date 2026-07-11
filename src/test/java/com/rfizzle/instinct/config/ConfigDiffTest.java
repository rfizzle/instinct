package com.rfizzle.instinct.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigDiffTest {

    private static JsonObject json(String content) {
        return JsonParser.parseString(content).getAsJsonObject();
    }

    @Test
    void identicalTreesCountZero() {
        JsonObject tree = json("""
                { "a": 1, "b": true, "list": [1, 2], "nested": { "c": "x" } }
                """);
        assertEquals(0, ConfigDiff.countChangedKeys(tree, tree.deepCopy()));
    }

    @Test
    void eachChangedLeafCountsOnce() {
        JsonObject before = json("""
                { "a": 1, "b": true, "c": 2.5 }
                """);
        JsonObject after = json("""
                { "a": 2, "b": false, "c": 2.5 }
                """);
        assertEquals(2, ConfigDiff.countChangedKeys(before, after));
    }

    @Test
    void changedListCountsAsOneKey() {
        JsonObject before = json("""
                { "thresholds": [10, 30, 60] }
                """);
        JsonObject after = json("""
                { "thresholds": [5, 30, 60, 90] }
                """);
        assertEquals(1, ConfigDiff.countChangedKeys(before, after));
    }

    @Test
    void nestedLeavesCountIndividually() {
        JsonObject before = json("""
                { "outer": { "a": 1, "b": 2 } }
                """);
        JsonObject after = json("""
                { "outer": { "a": 9, "b": 8 } }
                """);
        assertEquals(2, ConfigDiff.countChangedKeys(before, after));
    }

    @Test
    void addedAndRemovedKeysCount() {
        JsonObject before = json("""
                { "a": 1, "removed": 5 }
                """);
        JsonObject after = json("""
                { "a": 1, "added": 7 }
                """);
        assertEquals(2, ConfigDiff.countChangedKeys(before, after));
    }
}
