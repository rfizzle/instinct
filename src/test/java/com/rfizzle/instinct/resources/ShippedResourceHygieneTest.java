package com.rfizzle.instinct.resources;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the boundary between what ships and what only ever serves a test.
 *
 * <p>Gametest fixtures — structure templates and bespoke loot tables — resolve through the merged
 * {@code ResourceManager} by namespace, so they work from any loaded mod's resource root. That means
 * they belong in the gametest source set, whose manifest declares a separate {@code instinct-gametest}
 * mod that never enters the jar.
 *
 * <p>Keeping them out of the shipped roots is worth a guard on two counts. A loot table in the
 * {@code instinct} namespace is eagerly parsed and validated on every datapack reload on every server,
 * including the integrated server behind a singleplayer world, purely to serve a test. A structure
 * template is cheaper — it loads on demand rather than on reload — but it is still listed for
 * {@code /place template} autocomplete, so it surfaces test fixtures to operators.
 *
 * <p>This guard is the one that would have caught {@code data/instinct/gametest/structure/drive_lane.snbt}
 * shipping in the jar, where it sat unnoticed while every other check read green.
 *
 * <p>These assertions read the test classpath rather than the source tree, because the classpath is
 * what the jar is built from — it is the shipped artifact under test. That also means they cover
 * {@code src/main/generated}, which {@code build.gradle} folds into main's resource roots.
 */
class ShippedResourceHygieneTest {

    /**
     * A shipped resource root, located by anchoring on a file known to live at its top level.
     *
     * @param anchor  classpath path of the anchor file
     * @param markers entries that must exist directly under the resolved root, so that an anchor which
     *                moves into a subdirectory fails loudly instead of silently narrowing the walk to
     *                that subdirectory
     */
    private record ShippedRoot(String anchor, List<String> markers) {
    }

    /**
     * Instinct's client source set carries no resources of its own — {@code src/client} is Java only —
     * so {@code main} (with {@code src/main/generated} merged into it by {@code processResources}) is
     * the single root that reaches the jar. If a {@code src/client/resources} tree is ever added, it
     * needs its own entry here, or this guard walks past it.
     */
    private static final List<ShippedRoot> SHIPPED_ROOTS = List.of(
            new ShippedRoot("/instinct.mixins.json",
                    List.of("fabric.mod.json", "instinct.accesswidener", "data/instinct", "assets/instinct")));

    /** Path segment that marks a file as existing only to serve the gametest suite. */
    private static final String TEST_ONLY_SEGMENT = "gametest";

    /**
     * Structure templates are a gametest-only format here — Instinct ships no structures of its own,
     * and its two blocks are placed by the player — so the extension is disqualifying wherever it
     * appears, not just under a {@code gametest/} directory.
     */
    private static final String TEMPLATE_EXTENSION = ".snbt";

    private static final List<String> KNOWN_TEST_FIXTURES = List.of(
            "/data/instinct/gametest/structure/drive_lane.snbt");

    /**
     * The fixtures with a history of drifting into the shipped roots, named explicitly so a regression
     * points straight at the offending file rather than at a directory walk.
     */
    @Test
    void knownGametestFixtures_areNotOnTheShippedClasspath() {
        // Resolving the roots first keeps this from passing vacuously against an empty classpath.
        shippedResourceRoots();

        for (String fixture : KNOWN_TEST_FIXTURES) {
            assertNull(ShippedResourceHygieneTest.class.getResource(fixture),
                    "test-only fixture is on the shipped classpath and will land in the jar: "
                            + fixture + " — it belongs in src/gametest/resources/");
        }
    }

    /** The general form of the same rule, so a fixture added under a new name is caught too. */
    @Test
    void noGametestPathSegment_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> hasSegment(relative, TEST_ONLY_SEGMENT));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain gametest-only files, but found "
                        + offenders.size() + ": " + offenders
                        + " — move them to src/gametest/resources/, which is on the "
                        + "runGametest classpath but never enters the jar");
    }

    /** Catches a template parked outside a {@code gametest/} directory, which the sweep above misses. */
    @Test
    void noStructureTemplates_anywhereInShippedResources() {
        List<String> offenders = scanShippedRoots(
                relative -> relative.getFileName().toString().endsWith(TEMPLATE_EXTENSION));

        assertTrue(offenders.isEmpty(),
                "shipped resources must not contain structure templates, but found "
                        + offenders.size() + ": " + offenders
                        + " — templates serve the gametest suite only and belong in "
                        + "src/gametest/resources/");
    }

    /**
     * Pins the walk against files that are genuinely shipped, so a root that resolved to an empty or
     * wrong directory cannot let the sweeps above pass by finding nothing at all. Both a hand-authored
     * resource and a datagen output are named, because the two arrive from different source roots and
     * only {@code processResources} merges them.
     */
    @Test
    void theScanActuallyReachesTheShippedTree() {
        List<String> pinned = scanShippedRoots(relative ->
                relative.endsWith(Path.of("lang", "en_us.json"))
                        || relative.endsWith(Path.of("loot_table", "blocks", "kennel_post.json")));

        assertTrue(pinned.contains(Path.of("assets", "instinct", "lang", "en_us.json").toString()),
                "the shipped-resource walk did not find assets/instinct/lang/en_us.json, so the "
                        + "hygiene sweeps above are scanning the wrong tree and prove nothing; found: "
                        + pinned);
        assertTrue(pinned.contains(
                        Path.of("data", "instinct", "loot_table", "blocks", "kennel_post.json").toString()),
                "the shipped-resource walk did not find the datagen output merged in from "
                        + "src/main/generated, so it would miss a fixture parked there; found: " + pinned);
    }

    /** Walks every shipped root, collecting root-relative paths of files matching the rule. */
    private static List<String> scanShippedRoots(Predicate<Path> offending) {
        List<String> offenders = new ArrayList<>();

        for (Path root : shippedResourceRoots()) {
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .filter(offending)
                        .map(Path::toString)
                        .forEach(offenders::add);
            } catch (Exception e) {
                fail("could not walk shipped resource root " + root, e);
            }
        }

        offenders.sort(String::compareTo);
        return offenders;
    }

    private static boolean hasSegment(Path relative, String segment) {
        for (Path part : relative) {
            if (segment.equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves every directory the shipped resources are processed into, failing rather than returning
     * an empty list — a guard that walks nothing passes while enforcing nothing.
     */
    private static List<Path> shippedResourceRoots() {
        List<Path> roots = new ArrayList<>();

        for (ShippedRoot shipped : SHIPPED_ROOTS) {
            URL anchor = ShippedResourceHygieneTest.class.getResource(shipped.anchor());
            if (anchor == null) {
                fail("could not locate " + shipped.anchor() + " on the test classpath — the "
                        + "anchor this guard uses to find a shipped resource root has moved");
            }
            if (!"file".equals(anchor.getProtocol())) {
                fail("expected the shipped resource root to be a directory on the test "
                        + "classpath, but " + shipped.anchor() + " resolved to " + anchor);
            }

            Path root;
            try {
                root = Path.of(anchor.toURI()).getParent();
            } catch (Exception e) {
                return fail("could not resolve a filesystem path for " + anchor, e);
            }

            for (String marker : shipped.markers()) {
                assertTrue(Files.exists(root.resolve(marker)),
                        "resolved shipped resource root " + root + " is missing expected entry '"
                                + marker + "' — the anchor " + shipped.anchor() + " has most "
                                + "likely moved into a subdirectory, which would silently narrow "
                                + "this guard's coverage");
            }

            roots.add(root);
        }

        return roots;
    }
}
