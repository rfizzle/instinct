package com.rfizzle.instinct.compat.modmenu;

import com.rfizzle.instinct.config.InstinctConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/**
 * Builds the Cloth Config screen — every {@code config/instinct.json} key from the spec's
 * Configuration table, grouped one {@link ConfigCategory} per SPEC section. Referenced only when
 * Cloth Config is loaded (see {@link ModMenuIntegration}), so its Cloth imports never resolve
 * without it. The screen edits a working copy; {@link InstinctConfig#publish} null-heals, clamps,
 * persists, and atomically swaps it in, so a reader never observes a half-applied edit and every
 * field lands within its validated range no matter what the screen wrote.
 */
final class ClothConfigScreenBuilder {

    private ClothConfigScreenBuilder() {
    }

    static Screen build(Screen parent) {
        InstinctConfig config = InstinctConfig.get();
        InstinctConfig defaults = new InstinctConfig();
        InstinctConfig working = config.copy();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.instinct.title"))
                .setSavingRunnable(() -> InstinctConfig.publish(working));

        ConfigEntryBuilder entry = builder.entryBuilder();

        // --- Animal Coverage ---
        ConfigCategory coverage = builder.getOrCreateCategory(Component.translatable("config.instinct.category.coverage"));
        coverage.addEntry(entry.startBooleanToggle(label("autoDetectAnimals"), config.autoDetectAnimals)
                .setDefaultValue(defaults.autoDetectAnimals)
                .setTooltip(tooltip("autoDetectAnimals"))
                .setSaveConsumer(v -> working.autoDetectAnimals = v)
                .build());
        coverage.addEntry(entry.startStrList(label("petsInclude"), config.petsInclude)
                .setDefaultValue(defaults.petsInclude)
                .setTooltip(tooltip("petsInclude"))
                .setSaveConsumer(v -> working.petsInclude = new ArrayList<>(v))
                .build());
        coverage.addEntry(entry.startStrList(label("petsExclude"), config.petsExclude)
                .setDefaultValue(defaults.petsExclude)
                .setTooltip(tooltip("petsExclude"))
                .setSaveConsumer(v -> working.petsExclude = new ArrayList<>(v))
                .build());
        coverage.addEntry(entry.startStrList(label("livestockInclude"), config.livestockInclude)
                .setDefaultValue(defaults.livestockInclude)
                .setTooltip(tooltip("livestockInclude"))
                .setSaveConsumer(v -> working.livestockInclude = new ArrayList<>(v))
                .build());
        coverage.addEntry(entry.startStrList(label("livestockExclude"), config.livestockExclude)
                .setDefaultValue(defaults.livestockExclude)
                .setTooltip(tooltip("livestockExclude"))
                .setSaveConsumer(v -> working.livestockExclude = new ArrayList<>(v))
                .build());
        coverage.addEntry(entry.startStrList(label("mountsInclude"), config.mountsInclude)
                .setDefaultValue(defaults.mountsInclude)
                .setTooltip(tooltip("mountsInclude"))
                .setSaveConsumer(v -> working.mountsInclude = new ArrayList<>(v))
                .build());
        coverage.addEntry(entry.startStrList(label("mountsExclude"), config.mountsExclude)
                .setDefaultValue(defaults.mountsExclude)
                .setTooltip(tooltip("mountsExclude"))
                .setSaveConsumer(v -> working.mountsExclude = new ArrayList<>(v))
                .build());

        // --- Pet Self-Preservation (§1) ---
        ConfigCategory selfPreservation = builder.getOrCreateCategory(Component.translatable("config.instinct.category.self_preservation"));
        selfPreservation.addEntry(entry.startBooleanToggle(label("enableSelfPreservation"), config.enableSelfPreservation)
                .setDefaultValue(defaults.enableSelfPreservation)
                .setTooltip(tooltip("enableSelfPreservation"))
                .setSaveConsumer(v -> working.enableSelfPreservation = v)
                .build());
        selfPreservation.addEntry(entry.startIntField(label("creeperBerthBlocks"), config.creeperBerthBlocks)
                .setDefaultValue(defaults.creeperBerthBlocks)
                .setMin(2).setMax(8)
                .setTooltip(tooltip("creeperBerthBlocks"))
                .setSaveConsumer(v -> working.creeperBerthBlocks = v)
                .build());
        selfPreservation.addEntry(entry.startDoubleField(label("teleportSuppressFallDistance"), config.teleportSuppressFallDistance)
                .setDefaultValue(defaults.teleportSuppressFallDistance)
                .setMin(0.5).setMax(10.0)
                .setTooltip(tooltip("teleportSuppressFallDistance"))
                .setSaveConsumer(v -> working.teleportSuppressFallDistance = v)
                .build());

        // --- Pet Veterancy (§2) ---
        ConfigCategory veterancy = builder.getOrCreateCategory(Component.translatable("config.instinct.category.veterancy"));
        veterancy.addEntry(entry.startBooleanToggle(label("enableVeterancy"), config.enableVeterancy)
                .setDefaultValue(defaults.enableVeterancy)
                .setTooltip(tooltip("enableVeterancy"))
                .setSaveConsumer(v -> working.enableVeterancy = v)
                .build());
        veterancy.addEntry(entry.startIntList(label("veterancyThresholdDays"), config.veterancyThresholdDays)
                .setDefaultValue(defaults.veterancyThresholdDays)
                .setMin(1).setMax(1000)
                .setTooltip(tooltip("veterancyThresholdDays"))
                .setSaveConsumer(v -> working.veterancyThresholdDays = new ArrayList<>(v))
                .build());
        veterancy.addEntry(entry.startDoubleField(label("healthPerRank"), config.healthPerRank)
                .setDefaultValue(defaults.healthPerRank)
                .setMin(0.0).setMax(20.0)
                .setTooltip(tooltip("healthPerRank"))
                .setSaveConsumer(v -> working.healthPerRank = v)
                .build());
        veterancy.addEntry(entry.startDoubleField(label("damagePerRank"), config.damagePerRank)
                .setDefaultValue(defaults.damagePerRank)
                .setMin(0.0).setMax(10.0)
                .setTooltip(tooltip("damagePerRank"))
                .setSaveConsumer(v -> working.damagePerRank = v)
                .build());
        veterancy.addEntry(entry.startBooleanToggle(label("enableRankBehaviors"), config.enableRankBehaviors)
                .setDefaultValue(defaults.enableRankBehaviors)
                .setTooltip(tooltip("enableRankBehaviors"))
                .setSaveConsumer(v -> working.enableRankBehaviors = v)
                .build());
        veterancy.addEntry(entry.startIntField(label("warningRadiusBlocks"), config.warningRadiusBlocks)
                .setDefaultValue(defaults.warningRadiusBlocks)
                .setMin(8).setMax(24)
                .setTooltip(tooltip("warningRadiusBlocks"))
                .setSaveConsumer(v -> working.warningRadiusBlocks = v)
                .build());
        veterancy.addEntry(entry.startIntField(label("mentorRadiusBlocks"), config.mentorRadiusBlocks)
                .setDefaultValue(defaults.mentorRadiusBlocks)
                .setMin(8).setMax(32)
                .setTooltip(tooltip("mentorRadiusBlocks"))
                .setSaveConsumer(v -> working.mentorRadiusBlocks = v)
                .build());
        veterancy.addEntry(entry.startDoubleField(label("mentorRateBonus"), config.mentorRateBonus)
                .setDefaultValue(defaults.mentorRateBonus)
                .setMin(0.0).setMax(1.0)
                .setTooltip(tooltip("mentorRateBonus"))
                .setSaveConsumer(v -> working.mentorRateBonus = v)
                .build());

        // --- Quality Genetics (§3) ---
        ConfigCategory genetics = builder.getOrCreateCategory(Component.translatable("config.instinct.category.genetics"));
        genetics.addEntry(entry.startBooleanToggle(label("enableGenetics"), config.enableGenetics)
                .setDefaultValue(defaults.enableGenetics)
                .setTooltip(tooltip("enableGenetics"))
                .setSaveConsumer(v -> working.enableGenetics = v)
                .build());
        genetics.addEntry(entry.startBooleanToggle(label("enableGenericDropMirror"), config.enableGenericDropMirror)
                .setDefaultValue(defaults.enableGenericDropMirror)
                .setTooltip(tooltip("enableGenericDropMirror"))
                .setSaveConsumer(v -> working.enableGenericDropMirror = v)
                .build());
        genetics.addEntry(entry.startIntField(label("hayRadiusBlocks"), config.hayRadiusBlocks)
                .setDefaultValue(defaults.hayRadiusBlocks)
                .setMin(2).setMax(16)
                .setTooltip(tooltip("hayRadiusBlocks"))
                .setSaveConsumer(v -> working.hayRadiusBlocks = v)
                .build());
        genetics.addEntry(entry.startIntField(label("crowdingThreshold"), config.crowdingThreshold)
                .setDefaultValue(defaults.crowdingThreshold)
                .setMin(4).setMax(64)
                .setTooltip(tooltip("crowdingThreshold"))
                .setSaveConsumer(v -> working.crowdingThreshold = v)
                .build());
        genetics.addEntry(entry.startIntField(label("crowdingRadiusBlocks"), config.crowdingRadiusBlocks)
                .setDefaultValue(defaults.crowdingRadiusBlocks)
                .setMin(4).setMax(16)
                .setTooltip(tooltip("crowdingRadiusBlocks"))
                .setSaveConsumer(v -> working.crowdingRadiusBlocks = v)
                .build());
        genetics.addEntry(entry.startDoubleField(label("gradeUpgradeChance"), config.gradeUpgradeChance)
                .setDefaultValue(defaults.gradeUpgradeChance)
                .setMin(0.0).setMax(1.0)
                .setTooltip(tooltip("gradeUpgradeChance"))
                .setSaveConsumer(v -> working.gradeUpgradeChance = v)
                .build());
        genetics.addEntry(entry.startDoubleField(label("gradeDowngradeChance"), config.gradeDowngradeChance)
                .setDefaultValue(defaults.gradeDowngradeChance)
                .setMin(0.0).setMax(1.0)
                .setTooltip(tooltip("gradeDowngradeChance"))
                .setSaveConsumer(v -> working.gradeDowngradeChance = v)
                .build());
        genetics.addEntry(entry.startDoubleField(label("fertileRenewableReduction"), config.fertileRenewableReduction)
                .setDefaultValue(defaults.fertileRenewableReduction)
                .setMin(0.0).setMax(0.5)
                .setTooltip(tooltip("fertileRenewableReduction"))
                .setSaveConsumer(v -> working.fertileRenewableReduction = v)
                .build());

        // --- Flocking & Herding (§4) ---
        ConfigCategory herding = builder.getOrCreateCategory(Component.translatable("config.instinct.category.herding"));
        herding.addEntry(entry.startBooleanToggle(label("enableFlocking"), config.enableFlocking)
                .setDefaultValue(defaults.enableFlocking)
                .setTooltip(tooltip("enableFlocking"))
                .setSaveConsumer(v -> working.enableFlocking = v)
                .build());
        herding.addEntry(entry.startDoubleField(label("flockSpeedMultiplier"), config.flockSpeedMultiplier)
                .setDefaultValue(defaults.flockSpeedMultiplier)
                .setMin(1.0).setMax(1.5)
                .setTooltip(tooltip("flockSpeedMultiplier"))
                .setSaveConsumer(v -> working.flockSpeedMultiplier = v)
                .build());
        herding.addEntry(entry.startDoubleField(label("flockSpacingBlocks"), config.flockSpacingBlocks)
                .setDefaultValue(defaults.flockSpacingBlocks)
                .setMin(1.0).setMax(4.0)
                .setTooltip(tooltip("flockSpacingBlocks"))
                .setSaveConsumer(v -> working.flockSpacingBlocks = v)
                .build());
        herding.addEntry(entry.startBooleanToggle(label("enableHerding"), config.enableHerding)
                .setDefaultValue(defaults.enableHerding)
                .setTooltip(tooltip("enableHerding"))
                .setSaveConsumer(v -> working.enableHerding = v)
                .build());
        herding.addEntry(entry.startIntField(label("herdingMaxPets"), config.herdingMaxPets)
                .setDefaultValue(defaults.herdingMaxPets)
                .setMin(1).setMax(4)
                .setTooltip(tooltip("herdingMaxPets"))
                .setSaveConsumer(v -> working.herdingMaxPets = v)
                .build());

        // --- Feeding Trough (§5) ---
        ConfigCategory trough = builder.getOrCreateCategory(Component.translatable("config.instinct.category.trough"));
        trough.addEntry(entry.startBooleanToggle(label("enableTrough"), config.enableTrough)
                .setDefaultValue(defaults.enableTrough)
                .setTooltip(tooltip("enableTrough"))
                .setSaveConsumer(v -> working.enableTrough = v)
                .build());
        trough.addEntry(entry.startIntField(label("troughRadiusBlocks"), config.troughRadiusBlocks)
                .setDefaultValue(defaults.troughRadiusBlocks)
                .setMin(4).setMax(24)
                .setTooltip(tooltip("troughRadiusBlocks"))
                .setSaveConsumer(v -> working.troughRadiusBlocks = v)
                .build());
        trough.addEntry(entry.startIntField(label("troughFeedIntervalTicks"), config.troughFeedIntervalTicks)
                .setDefaultValue(defaults.troughFeedIntervalTicks)
                .setMin(10).setMax(200)
                .setTooltip(tooltip("troughFeedIntervalTicks"))
                .setSaveConsumer(v -> working.troughFeedIntervalTicks = v)
                .build());
        trough.addEntry(entry.startIntField(label("troughPopulationCap"), config.troughPopulationCap)
                .setDefaultValue(defaults.troughPopulationCap)
                .setMin(0).setMax(64)
                .setTooltip(tooltip("troughPopulationCap"))
                .setSaveConsumer(v -> working.troughPopulationCap = v)
                .build());

        // --- Command Whistle (§6) ---
        ConfigCategory whistle = builder.getOrCreateCategory(Component.translatable("config.instinct.category.whistle"));
        whistle.addEntry(entry.startBooleanToggle(label("enableWhistle"), config.enableWhistle)
                .setDefaultValue(defaults.enableWhistle)
                .setTooltip(tooltip("enableWhistle"))
                .setSaveConsumer(v -> working.enableWhistle = v)
                .build());
        whistle.addEntry(entry.startIntField(label("whistleRadiusBlocks"), config.whistleRadiusBlocks)
                .setDefaultValue(defaults.whistleRadiusBlocks)
                .setMin(8).setMax(48)
                .setTooltip(tooltip("whistleRadiusBlocks"))
                .setSaveConsumer(v -> working.whistleRadiusBlocks = v)
                .build());
        whistle.addEntry(entry.startIntField(label("whistleTargetRangeBlocks"), config.whistleTargetRangeBlocks)
                .setDefaultValue(defaults.whistleTargetRangeBlocks)
                .setMin(8).setMax(64)
                .setTooltip(tooltip("whistleTargetRangeBlocks"))
                .setSaveConsumer(v -> working.whistleTargetRangeBlocks = v)
                .build());
        whistle.addEntry(entry.startIntField(label("whistleCooldownTicks"), config.whistleCooldownTicks)
                .setDefaultValue(defaults.whistleCooldownTicks)
                .setMin(0).setMax(100)
                .setTooltip(tooltip("whistleCooldownTicks"))
                .setSaveConsumer(v -> working.whistleCooldownTicks = v)
                .build());
        whistle.addEntry(entry.startIntField(label("roundUpGroupRadiusBlocks"), config.roundUpGroupRadiusBlocks)
                .setDefaultValue(defaults.roundUpGroupRadiusBlocks)
                .setMin(4).setMax(16)
                .setTooltip(tooltip("roundUpGroupRadiusBlocks"))
                .setSaveConsumer(v -> working.roundUpGroupRadiusBlocks = v)
                .build());

        // --- Downed Pets & Revival (§7) ---
        ConfigCategory downed = builder.getOrCreateCategory(Component.translatable("config.instinct.category.downed"));
        downed.addEntry(entry.startBooleanToggle(label("enableDownedState"), config.enableDownedState)
                .setDefaultValue(defaults.enableDownedState)
                .setTooltip(tooltip("enableDownedState"))
                .setSaveConsumer(v -> working.enableDownedState = v)
                .build());
        downed.addEntry(entry.startDoubleField(label("reviveHealthFraction"), config.reviveHealthFraction)
                .setDefaultValue(defaults.reviveHealthFraction)
                .setMin(0.1).setMax(1.0)
                .setTooltip(tooltip("reviveHealthFraction"))
                .setSaveConsumer(v -> working.reviveHealthFraction = v)
                .build());
        downed.addEntry(entry.startBooleanToggle(label("downedRankPenalty"), config.downedRankPenalty)
                .setDefaultValue(defaults.downedRankPenalty)
                .setTooltip(tooltip("downedRankPenalty"))
                .setSaveConsumer(v -> working.downedRankPenalty = v)
                .build());
        downed.addEntry(entry.startBooleanToggle(label("enableCarryDowned"), config.enableCarryDowned)
                .setDefaultValue(defaults.enableCarryDowned)
                .setTooltip(tooltip("enableCarryDowned"))
                .setSaveConsumer(v -> working.enableCarryDowned = v)
                .build());
        downed.addEntry(entry.startDoubleField(label("carrySlowdownFraction"), config.carrySlowdownFraction)
                .setDefaultValue(defaults.carrySlowdownFraction)
                .setMin(0.0).setMax(0.9)
                .setTooltip(tooltip("carrySlowdownFraction"))
                .setSaveConsumer(v -> working.carrySlowdownFraction = v)
                .build());

        // --- Predator Watch (§8) ---
        ConfigCategory predatorWatch = builder.getOrCreateCategory(Component.translatable("config.instinct.category.predator_watch"));
        predatorWatch.addEntry(entry.startBooleanToggle(label("enablePredatorWatch"), config.enablePredatorWatch)
                .setDefaultValue(defaults.enablePredatorWatch)
                .setTooltip(tooltip("enablePredatorWatch"))
                .setSaveConsumer(v -> working.enablePredatorWatch = v)
                .build());
        predatorWatch.addEntry(entry.startIntField(label("predatorWatchRadiusBlocks"), config.predatorWatchRadiusBlocks)
                .setDefaultValue(defaults.predatorWatchRadiusBlocks)
                .setMin(4).setMax(24)
                .setTooltip(tooltip("predatorWatchRadiusBlocks"))
                .setSaveConsumer(v -> working.predatorWatchRadiusBlocks = v)
                .build());
        predatorWatch.addEntry(entry.startStrList(label("predatorsInclude"), config.predatorsInclude)
                .setDefaultValue(defaults.predatorsInclude)
                .setTooltip(tooltip("predatorsInclude"))
                .setSaveConsumer(v -> working.predatorsInclude = new ArrayList<>(v))
                .build());
        predatorWatch.addEntry(entry.startStrList(label("predatorsExclude"), config.predatorsExclude)
                .setDefaultValue(defaults.predatorsExclude)
                .setTooltip(tooltip("predatorsExclude"))
                .setSaveConsumer(v -> working.predatorsExclude = new ArrayList<>(v))
                .build());

        // --- Inspection (§2/§3 shared) ---
        ConfigCategory inspection = builder.getOrCreateCategory(Component.translatable("config.instinct.category.inspection"));
        inspection.addEntry(entry.startBooleanToggle(label("enableInspection"), config.enableInspection)
                .setDefaultValue(defaults.enableInspection)
                .setTooltip(tooltip("enableInspection"))
                .setSaveConsumer(v -> working.enableInspection = v)
                .build());

        return builder.build();
    }

    private static Component label(String field) {
        return Component.translatable("config.instinct." + field);
    }

    private static Component tooltip(String field) {
        return Component.translatable("config.instinct." + field + ".tooltip");
    }
}
