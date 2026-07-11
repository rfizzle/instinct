package com.rfizzle.instinct.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Leaf-level diff between two config JSON trees, backing the changed-key count that
 * {@code /instinct reload} reports. Nested objects are flattened to dot-separated paths; arrays
 * compare as one leaf value (a reordered or extended list counts as one changed key).
 */
public final class ConfigDiff {

    private ConfigDiff() {
    }

    /** Counts leaf paths whose values differ, including paths present in only one tree. */
    public static int countChangedKeys(JsonObject before, JsonObject after) {
        Map<String, JsonElement> flatBefore = flatten(before);
        Map<String, JsonElement> flatAfter = flatten(after);
        Set<String> paths = new HashSet<>(flatBefore.keySet());
        paths.addAll(flatAfter.keySet());
        int changed = 0;
        for (String path : paths) {
            if (!Objects.equals(flatBefore.get(path), flatAfter.get(path))) {
                changed++;
            }
        }
        return changed;
    }

    private static Map<String, JsonElement> flatten(JsonObject object) {
        Map<String, JsonElement> flat = new HashMap<>();
        flattenInto(flat, "", object);
        return flat;
    }

    private static void flattenInto(Map<String, JsonElement> flat, String prefix, JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) {
                flattenInto(flat, path, value.getAsJsonObject());
            } else {
                flat.put(path, value);
            }
        }
    }
}
