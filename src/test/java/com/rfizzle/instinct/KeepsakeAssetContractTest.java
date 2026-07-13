package com.rfizzle.instinct;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the keepsake collar's shipped item model and texture against the drift datagen never sees:
 * the generated model must parent the flat-item vanilla model and point {@code layer0} at the
 * collar sprite, and that sprite's {@code .png} must actually exist on the classpath — otherwise the
 * item renders as the black-and-magenta missing-texture checker in-game with a green build.
 */
class KeepsakeAssetContractTest {

    private static final String MODEL_RESOURCE = "/assets/instinct/models/item/keepsake_collar.json";
    private static final Path MODEL_SOURCE =
            Path.of("src/main/generated/assets/instinct/models/item/keepsake_collar.json");
    private static final String TEXTURE_RESOURCE = "/assets/instinct/textures/item/keepsake_collar.png";

    @Test
    void modelIsAFlatItemPointingAtTheCollarSprite() {
        JsonObject model = modelJson();
        assertEquals("minecraft:item/generated", model.get("parent").getAsString(),
                "a 2D item sprite parents minecraft:item/generated");
        assertEquals("instinct:item/keepsake_collar",
                model.getAsJsonObject("textures").get("layer0").getAsString(),
                "layer0 resolves to the collar texture");
    }

    @Test
    void collarTextureExistsOnTheClasspath() {
        try (InputStream in = KeepsakeAssetContractTest.class.getResourceAsStream(TEXTURE_RESOURCE)) {
            assertNotNull(in, "keepsake_collar.png must ship at " + TEXTURE_RESOURCE);
            byte[] png = in.readAllBytes();
            assertTrue(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P',
                    "the shipped collar sprite is a real PNG");
        } catch (IOException e) {
            throw new AssertionError("could not read the collar texture", e);
        }
    }

    private static JsonObject modelJson() {
        try (InputStream in = KeepsakeAssetContractTest.class.getResourceAsStream(MODEL_RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(MODEL_SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load the keepsake collar model", e);
        }
    }
}
