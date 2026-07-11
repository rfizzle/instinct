package com.rfizzle.instinct.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.coverage.CoverageResolver;
import com.rfizzle.instinct.coverage.MembershipRule;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import com.rfizzle.instinct.genetics.ProductTable;
import com.rfizzle.instinct.inspection.Inspection;
import com.rfizzle.instinct.veterancy.Veterancy;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /instinct} command surface: {@code info} (perm 0 — reports the looked-at animal's
 * species, set membership, granting rule, and attachment-backed state; the modded-animal
 * debugging surface), {@code set veterancy} (perm 2 — sets the looked-at pet's accrued days;
 * rank and bonuses re-derive immediately through the veterancy choke point), {@code set grade}
 * (perm 2 — sets the looked-at livestock's bloodline grade through the genetics choke point, which
 * re-derives the hardy/fleet bonuses), and {@code reload} (perm 2 — reloads
 * {@code config/instinct.json} and reports the changed-key count). All output is localized
 * {@code command.instinct.*}.
 */
public final class InstinctCommand {

    /** How far {@code /instinct info} looks for an animal, per {@code design/SPEC.md} §Commands. */
    static final double INFO_RANGE_BLOCKS = 8.0;

    /** {@code set veterancy} bounds, per {@code design/SPEC.md} §Commands (0–100000 days). */
    static final int MAX_VETERANCY_DAYS = 100_000;

