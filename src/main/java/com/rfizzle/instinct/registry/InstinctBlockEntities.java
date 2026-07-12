package com.rfizzle.instinct.registry;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.block.FeedingTroughBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Instinct's block-entity types ({@code design/SPEC.md} §5). Registered once from {@code
 * onInitialize}, after {@link InstinctBlocks} (the type references its block).
 */
public final class InstinctBlockEntities {

    /** The feeding trough's block entity — storage plus the passive feeding-loop ticker (§5). */
    public static final BlockEntityType<FeedingTroughBlockEntity> FEEDING_TROUGH =
            FabricBlockEntityTypeBuilder.create(FeedingTroughBlockEntity::new, InstinctBlocks.FEEDING_TROUGH)
                    .build();

    private static boolean registered = false;

    private InstinctBlockEntities() {
    }

    /** Idempotent: datagen bootstrap and test setup may reach this beside {@code onInitialize}. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Instinct.id("feeding_trough"), FEEDING_TROUGH);
    }
}
