package com.rfizzle.instinct.compat.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trough probe formatter is pure ({@code CompoundTag → List<Component>}). Item-name resolution
 * touches the item registry, so the run bootstraps vanilla registries once; the branch logic
 * (empty vs. stored, population line, unknown-id fallback) is otherwise hand-built.
 */
class TroughProbeTooltipTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String key(Component line) {
        return ((TranslatableContents) line.getContents()).getKey();
    }

    @Test
    void tagWithoutPresenceFlagYieldsNoLines() {
        assertTrue(TroughProbeTooltip.buildLines(new CompoundTag()).isEmpty());
    }

    @Test
    void emptyTroughShowsEmptyThenPopulation() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TroughProbeTooltip.KEY_PRESENT, true);
        tag.putInt(TroughProbeTooltip.KEY_POPULATION, 3);
        tag.putInt(TroughProbeTooltip.KEY_CAP, 16);
        List<Component> lines = TroughProbeTooltip.buildLines(tag);
        assertEquals(2, lines.size());
        assertEquals("tooltip.instinct.trough.empty", key(lines.get(0)));
        assertEquals("tooltip.instinct.trough.population", key(lines.get(1)));
    }

    @Test
    void storedTroughShowsStoredThenPopulation() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TroughProbeTooltip.KEY_PRESENT, true);
        tag.putString(TroughProbeTooltip.KEY_ITEM, "minecraft:wheat");
        tag.putInt(TroughProbeTooltip.KEY_COUNT, 12);
        tag.putInt(TroughProbeTooltip.KEY_POPULATION, 2);
        tag.putInt(TroughProbeTooltip.KEY_CAP, 16);
        List<Component> lines = TroughProbeTooltip.buildLines(tag);
        assertEquals(2, lines.size());
        assertEquals("tooltip.instinct.trough.stored", key(lines.get(0)));
        assertEquals("tooltip.instinct.trough.population", key(lines.get(1)));
    }

    @Test
    void unknownItemIdFallsBackToLiteralWithoutThrowing() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TroughProbeTooltip.KEY_PRESENT, true);
        tag.putString(TroughProbeTooltip.KEY_ITEM, "notamod:notanitem");
        tag.putInt(TroughProbeTooltip.KEY_COUNT, 1);
        tag.putInt(TroughProbeTooltip.KEY_POPULATION, 0);
        tag.putInt(TroughProbeTooltip.KEY_CAP, 16);
        List<Component> lines = TroughProbeTooltip.buildLines(tag);
        assertEquals(2, lines.size());
        assertEquals("tooltip.instinct.trough.stored", key(lines.get(0)));
    }
}