    private InstinctCommand() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Instinct.MOD_ID)
                .then(Commands.literal("info")
                        .executes(ctx -> runInfo(ctx.getSource())))
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("veterancy")
                                .then(Commands.argument("days",
                                                IntegerArgumentType.integer(0, MAX_VETERANCY_DAYS))
                                        .executes(ctx -> runSetVeterancy(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "days")))))
                        .then(Commands.literal("grade")
                                .then(gradeLiteral(Grade.ORDINARY))
                                .then(gradeLiteral(Grade.STURDY))
                                .then(gradeLiteral(Grade.PRIME))))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> runReload(ctx.getSource()))));
    }

    private static int runInfo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.instinct.not_player"));
            return 0;
        }
        Animal animal = raycastAnimal(player, INFO_RANGE_BLOCKS);
        if (animal == null) {
            source.sendFailure(Component.translatable("command.instinct.info.no_animal"));
            return 0;
        }
        for (Component line : infoLines(animal)) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int runSetVeterancy(CommandSourceStack source, int days) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.instinct.not_player"));
            return 0;
        }
        Animal animal = raycastAnimal(player, INFO_RANGE_BLOCKS);
        if (!(animal instanceof TamableAnimal pet) || !AnimalCoverage.membershipOf(pet).pet()) {
            source.sendFailure(Component.translatable("command.instinct.set.no_pet"));
            return 0;
        }
        int rank = VeterancyHandler.setAccruedDays(pet, days);
        if (rank > 0) {
            source.sendSuccess(() -> Component.translatable("command.instinct.set.veterancy.ranked",
                    pet.getName(), days, Component.translatable(Veterancy.rankKey(rank))), true);
        } else {
            source.sendSuccess(() -> Component.translatable("command.instinct.set.veterancy",
                    pet.getName(), days), true);
        }
        return 1;
    }

    /** One {@code set grade <name>} leaf, its literal being the grade's serialized name. */
    private static LiteralArgumentBuilder<CommandSourceStack> gradeLiteral(Grade grade) {
        return Commands.literal(grade.getSerializedName())
                .executes(ctx -> runSetGrade(ctx.getSource(), grade));
    }

    private static int runSetGrade(CommandSourceStack source, Grade grade) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.instinct.not_player"));
            return 0;
        }
        Animal animal = raycastAnimal(player, INFO_RANGE_BLOCKS);
        if (animal == null || !AnimalCoverage.membershipOf(animal).livestock()) {
            source.sendFailure(Component.translatable("command.instinct.set.no_livestock"));
            return 0;
        }
        GeneticsHandler.setGrade(animal, grade);
        source.sendSuccess(() -> Component.translatable("command.instinct.set.grade",
                animal.getName(), Component.translatable(grade.translationKey())), true);
        return 1;
    }

    private static int runReload(CommandSourceStack source) {
        int changed;
        try {
            changed = InstinctConfig.reload();
        } catch (Exception e) {
            Instinct.LOGGER.error("Config reload failed via command", e);
            source.sendFailure(Component.translatable("command.instinct.reload_failed",
                    String.valueOf(e.getMessage())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.instinct.reload", changed), true);
        return 1;
    }

    /**
     * The {@code /instinct info} report for one animal. Public (internal, not API) so gametests
     * can assert the report without driving a raycast.
     */
    public static List<Component> infoLines(Animal animal) {
        List<Component> lines = new ArrayList<>();
        CoverageResolver.Membership membership = AnimalCoverage.membershipOf(animal);

        lines.add(Component.translatable("command.instinct.info.header",
                animal.getType().getDescription(),
                BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString()));
        lines.add(setLine("pets", membership.pet(), membership.petRule()));
        lines.add(setLine("livestock", membership.livestock(), membership.livestockRule()));

        if (membership.livestock()) {
            Grade grade = InstinctAPI.getGrade(animal);
            Perk perk = InstinctAPI.getPerk(animal);
            lines.add(Component.translatable("command.instinct.info.genetics",
                    Component.translatable(grade.translationKey()),
                    Component.translatable(perk.translationKey())));
            GeneticsData genetics = animal.getAttached(InstinctAttachments.GENETICS);
            if (genetics != null && InstinctAPI.isTroughFed(animal)) {
                long elapsed = animal.level().getGameTime() - genetics.lastTroughFeedTime();
                lines.add(Component.translatable("command.instinct.info.trough_fed", elapsed));
            } else {
                lines.add(Component.translatable("command.instinct.info.trough_fed.never"));
            }
            if (genetics != null && genetics.primeNextOffspring()) {
                lines.add(Component.translatable("command.instinct.info.treat"));
            }
            lines.add(Component.translatable("command.instinct.info.product_source",
                    Component.translatable(productSourceKey(animal))));
        }

        if (membership.pet() && animal instanceof TamableAnimal pet) {
            long days = (long) InstinctAPI.getVeterancyDays(pet);
            int rank = InstinctAPI.getVeterancyRank(pet);
            if (rank > 0) {
                lines.add(Component.translatable("command.instinct.info.veterancy.ranked",
                        days, Component.translatable(Veterancy.rankKey(rank))));
            } else {
                lines.add(Component.translatable("command.instinct.info.veterancy", days));
            }
            if (InstinctAPI.isDowned(pet)) {
                lines.add(Component.translatable("command.instinct.info.downed"));
            }
        }
        return lines;
    }

    /**
     * The lang key naming where an animal's death-drop bonus comes from: a data product row, the
     * drop mirror when enabled, or nothing.
     */
    private static String productSourceKey(Animal animal) {
        if (ProductTable.rowFor(animal.getType()) != null) {
            return "command.instinct.info.product_source.data";
        }
        return InstinctConfig.get().enableGenericDropMirror
                ? "command.instinct.info.product_source.mirror"
                : "command.instinct.info.product_source.none";
    }

    /** One set's membership line: "yes — rule", "no — excluded by rule", or plain "no". */
    private static Component setLine(String set, boolean member, MembershipRule rule) {
        if (member) {
            return Component.translatable("command.instinct.info." + set + ".member",
                    Component.translatable(rule.translationKey()));
        }
        if (rule == MembershipRule.NONE) {
            return Component.translatable("command.instinct.info." + set + ".non_member");
        }
        return Component.translatable("command.instinct.info." + set + ".excluded",
                Component.translatable(rule.translationKey()));
    }

    /** The shared crosshair raycast ({@link Inspection#animalOnCrosshair}). */
    private static Animal raycastAnimal(ServerPlayer player, double range) {
        return Inspection.animalOnCrosshair(player, range);
    }
}
