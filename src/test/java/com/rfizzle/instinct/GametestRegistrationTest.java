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
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the gametest-manifest split and gametest registration in both directions.
 *
 * <p>The split: {@code fabric-gametest} entrypoints must live only in the gametest source set's
 * {@code instinct-gametest} manifest, never in the shipped {@code instinct} manifest. Declaring them
 * in the main manifest lets Fabric's ungated gametest initializer try to load classes absent from the
 * {@code server}/{@code datagen} classpaths, crashing {@code runServer} — one of the two regressions
 * this test exists to catch.
 *
 * <p>Registration: it fails silently in <em>both</em> directions. An unregistered
 * {@code FabricGameTest} never runs and never warns, so a suite can rot for months while CI stays
 * green; a stale entrypoint naming a deleted class crashes the run at startup.
 *
 * <p>The gametest source set is not on the test classpath, so its classes cannot be enumerated
 * reflectively — the guard reads the source tree instead, walking it recursively so suites in
 * subpackages are not missed. A suite is identified by the <em>interface it implements</em>, not a
 * filename suffix and not an annotation regex: the source set also holds helpers ({@code MockPlayers},
 * {@code PetSpawns}, {@code TestFloors}), so a suffix-free match would flag those as unregistered, a
 * suffix-only match would let a suite named {@code FooTests} slip past both sides of the comparison at
 * once, and an unanchored {@code @GameTest} regex matches the annotation inside a comment or a string.
 * {@code implements FabricGameTest} is the same predicate the loader itself uses.
 *
 * <p>The task inputs this reads are declared in {@code build.gradle} — Gradle sees no dependency
 * between the test task and a source tree it never compiles, so without that block this check would
 * report {@code UP-TO-DATE} exactly when registration had drifted.
 */
class GametestRegistrationTest {

    // Read both manifests straight from source, not the classpath: {@code /fabric.mod.json} is not a
    // unique classpath resource (fabric-loader ships its own at the jar root), so a classpath lookup
    // can return the wrong manifest. The only processResources transform on the shipped manifest is
    // {@code ${version}} expansion, which touches none of the keys asserted here.
    private static final Path MAIN_SOURCE = Path.of("src/main/resources/fabric.mod.json");
    private static final Path GAMETEST_SOURCE = Path.of("src/gametest/resources/fabric.mod.json");
    private static final Path GAMETEST_JAVA_ROOT = Path.of("src/gametest/java");

    /** Matches a class's {@code implements} clause naming FabricGameTest. */
    private static final Pattern IMPLEMENTS_FABRIC_GAMETEST =
            Pattern.compile("implements\\s+[^{]*\\bFabricGameTest\\b");

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

    private static JsonObject depends(JsonObject manifest) {
        assertTrue(manifest.has("depends"),
                manifest.get("id").getAsString() + " manifest lost its depends block entirely");
        return manifest.getAsJsonObject("depends");
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
        assertTrue(depends(gametest).has("instinct"),
                "gametest manifest must depend on the main instinct mod");
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
        assertEquals(Set.of("instinct"), depends(gametestManifest()).keySet(),
                "the gametest manifest depends only on the main mod; the loader/Minecraft/Java/"
                        + "fabric-api floors are enforced transitively through it");
    }

    /**
     * The other half of {@link #gametestManifestRestatesNoTransitiveFloors()}: the floors the
     * gametest manifest inherits have to actually be declared somewhere, and the shipped manifest is
     * that somewhere. Without this, dropping a floor from the main manifest leaves the gametest mod
     * silently unguarded while every other assertion here still reads green.
     */
    @Test
    void mainManifestOwnsTheDependencyFloors() {
        assertEquals(Set.of("fabricloader", "minecraft", "java", "fabric-api"),
                depends(mainManifest()).keySet(),
                "the shipped manifest declares the loader/Minecraft/Java/fabric-api floors that the "
                        + "gametest manifest inherits transitively");
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

    private static Set<String> declaredEntrypoints() {
        JsonArray list = entrypoints(gametestManifest()).getAsJsonArray("fabric-gametest");
        TreeSet<String> fqcns = new TreeSet<>();
        for (JsonElement element : list) {
            fqcns.add(element.getAsString());
        }
        return fqcns;
    }

    /** Fully-qualified names of every class under the gametest tree, mapped to its source text. */
    private static TreeMap<String, String> gametestSources() {
        TreeMap<String, String> sources = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(GAMETEST_JAVA_ROOT)) {
            tree.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String relative = GAMETEST_JAVA_ROOT.relativize(p).toString();
                String className = relative.substring(0, relative.length() - ".java".length())
                        .replace(java.io.File.separatorChar, '.');
                try {
                    sources.put(className, Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new AssertionError("could not walk " + GAMETEST_JAVA_ROOT, e);
        } catch (UncheckedIOException e) {
            throw new AssertionError("could not read a source file under " + GAMETEST_JAVA_ROOT, e.getCause());
        }
        return sources;
    }

    private static boolean isSuite(String source) {
        return IMPLEMENTS_FABRIC_GAMETEST.matcher(source).find();
    }

    private static Set<String> suitesOnDisk() {
        TreeSet<String> suites = new TreeSet<>();
        gametestSources().forEach((className, source) -> {
            if (isSuite(source)) {
                suites.add(className);
            }
        });
        return suites;
    }

    @Test
    void everyRegisteredEntrypointIsASuiteOnDisk() {
        TreeSet<String> dangling = new TreeSet<>(declaredEntrypoints());
        dangling.removeAll(suitesOnDisk());
        assertTrue(dangling.isEmpty(),
                GAMETEST_SOURCE + " declares entrypoints that are not FabricGameTest classes on disk"
                        + " — the gametest run will fail to load them: " + dangling);
    }

    /**
     * The reverse: every {@code FabricGameTest} implementor must be listed in the manifest, so a newly
     * added suite can never silently go unregistered and unrun.
     */
    @Test
    void everySuiteOnDiskIsRegistered() {
        TreeSet<String> unregistered = new TreeSet<>(suitesOnDisk());
        unregistered.removeAll(declaredEntrypoints());
        assertTrue(unregistered.isEmpty(),
                "gametest suites exist but are not declared in " + GAMETEST_SOURCE
                        + " — they will silently never run: " + unregistered);
    }

    @Test
    void suiteNamingConventionHoldsInBothDirections() {
        // Matching suites by interface closes the "helper flagged as unregistered" hole; enforcing the
        // name closes the other one, where a suite called FooTests goes missing from the source-tree
        // scan and the manifest at the same time and the guards above stay green.
        TreeSet<String> misnamedSuites = new TreeSet<>();
        TreeSet<String> impostors = new TreeSet<>();
        gametestSources().forEach((className, source) -> {
            boolean suite = isSuite(source);
            boolean named = className.endsWith("GameTest");
            if (suite && !named) {
                misnamedSuites.add(className);
            } else if (!suite && named) {
                impostors.add(className);
            }
        });
        assertTrue(misnamedSuites.isEmpty(),
                "FabricGameTest implementors must be named *GameTest: " + misnamedSuites);
        assertTrue(impostors.isEmpty(),
                "classes named *GameTest must implement FabricGameTest: " + impostors);
    }
}
