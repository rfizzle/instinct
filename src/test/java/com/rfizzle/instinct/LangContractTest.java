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
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
            "block.instinct.kennel_post",
            "itemGroup.instinct",
            "config.instinct.title",
            "config.instinct.category.coverage",
            "config.instinct.category.self_preservation",
            "config.instinct.category.veterancy",
            "config.instinct.category.genetics",
            "config.instinct.category.herding",
            "config.instinct.category.trough",
            "config.instinct.category.whistle",
            "config.instinct.category.kennel",
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
            "notification.instinct.pet_carried",
            "notification.instinct.pet_set_down",
            "notification.instinct.hands_full",
            "notification.instinct.pet_recovered",
            "notification.instinct.whistle.follow",
            "notification.instinct.whistle.stay",
            "notification.instinct.whistle.none",
            "notification.instinct.whistle.attack",
            "notification.instinct.whistle.no_target",
            "notification.instinct.whistle.round_up",
            "notification.instinct.whistle.nothing",
            "notification.instinct.whistle.guard",
            "notification.instinct.whistle.assign_home",
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
            "subtitles.instinct.whistle_guard",
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
            "fragment.instinct.grade.ordinary",
            "fragment.instinct.grade.sturdy",
            "fragment.instinct.grade.prime",
            "fragment.instinct.perk.none",
            "fragment.instinct.perk.hardy",
            "fragment.instinct.perk.fleet",
            "fragment.instinct.perk.fertile",
            "fragment.instinct.perk.placid",
            "fragment.instinct.rank.seasoned",
            "fragment.instinct.rank.veteran",
            "fragment.instinct.rank.venerable");

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

    /**
     * Every subtitle {@code sounds.json} names must exist in the lang file, derived from the manifest
     * rather than hand-listed. {@code REQUIRED_KEYS} above is maintained by hand and had already drifted
     * — {@code subtitles.instinct.whistle_guard} shipped for a release without being listed there — and a
     * missing subtitle is invisible in play unless the player has subtitles turned on, so nothing else
     * would have said so.
     */
    @Test
    void everySoundSubtitleNamedByTheManifestExists() {
        JsonObject lang = lang();
        JsonObject sounds;
        try {
            sounds = JsonParser.parseString(
                    Files.readString(Path.of("src/main/resources/assets/instinct/sounds.json"),
                            StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load sounds.json", e);
        }

        List<String> missing = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        for (String sound : sounds.keySet()) {
            JsonObject entry = sounds.getAsJsonObject(sound);
            if (!entry.has("subtitle")) {
                missing.add(sound + " (declares no subtitle at all)");
                continue;
            }
            String key = entry.get("subtitle").getAsString();
            checked.add(key);
            if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                missing.add(key + " (named by sound '" + sound + "')");
            }
        }

        assertFalse(checked.isEmpty(), "sounds.json named no subtitles, so this guard proved nothing");
        assertTrue(missing.isEmpty(),
                "every custom sound needs a subtitle key that resolves (DESIGN-SYSTEM §9), but: " + missing);
    }

    /**
     * The prefixes from concord {@code design/DESIGN-SYSTEM.md} §10's vocabulary table that Instinct
     * actually uses. Bare-modid prefixes are deliberately absent: §10 names {@code <mod>.rank.*} as the
     * defect the enum rule forbids, and this list used to whitelist exactly that — so the guard
     * certified the violation instead of catching it.
     *
     * <p>{@code fragment.} is §10's reserved prefix for a noun phrase composed into a {@code %s} slot on
     * two or more surfaces at once. Instinct's grade, perk, and rank names each reach three or four —
     * command feedback, an action-bar notification, a Jade/WTHIT line, and (for rank) an item tooltip —
     * so filing any of them under a single surface would make the key a lie on the others.
     */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "command.instinct.",
            "config.instinct.",
            "notification.instinct.",
            "tooltip.instinct.",
            "advancements.instinct.",
            "subtitles.instinct.",
            "fragment.instinct.",
            "block.instinct.",
            "item.instinct.",
            "itemGroup.instinct");

    /**
     * Registry-derived prefixes, whose casing is vanilla-mandated rather than ours to rule: the key
     * mirrors a registry id, and registry ids are snake_case.
     */
    private static final List<String> REGISTRY_PREFIXES = List.of(
            "block.instinct.", "item.instinct.", "itemGroup.instinct");

    private static final Pattern SNAKE_SEGMENT = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final Pattern CAMEL_SEGMENT = Pattern.compile("[a-z][a-zA-Z0-9]*");

    @Test
    void everyLangKeyUsesAKnownSurfacePrefix() {
        JsonObject lang = lang();
        List<String> unknown = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (ALLOWED_PREFIXES.stream().noneMatch(key::startsWith)) {
                unknown.add(key);
            }
        }
        assertTrue(unknown.isEmpty(),
                "lang keys outside concord DESIGN-SYSTEM §10's prefix vocabulary: " + unknown
                        + " — a bare-modid prefix (instinct.rank.*) is the defect §10 names explicitly."
                        + " An internal enum's translationKey() is surface-prefixed, or"
                        + " fragment.instinct.* when the value lands in a %s slot on two or more surfaces");
    }

    /**
     * §10's casing rule, which follows the <em>surface</em> rather than the mod.
     *
     * <p>{@code config.<mod>.<field>} and its {@code .tooltip} pair are camelCase, because the key
     * mirrors the Java config field it labels and the two must stay mechanically aligned — which is
     * exactly what {@link #everyConfigFieldHasLabelAndTooltip()} depends on.
     * {@code config.<mod>.category.<name>} names a section rather than a field, so it is snake_case like
     * everything else. Registry-derived keys are snake_case because vanilla says so. Every other
     * authored surface is snake_case.
     *
     * <p>Enum <em>values</em> inside a key stay snake_case wherever they name a state, which is why this
     * runs per dot-separated segment rather than over the whole key.
     */
    @Test
    void everyLangKeyFollowsItsSurfacesCasing() {
        JsonObject lang = lang();
        List<String> offenders = new ArrayList<>();
        for (String key : lang.keySet()) {
            String[] segments = key.split("\\.");
            // Only the segments we author are in scope. The prefix itself comes from §10's fixed
            // vocabulary, and some of those tokens are vanilla's own camelCase (`itemGroup.<mod>`) —
            // holding them to the rule would flag the standard's own table.
            int modIndex = indexOfModSegment(segments);
            for (int i = modIndex + 1; i < segments.length; i++) {
                if (segments[i].isEmpty()) {
                    offenders.add(key + " (empty segment)");
                    break;
                }
                boolean camel = isConfigFieldSegment(key, segments, i, modIndex);
                Pattern expected = camel ? CAMEL_SEGMENT : SNAKE_SEGMENT;
                if (!expected.matcher(segments[i]).matches()) {
                    offenders.add(key + " (segment '" + segments[i] + "' is not "
                            + (camel ? "camelCase" : "snake_case") + ")");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "lang keys violating concord DESIGN-SYSTEM §10's casing rule: " + offenders
                        + " — config.<mod>.<field> and its .tooltip are camelCase (the key mirrors the"
                        + " Java field); every other authored surface, config categories included, is"
                        + " snake_case");
    }

    /**
     * Whether segment {@code i} is the one §10 requires to be camelCase: the field name in
     * {@code config.instinct.<field>} and in {@code config.instinct.<field>.tooltip}. Not
     * {@code config.instinct.category.<name>}, which names a section; not {@code config.instinct.title},
     * which names the screen; and not the value segment of a trailing {@code .option.<value>}, which
     * names a state.
     */
    private static boolean isConfigFieldSegment(String key, String[] segments, int i, int modIndex) {
        return i == modIndex + 1
                && "config".equals(segments[0])
                && !"category".equals(segments[i])
                && !"config.instinct.title".equals(key);
    }

    /**
     * Index of the {@code instinct} segment that ends every §10 prefix — 0 for the modid-only
     * {@code itemGroup.instinct} shape, 1 for the common one, 2 for two-token prefixes like
     * {@code death.attack.<mod>}. Returns -1 when absent, which puts the whole key in scope; the prefix
     * guard above has already rejected such a key, so this only decides how loudly it also fails here.
     */
    private static int indexOfModSegment(String[] segments) {
        for (int i = 0; i < segments.length; i++) {
            if ("instinct".equals(segments[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Guards the casing guard: it must actually be looking at registry-derived keys, not an empty set. */
    @Test
    void theCasingGuardSeesEveryKindOfSurface() {
        JsonObject lang = lang();
        assertTrue(lang.keySet().stream().anyMatch(k -> REGISTRY_PREFIXES.stream().anyMatch(k::startsWith)),
                "found no registry-derived lang keys, so the casing guard is proving nothing about them");
        assertTrue(lang.keySet().stream().anyMatch(k -> k.startsWith("config.instinct.category.")),
                "found no config category keys, so the snake_case half of the config rule is unexercised");
        assertTrue(lang.keySet().stream().anyMatch(k -> k.startsWith("fragment.instinct.")),
                "found no fragment keys, so the enum-prefix rule is unexercised");
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

    /**
     * Every constant of an enum-valued config field has a {@code config.instinct.<field>.option.<constant>}
     * label, so the Cloth selector never offers a raw Java constant name as a choice. Reflection over the
     * POJO keeps this in step as enum fields and their constants are added.
     *
     * <p>Options carry a label only — the field's own tooltip explains the choice — so
     * {@link #everyConfigLabelHasATooltip} exempts {@code .option.} keys from its sweep.
     */
    @Test
    void everyEnumConfigOptionHasALabel() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (Field field : InstinctConfig.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isPublic(mods) || Modifier.isStatic(mods) || Modifier.isTransient(mods)
                    || !field.getType().isEnum()) {
                continue;
            }
            for (Object constant : field.getType().getEnumConstants()) {
                String key = "config.instinct." + field.getName() + ".option."
                        + ((Enum<?>) constant).name().toLowerCase(Locale.ROOT);
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "enum config option labels missing or blank: " + missing);
    }

    /** No orphan {@code config.instinct.*} label without a matching non-blank {@code .tooltip}. */
    @Test
    void everyConfigLabelHasATooltip() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (!key.startsWith("config.instinct.") || key.endsWith(".tooltip")
                    || key.equals("config.instinct.title") || key.startsWith("config.instinct.category.")
                    || key.contains(".option.")) {
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
