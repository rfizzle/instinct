package com.rfizzle.instinct.compat.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animal probe formatter is pure ({@code CompoundTag → List<Component>}), so it unit-tests with
 * hand-built tags and no Jade/WTHIT jars — pinning exact keys, order, and the missing-presence and
 * unknown-value fallbacks.
 */
class AnimalProbeTooltipTest {

    private static String key(Component line) {
        return ((TranslatableContents) line.getContents()).getKey();
    }

    @Test
    void emptyTagYieldsNoLines() {
        assertTrue(AnimalProbeTooltip.buildLines(new CompoundTag()).isEmpty());
    }

    @Test
    void tagWithoutPresenceFlagYieldsNoLines() {
        CompoundTag tag = new CompoundTag();
        tag.putString(AnimalProbeTooltip.KEY_GRADE, "prime");
        assertTrue(AnimalProbeTooltip.buildLines(tag).isEmpty(), "no presence flag → inert");
    }

    @Test
    void gradeWithoutPerkUsesGradeLine() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AnimalProbeTooltip.KEY_PRESENT, true);
        tag.putString(AnimalProbeTooltip.KEY_GRADE, "sturdy");
        tag.putString(AnimalProbeTooltip.KEY_PERK, "none");
        List<Component> lines = AnimalProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        assertEquals("tooltip.instinct.animal.grade", key(lines.get(0)));
    }

    @Test
    void gradeWithPerkUsesGradePerkLine() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AnimalProbeTooltip.KEY_PRESENT, true);
        tag.putString(AnimalProbeTooltip.KEY_GRADE, "prime");
        tag.putString(AnimalProbeTooltip.KEY_PERK, "fleet");
        List<Component> lines = AnimalProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        assertEquals("tooltip.instinct.animal.grade_perk", key(lines.get(0)));
        assertEquals(2, ((TranslatableContents) lines.get(0).getContents()).getArgs().length);
    }

    @Test
    void rankZeroVeterancyOmitsRank() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AnimalProbeTooltip.KEY_PRESENT, true);
        tag.putLong(AnimalProbeTooltip.KEY_VET_DAYS, 5L);
        tag.putInt(AnimalProbeTooltip.KEY_VET_RANK, 0);
        List<Component> lines = AnimalProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        assertEquals("tooltip.instinct.animal.veterancy", key(lines.get(0)));
    }

    @Test
    void rankedVeterancyUsesRankedLine() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AnimalProbeTooltip.KEY_PRESENT, true);
        tag.putLong(AnimalProbeTooltip.KEY_VET_DAYS, 40L);
        tag.putInt(AnimalProbeTooltip.KEY_VET_RANK, 2);
        List<Component> lines = AnimalProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        assertEquals("tooltip.instinct.animal.veterancy_ranked", key(lines.get(0)));
    }

    @Test
    void downedLineAppears() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AnimalProbeTooltip.KEY_PRESENT, true);
        tag.putBoolean(AnimalProbeTooltip.KEY_DOWNED, true);
        List<Component> lines = AnimalProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        assertEquals("tooltip.instinct.animal.downed", key(lines.get(0)));
    }

    @Test
    void combinedStateOrdersGradeThenVeterancyThenDowned() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AnimalProbeTooltip.KEY_PRESENT, true);
        tag.putString(AnimalProbeTooltip.KEY_GRADE, "prime");
        tag.putString(AnimalProbeTooltip.KEY_PERK, "none");
        tag.putLong(AnimalProbeTooltip.KEY_VET_DAYS, 40L);
        tag.putInt(AnimalProbeTooltip.KEY_VET_RANK, 2);
        tag.putBoolean(AnimalProbeTooltip.KEY_DOWNED, true);
        List<Component> lines = AnimalProbeTooltip.buildLines(tag);
        assertEquals(3, lines.size());
        assertEquals("tooltip.instinct.animal.grade", key(lines.get(0)));
        assertEquals("tooltip.instinct.animal.veterancy_ranked", key(lines.get(1)));
        assertEquals("tooltip.instinct.animal.downed", key(lines.get(2)));
    }

    @Test
    void unknownGradeNameFallsBackWithoutThrowing() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AnimalProbeTooltip.KEY_PRESENT, true);
        tag.putString(AnimalProbeTooltip.KEY_GRADE, "not_a_grade");
        tag.putString(AnimalProbeTooltip.KEY_PERK, "not_a_perk");
        List<Component> lines = AnimalProbeTooltip.buildLines(tag);
        assertEquals(1, lines.size());
        // Unknown perk falls back to NONE, so the perkless grade line is used.
        assertEquals("tooltip.instinct.animal.grade", key(lines.get(0)));
    }
}
