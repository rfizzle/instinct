package com.rfizzle.instinct;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the gametest-manifest split: the {@code fabric-gametest} entrypoints must live only in the
 * gametest source set's {@code instinct-gametest} manifest, never in the shipped {@code instinct}
 * manifest. Declaring them in the main manifest lets Fabric's ungated gametest initializer try to
 * load classes absent from the {@code server}/{@code datagen} classpaths, crashing {@code runServer}
 * — the regression this test exists to catch.
 */
class ManifestContractTest {

    // Read both manifests straight from source, not the classpath: {@code /fabric.mod.json} is not a
    // unique classpath resource (fabric-loader ships its own at the jar root), so a classpath lookup
    // can return the wrong manifest. The only processResources transform on the shipped manifest is
    // {@code ${version}} expansion, which touches none of the keys asserted here.
    private static final Path MAIN_SOURCE = Path.of("src/main/resources/fabric.mod.json");
    private static final Path GAMETEST_SOURCE = Path.of("src/gametest/resources/fabric.mod.json");
    private static final Path GAMETEST_JAVA_ROOT = Path.of("src/gametest/java");

    private static JsonObject manifest(Path source) {
        try {
            return JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load " + source, e);
        }
    }

    private static JsonObject mainManifest() {
        return manifest(MAIN_SOURCE);
    }

    private static JsonObject gametestManifest() {
        return manifest(GAMETEST_SOURCE);
    }

    private static JsonObject entrypoints(JsonObject manifest) {
        return manifest.has("entrypoints") ? manifest.getAsJsonObject("entrypoints") : new JsonObject();
    }

    @Test
    void mainManifestDeclaresNoGametestEntrypoints() {
        assertFalse(entrypoints(mainManifest()).has("fabric-gametest"),
                "the shipped instinct manifest must not declare fabric-gametest entrypoints");
    }

    @Test
    void mainManifestKeepsMixinsAndAccessWidener() {
        JsonObject main = mainManifest();
        assertTrue(main.has("mixins"), "main manifest lost its mixins declaration");
        assertTrue(main.has("accessWidener"), "main manifest lost its accessWidener declaration");
    }

    @Test
    void gametestManifestIsADistinctModDependingOnInstinct() {
        JsonObject gametest = gametestManifest();
        assertEquals("instinct-gametest", gametest.get("id").getAsString());
        JsonObject depends = gametest.getAsJsonObject("depends");
        assertTrue(depends.has("instinct"), "gametest manifest must depend on the main instinct mod");
    }

    /**
     * The loader, Minecraft, Java, and Fabric API floors belong only to the shipped manifest.
     * {@code instinct-gametest} cannot load unless {@code instinct} did, and {@code instinct} cannot
     * load without them, so restating the floors here only adds a second place to update on every
     * Minecraft or Fabric API bump — where a missed edit surfaces as a confusing gametest load error
     * rather than an obvious version mismatch.
     */
    @Test
    void gametestManifestRestatesNoTransitiveFloors() {
        JsonObject depends = gametestManifest().getAsJsonObject("depends");
        assertEquals(Set.of("instinct"), depends.keySet(),
                "the gametest manifest depends only on the main mod; the loader/Minecraft/Java/"
                        + "fabric-api floors are enforced transitively through it");
    }

    @Test
    void gametestManifestDeclaresTheGametestEntrypoints() {
        JsonObject entrypoints = entrypoints(gametestManifest());
        assertTrue(entrypoints.has("fabric-gametest"),
                "gametest manifest must declare the fabric-gametest entrypoints");
        assertFalse(entrypoints.getAsJsonArray("fabric-gametest").isEmpty(),
                "gametest manifest declares an empty fabric-gametest list");
    }

    @Test
    void gametestManifestOwnsNoMixinsOrAccessWidener() {
        JsonObject gametest = gametestManifest();
        assertFalse(gametest.has("mixins"),
                "mixins belong only to the main manifest; a second declaration risks a Loom conflict");
        assertFalse(gametest.has("accessWidener"),
                "the accessWidener belongs only to the main manifest");
    }

    private static List<String> gametestEntrypoints() {
        JsonArray list = entrypoints(gametestManifest()).getAsJsonArray("fabric-gametest");
        List<String> fqcns = new ArrayList<>();
        for (JsonElement element : list) {
            fqcns.add(element.getAsString());
        }
        return fqcns;
    }

    @Test
    void everyGametestEntrypointClassExists() {
        List<String> missing = new ArrayList<>();
        for (String fqcn : gametestEntrypoints()) {
            Path source = GAMETEST_JAVA_ROOT.resolve(fqcn.replace('.', '/') + ".java");
            if (!Files.exists(source)) {
                missing.add(fqcn);
            }
        }
        assertTrue(missing.isEmpty(), "fabric-gametest entrypoints without a source class: " + missing);
    }

    /**
     * The reverse of {@link #everyGametestEntrypointClassExists()}: every {@code *GameTest} source
     * class must be listed in the manifest, so a newly added gametest can never silently go
     * unregistered and unrun.
     */
    @Test
    void everyGametestSourceClassIsRegistered() {
        List<String> registered = gametestEntrypoints();
        List<String> unregistered = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(GAMETEST_JAVA_ROOT)) {
            sources.filter(p -> p.getFileName().toString().endsWith("GameTest.java"))
                    .forEach(p -> {
                        String fqcn = GAMETEST_JAVA_ROOT.relativize(p).toString()
                                .replace(java.io.File.separatorChar, '.')
                                .replaceAll("\\.java$", "");
                        if (!registered.contains(fqcn)) {
                            unregistered.add(fqcn);
                        }
                    });
        } catch (IOException e) {
            throw new AssertionError("could not walk " + GAMETEST_JAVA_ROOT, e);
        }
        assertTrue(unregistered.isEmpty(),
                "gametest classes missing from the manifest's fabric-gametest list: " + unregistered);
    }
}
