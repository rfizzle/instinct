package com.rfizzle.instinct.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the ModMenu / Cloth Config screen against a config field shipping without an editable entry —
 * the exact drift that leaves a documented, localized option uneditable in-game. The Cloth builder is
 * client-only and needs the Cloth runtime, so this reads its source and asserts every server-config
 * field is wired through a {@code working.<field> = } save consumer, reflection keeping it in step as
 * fields are added.
 */
class ClothConfigScreenContractTest {

    private static final Path BUILDER = Path.of(
            "src/client/java/com/rfizzle/instinct/compat/modmenu/ClothConfigScreenBuilder.java");

    @Test
    void everyConfigFieldIsWiredIntoTheClothScreen() throws IOException {
        String source = Files.readString(BUILDER, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (Field field : InstinctConfig.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isPublic(mods) || Modifier.isStatic(mods) || Modifier.isTransient(mods)
                    || field.getName().equals("configVersion")) {
                continue;
            }
            // Each editable field saves through `working.<field> = ` in the builder.
            if (!source.contains("working." + field.getName() + " =")) {
                missing.add(field.getName());
            }
        }
        assertTrue(missing.isEmpty(), "config fields missing a Cloth screen entry: " + missing);
    }
}
