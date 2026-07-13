package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.item.KeepsakeEngraving;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Instinct's custom {@link DataComponentType data components} ({@code design/SPEC.md} §7). Per-stack
 * data on the mod's items, registered from {@code onInitialize} before {@link InstinctItems} so the
 * items can reference their component types. Registered once; idempotent for datagen and tests.
 */
public final class InstinctDataComponents {

    /** The keepsake collar's frozen engraving — the lost pet's name, rank, and days at your side. */
    public static final DataComponentType<KeepsakeEngraving> KEEPSAKE_ENGRAVING =
            DataComponentType.<KeepsakeEngraving>builder()
                    .persistent(KeepsakeEngraving.CODEC)
                    .networkSynchronized(KeepsakeEngraving.STREAM_CODEC)
                    .build();

    private static boolean registered = false;

    private InstinctDataComponents() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Instinct.id("keepsake_engraving"), KEEPSAKE_ENGRAVING);
    }
}
