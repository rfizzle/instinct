package com.rfizzle.instinct;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the {@code runGametest} wiring in {@code build.gradle}.
 *
 * <p>Attaching the JaCoCo agent gives {@code runGametest} a declared {@code @OutputFile} — the
 * agent's exec file — which turns an otherwise output-less {@code JavaExec} into an
 * up-to-date-checkable task. Without an explicit {@code outputs.upToDateWhen { false }} pin, a
 * repeat sweep reports {@code UP-TO-DATE} and {@code BUILD SUCCESSFUL} having run no suites at
 * all, re-emitting the previous coverage number and leaving {@code build/junit-gametest.xml}
 * stale behind the green result.
 *
 * <p>That regression is worth a test precisely because it does not announce itself: it surfaces
 * as a passing build, not a failing one, so nothing else in the pipeline would catch the pin
 * being dropped by a reformat or a template sync.
 */
class BuildWiringContractTest {

    private static final Path BUILD_SCRIPT = Path.of("build.gradle");
    private static final String BLOCK_HEADER = "tasks.named('runGametest')";

    /**
     * Returns the body of the {@code tasks.named('runGametest')} configuration block, with line
     * comments removed and whitespace squeezed out.
     *
     * <p>Scoping to the block matters: a bare file-wide substring search would be satisfied by a
     * line pasted anywhere in the script, including somewhere inert. Dropping comments matters for
     * the same reason — prose describing the pin must not stand in for the pin itself.
     */
    private static String runGametestBlock() {
        String script;
        try {
            script = Files.readString(BUILD_SCRIPT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + BUILD_SCRIPT, e);
        }

        int header = script.indexOf(BLOCK_HEADER);
        if (header < 0) {
            return fail(BUILD_SCRIPT + " no longer configures " + BLOCK_HEADER
                    + " — the JaCoCo agent attachment and the always-execute pin have moved or been lost");
        }
        int open = script.indexOf('{', header + BLOCK_HEADER.length());
        if (open < 0) {
            return fail(BLOCK_HEADER + " in " + BUILD_SCRIPT + " has no opening brace");
        }

        StringBuilder body = new StringBuilder();
        int depth = 0;
        boolean inLineComment = false;
        for (int i = open; i < script.length(); i++) {
            char c = script.charAt(i);
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (c == '/' && i + 1 < script.length() && script.charAt(i + 1) == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return body.toString().replaceAll("\\s+", "");
                }
            }
            body.append(c);
        }
        return fail(BLOCK_HEADER + " in " + BUILD_SCRIPT
                + " has an unbalanced brace — could not extract the block to check its wiring");
    }

    @Test
    void gametestSweepIsPinnedToAlwaysExecute() {
        assertTrue(runGametestBlock().contains("outputs.upToDateWhen{false}"),
                BLOCK_HEADER + " lost its `outputs.upToDateWhen { false }` pin. The JaCoCo agent's "
                        + "exec file is a declared output, so without the pin a repeat "
                        + "`./gradlew runGametest` reports UP-TO-DATE, runs zero suites, and "
                        + "re-emits the previous coverage number as though it were fresh.");
    }

    @Test
    void gametestSweepStillAttachesTheJacocoAgent() {
        assertTrue(runGametestBlock().contains("jacoco.applyTo("),
                BLOCK_HEADER + " no longer attaches the JaCoCo agent. Without it the gametest "
                        + "sweep writes no exec file, and jacocoMergedReport silently degrades to "
                        + "unit-test-only coverage under the `merged` label.");
    }
}
