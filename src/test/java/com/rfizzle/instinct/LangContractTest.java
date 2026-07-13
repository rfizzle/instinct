package com.rfizzle.instinct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rfizzle.instinct.config.InstinctConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
            "command.instinct.info.mounts.member",
            "command.instinct.info.mounts.excluded",
            "command.instinct.info.mounts.non_member",
            "command.instinct.info.rule.config",
            "command.instinct.info.rule.tag",
            "command.instinct.info.rule.heuristic",
            "command.instinct.info.rule.none",
            "command.instinct.info.genetics",
            "command.instinct.info.trough_fed",
            "command.instinct.info.trough_fed.never",
            "command.instinct.info.treat",
            "command.instinct.info.product_source",
            "command.instinct.info.product_source.data",
            "command.instinct.info.product_source.mirror",
            "command.instinct.info.product_source.none",
            "command.instinct.info.veterancy",
            "command.instinct.info.veterancy.ranked",
            "command.instinct.info.downed",
            "command.instinct.set.no_pet",
            "command.instinct.set.no_livestock",
            "command.instinct.set.veterancy",
            "command.instinct.set.veterancy.ranked",
            "command.instinct.set.grade",
            "command.instinct.reload",
            "command.instinct.reload_failed",
            "item.instinct.pedigree_treat",
            "item.instinct.vet_kit",
            "item.instinct.command_whistle",
            "item.instinct.keepsake_collar",
            "block.instinct.feeding_trough",
            "itemGroup.instinct",
            "config.instinct.title",
            "config.instinct.category.coverage",
            "config.instinct.category.self_preservation",
            "config.instinct.category.veterancy",
            "config.instinct.category.genetics",
            "config.instinct.category.herding",
            "config.instinct.category.trough",
            "config.instinct.category.whistle",
            "config.instinct.category.downed",
            "config.instinct.category.inspection",
            "tooltip.instinct.animal.grade",
            "tooltip.instinct.animal.grade_perk",
            "tooltip.instinct.animal.veterancy",
            "tooltip.instinct.animal.veterancy_ranked",
            "tooltip.instinct.animal.downed",
            "tooltip.instinct.keepsake_collar.ranked",
            "tooltip.instinct.keepsake_collar.days",
            "tooltip.instinct.keepsake_collar.empty",
            "tooltip.instinct.trough.stored",
            "tooltip.instinct.trough.empty",
            "tooltip.instinct.trough.population",
            "notification.instinct.rank_up",
            "notification.instinct.inspect_pet",
            "notification.instinct.inspect_pet.ranked",
            "notification.instinct.inspect_livestock",
            "notification.instinct.inspect_livestock.perk",
            "notification.instinct.pet_downed",
            "notification.instinct.pet_revived",
            "notification.instinct.whistle.follow",
            "notification.instinct.whistle.stay",
            "notification.instinct.whistle.none",
            "notification.instinct.whistle.attack",
            "notification.instinct.whistle.no_target",
            "notification.instinct.whistle.round_up",
            "notification.instinct.whistle.nothing",
            "notification.instinct.whistle.silent",
            "notification.instinct.whistle.locate.header",
            "notification.instinct.whistle.locate.line",
            "notification.instinct.whistle.locate.line_other",
            "notification.instinct.whistle.locate.line_other_downed",
            "notification.instinct.whistle.locate.more",
            "notification.instinct.whistle.locate.none",
            "notification.instinct.whistle.locate.dir.n",
            "notification.instinct.whistle.locate.dir.ne",
            "notification.instinct.whistle.locate.dir.e",
            "notification.instinct.whistle.locate.dir.se",
            "notification.instinct.whistle.locate.dir.s",
            "notification.instinct.whistle.locate.dir.sw",
            "notification.instinct.whistle.locate.dir.w",
            "notification.instinct.whistle.locate.dir.nw",
            "notification.instinct.whistle.locate.state.sitting",
            "notification.instinct.whistle.locate.state.following",
            "notification.instinct.whistle.locate.state.guarding",
            "notification.instinct.whistle.locate.state.downed",
            "notification.instinct.whistle.locate.dim.overworld",
            "notification.instinct.whistle.locate.dim.nether",
            "notification.instinct.whistle.locate.dim.end",
            "subtitles.instinct.rank_up",
            "subtitles.instinct.revive",
            "subtitles.instinct.whistle_follow",
            "subtitles.instinct.whistle_stay",
            "subtitles.instinct.whistle_attack",
            "subtitles.instinct.whistle_herd",
            "advancements.instinct.root.title",
            "advancements.instinct.root.description",
            "advancements.instinct.old_friend.title",
            "advancements.instinct.old_friend.description",
            "advancements.instinct.best_in_show.title",
            "advancements.instinct.best_in_show.description",
            "advancements.instinct.back_from_the_brink.title",
            "advancements.instinct.back_from_the_brink.description",
            "advancements.instinct.pack_leader.title",
            "advancements.instinct.pack_leader.description",
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

    /**
     * Every {@code config/instinct.json} field the Cloth screen exposes has a
     * {@code config.instinct.<field>} label and a {@code .tooltip}, so no config entry ever renders
     * as a raw key. Reflection over the POJO keeps this in step as fields are added.
     */
    @Test
    void everyConfigFieldHasLabelAndTooltip() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Field field : InstinctConfig.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isPublic(mods) || Modifier.isStatic(mods) || Modifier.isTransient(mods)
                    || field.getName().equals("configVersion")) {
                continue;
            }
            for (String key : List.of("config.instinct." + field.getName(),
                    "config.instinct." + field.getName() + ".tooltip")) {
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "config lang keys missing or blank: " + missing);
    }

    /** No orphan {@code config.instinct.*} label without a matching non-blank {@code .tooltip}. */
    @Test
    void everyConfigLabelHasATooltip() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (!key.startsWith("config.instinct.") || key.endsWith(".tooltip")
                    || key.equals("config.instinct.title") || key.startsWith("config.instinct.category.")) {
                continue;
            }
            String tooltip = key + ".tooltip";
            if (!lang.has(tooltip) || lang.get(tooltip).getAsString().isBlank()) {
                missing.add(tooltip);
            }
        }
        assertTrue(missing.isEmpty(), "config entries missing a .tooltip lang key: " + missing);
    }
}
