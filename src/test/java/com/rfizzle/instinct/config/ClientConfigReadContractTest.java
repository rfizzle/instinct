package com.rfizzle.instinct.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tripwire on Instinct's one deliberate config gap: it is the only Concord member with no
 * server→client config sync.
 *
 * <p>That is currently correct rather than merely tolerated. Every key in {@link InstinctConfig} is
 * server-authoritative, and nothing on the client reads one — Instinct has no HUD and no client
 * rendering toggle, and inspection lines are composed server-side and pushed as chat. The client's
 * single touch of the config class is the Cloth Config screen, which edits a <em>local working copy</em>
 * and saves it to the player's own file; that is a client editing its own config, not a client reading
 * a server rule. A {@code ConfigSyncPayload} today would carry values no client consumes.
 *
 * <p>What is <em>not</em> safe is the first client-visible toggle. The moment client code reads a
 * gameplay rule from this class, an unsynced client silently uses its own value where the server's must
 * win — the sync-precedence rule in the {@code mc-config} skill — and nothing else in the build would
 * say so. This test says so: it pins the exact set of client files allowed to reference
 * {@link InstinctConfig}, so adding a second one fails the build with the reason attached.
 *
 * <p>Widening {@link #ALLOWED_CLIENT_READERS} is the wrong fix. The right one is to implement the
 * canonical sync first, then add the reader.
 */
class ClientConfigReadContractTest {

    private static final Path CLIENT_SOURCES = Path.of("src/client/java");

    /** The class under guard, matched on its simple name so an import or a fully-qualified use both count. */
    private static final String CONFIG_CLASS = "InstinctConfig";

    /**
     * The Cloth Config screen builder: a client editing its own config file, which is not a
     * server-authoritative read and needs no sync. Nothing else may appear here without a sync landing
     * first.
     */
    private static final Set<String> ALLOWED_CLIENT_READERS = Set.of(
            "com/rfizzle/instinct/compat/modmenu/ClothConfigScreenBuilder.java");

    private static Set<String> clientFilesReferencingConfig() {
        TreeSet<String> found = new TreeSet<>();
        try (Stream<Path> tree = Files.walk(CLIENT_SOURCES)) {
            tree.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    if (Files.readString(p, StandardCharsets.UTF_8).contains(CONFIG_CLASS)) {
                        found.add(CLIENT_SOURCES.relativize(p).toString().replace(java.io.File.separatorChar, '/'));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new AssertionError("could not walk " + CLIENT_SOURCES, e);
        } catch (UncheckedIOException e) {
            throw new AssertionError("could not read a source file under " + CLIENT_SOURCES, e.getCause());
        }
        return found;
    }

    /** Guards the guard: a walk that reached nothing would pass the assertion below while proving nothing. */
    @Test
    void theWalkReachesTheClientSourceTree() {
        assertTrue(Files.isDirectory(CLIENT_SOURCES),
                CLIENT_SOURCES + " is missing, so this guard is scanning nothing");
        long javaFiles;
        try (Stream<Path> tree = Files.walk(CLIENT_SOURCES)) {
            javaFiles = tree.filter(p -> p.toString().endsWith(".java")).count();
        } catch (IOException e) {
            throw new AssertionError("could not walk " + CLIENT_SOURCES, e);
        }
        assertTrue(javaFiles > 0, "found no client sources under " + CLIENT_SOURCES);
    }

    @Test
    void onlyTheClothScreenReadsTheConfigFromTheClient() {
        assertEquals(ALLOWED_CLIENT_READERS, clientFilesReferencingConfig(),
                "Instinct ships no server->client config sync, which is only safe while no client code "
                        + "reads a server-authoritative value. A new client reader means an unsynced "
                        + "client silently using its own value where the server's must win (mc-config, "
                        + "sync precedence). Implement the canonical ConfigSyncPayload — one "
                        + "length-bounded serialized string under instinct:config_sync, clamped inside "
                        + "client.execute(...), setServerConfig(null) on disconnect — and then widen "
                        + "this set. Do not widen it first.");
    }
}
