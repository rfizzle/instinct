package com.rfizzle.instinct;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** How the block is named in failure messages. */
    private static final String BLOCK_LABEL = "the runGametest configuration block";

    /**
     * Matches the opening of the configuration block, through its brace.
     *
     * <p>Anchored to the start of a line and required to reach a {@code {}, so it can only match the
     * configuration block — {@code runGametest} is also named mid-line by {@code jacocoMergedReport}'s
     * {@code mustRunAfter}, and latching onto that would walk forward into an unrelated closure and
     * report the pin missing while it is present.
     *
     * <p>Both wiring forms are accepted: the eager {@code tasks.named('runGametest') &#123;} and the lazy
     * {@code tasks.matching &#123; it.name == 'runGametest' &#125;.configureEach &#123;}. This test guards what the
     * block <em>contains</em>, not which lookup found the task, and pinning one form would turn the
     * documented migration between them into a spurious failure. Either quote style is accepted for the
     * same reason a cosmetic reformat must not read as drift.
     */
    private static final Pattern BLOCK_HEADER = Pattern.compile(
            "(?m)^tasks\\.(?:named\\(\\s*['\"]runGametest['\"]\\s*\\)"
                    + "|matching\\s*\\{\\s*it\\.name\\s*==\\s*['\"]runGametest['\"]\\s*}\\s*\\.configureEach)"
                    + "\\s*\\{");

    /** The pin, after whitespace is squeezed out — with or without the optional wrapping parens. */
    private static final Pattern PIN = Pattern.compile("outputs\\.upToDateWhen\\(?\\{false}\\)?");

    /**
     * Returns the body of the {@code tasks.named('runGametest')} configuration block, with comments
     * removed and whitespace squeezed out.
     *
     * <p>Scoping to the block matters: a bare file-wide substring search would be satisfied by a
     * line pasted anywhere in the script, including somewhere inert. Dropping comments matters for
     * the same reason — prose describing the pin, or a commented-out pin, must not stand in for a
     * live one.
     */
    private static String runGametestBlock() {
        String script;
        try {
            script = Files.readString(BUILD_SCRIPT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + BUILD_SCRIPT, e);
        }

        Matcher header = BLOCK_HEADER.matcher(script);
        if (!header.find()) {
            return fail(BUILD_SCRIPT + " no longer configures " + BLOCK_LABEL
                    + " — the JaCoCo agent attachment and the always-execute pin have moved or been lost");
        }
        int open = header.end() - 1;

        StringBuilder body = new StringBuilder();
        int depth = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = open; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
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
        return fail(BLOCK_LABEL + " in " + BUILD_SCRIPT
                + " has an unbalanced brace or an unterminated comment — could not extract the block"
                + " to check its wiring");
    }

    @Test
    void gametestSweepIsPinnedToAlwaysExecute() {
        assertTrue(PIN.matcher(runGametestBlock()).find(),
                BLOCK_LABEL + " lost its `outputs.upToDateWhen { false }` pin. The JaCoCo agent's "
                        + "exec file is a declared output, so without the pin a repeat "
                        + "`./gradlew runGametest` reports UP-TO-DATE, runs zero suites, and "
                        + "re-emits the previous coverage number as though it were fresh.");
    }

    @Test
    void gametestSweepStillAttachesTheJacocoAgent() {
        assertTrue(runGametestBlock().contains("jacoco.applyTo("),
                BLOCK_LABEL + " no longer attaches the JaCoCo agent. Without it the gametest "
                        + "sweep writes no exec file, and jacocoMergedReport silently degrades to "
                        + "unit-test-only coverage under the `merged` label.");
    }
}
