package com.rfizzle.instinct;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class InstinctIdTest {
    @Test
    void idUsesTheModNamespace() {
        ResourceLocation id = Instinct.id("feeding_trough");
        assertEquals("instinct", id.getNamespace());
        assertEquals("feeding_trough", id.getPath());
    }
}
