package com.rfizzle.instinct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped lang file against silent drift: every translation key the code builds must
 * exist and be non-blank, so a renamed key can never surface as a raw {@code command.instinct.*}
 * string in chat.
 */
class LangContractTest {

    private static final String RESOURCE = "/assets/instinct/lang/en_us.json";
    private static final Path SOURCE = Path.of("src/main/resources/assets/instinct/lang/en_us.json");

    /** Every key referenced from code (commands, rules, grade/perk/rank display names). */
    private static final List<String> REQUIRED_KEYS = List.of(
            "command.instinct.not_player",
            "command.instinct.info.no_animal",
            "command.instinct.info.header",
            "command.instinct.info.pets.member",
            "command.instinct.info.pets.excluded",
            "command.instinct.info.pets.non_member",
            "command.instinct.info.livestock.member",
            "command.instinct.info.livestock.excluded",
            "command.instinct.info.livestock.non_member",
            "command.instinct.info.rule.config",
            "command.instinct.info.rule.tag",
            "command.instinct.info.rule.heuristic",
            "command.instinct.info.rule.none",
            "command.instinct.info.genetics",
            "command.instinct.info.trough_fed",
            "command.instinct.info.trough_fed.never",
            "command.instinct.info.treat",
            "command.instinct.info.veterancy",
            "command.instinct.info.veterancy.ranked",
            "command.instinct.info.downed",
            "command.instinct.reload",
            "command.instinct.reload_failed",
            "instinct.grade.ordinary",
            "instinct.grade.sturdy",
            "instinct.grade.prime",
            "instinct.perk.none",
            "instinct.perk.hardy",
            "instinct.perk.fleet",
            "instinct.perk.fertile",
            "instinct.perk.placid",
            "instinct.rank.seasoned",
            "instinct.rank.veteran",
            "instinct.rank.venerable");

    private static JsonObject lang() {
        try (InputStream in = LangContractTest.class.getResourceAsStream(RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }

    @Test
    void everyCodeReferencedKeyExistsAndIsNonBlank() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "lang keys missing or blank: " + missing);
    }

    @Test
    void everyLangKeyUsesAKnownSurfacePrefix() {
        JsonObject lang = lang();
        List<String> unknown = new ArrayList<>();
        for (String key : lang.keySet()) {
            boolean allowed = key.startsWith("command.instinct.")
                    || key.startsWith("config.instinct.")
                    || key.startsWith("notification.instinct.")
                    || key.startsWith("tooltip.instinct.")
                    || key.startsWith("advancements.instinct.")
                    || key.startsWith("subtitles.instinct.")
                    || key.startsWith("instinct.grade.")
                    || key.startsWith("instinct.perk.")
                    || key.startsWith("instinct.rank.")
                    || key.startsWith("block.instinct.")
                    || key.startsWith("item.instinct.")
                    || key.startsWith("itemGroup.instinct");
            if (!allowed) {
                unknown.add(key);
            }
        }
        assertTrue(unknown.isEmpty(), "lang keys outside the SPEC §Localization surfaces: " + unknown);
    }
}
