package com.rfizzle.instinct.gametest;

import com.mojang.brigadier.tree.CommandNode;
import com.rfizzle.instinct.command.InstinctCommand;
import com.rfizzle.instinct.config.InstinctConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The {@code /instinct} command surface: tree wiring and per-node permission gates asserted on the
 * live dispatcher, the reload changed-key count, and the info report core. The reload test rewrites
 * the shared {@code config/instinct.json}, so it gets its own {@code batch} and restores the file
 * in {@code finally}.
 */
public class InstinctCommandGameTest implements FabricGameTest {

    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("instinct.json");

    @GameTest(template = EMPTY_STRUCTURE)
    public void treeExistsWithPerNodePermissions(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> root =
                server.getCommands().getDispatcher().getRoot().getChild("instinct");
        helper.assertTrue(root != null, "/instinct should be registered");

        CommandSourceStack nonOp = server.createCommandSourceStack().withPermission(0);
        CommandSourceStack op = server.createCommandSourceStack().withPermission(2);

        helper.assertTrue(root.getChild("info").canUse(nonOp), "info should be usable at perm 0");
        helper.assertFalse(root.getChild("reload").canUse(nonOp), "reload should deny perm 0");
        helper.assertTrue(root.getChild("reload").canUse(op), "reload should allow perm 2");
        helper.assertFalse(root.getChild("set").canUse(nonOp), "set should deny perm 0");
        helper.assertTrue(root.getChild("set").canUse(op), "set should allow perm 2");
        CommandNode<CommandSourceStack> setGrade = root.getChild("set").getChild("grade");
        helper.assertTrue(setGrade != null && setGrade.getChild("prime") != null,
                "set grade exposes a leaf per grade name");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void setGradeMutatesTheLookedAtLivestock(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 1, 2, 1);
        com.rfizzle.instinct.genetics.GeneticsHandler.setGrade(cow, com.rfizzle.instinct.api.Grade.PRIME);
        helper.assertValueEqual(com.rfizzle.instinct.api.InstinctAPI.getGrade(cow),
                com.rfizzle.instinct.api.Grade.PRIME, "set grade writes the grade and re-derives bonuses");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "instinctConfig3")
    public void reloadReportsTheChangedKeyCount(GameTestHelper helper) {
        byte[] original;
        try {
            original = Files.readAllBytes(CONFIG_FILE);
        } catch (IOException e) {
            helper.fail("Could not read original config: " + e.getMessage());
            return;
        }
        try {
            InstinctConfig.reload(); // baseline from the file as it is on disk
            Files.writeString(CONFIG_FILE, """
                    { "configVersion": 1, "creeperBerthBlocks": 6, "troughRadiusBlocks": 12 }
                    """);
            int changed = InstinctConfig.reload();
            helper.assertValueEqual(changed, 2, "changed-key count for two edited keys");
            helper.assertValueEqual(InstinctConfig.get().creeperBerthBlocks, 6, "edit applied live");

            int unchanged = InstinctConfig.reload();
            helper.assertValueEqual(unchanged, 0, "reloading an unedited file changes nothing");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("IO error during test: " + e.getMessage());
        } finally {
            restoreConfig(original);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void infoReportsMembershipAndGrantingRule(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 1, 2, 1);
        List<Component> lines = InstinctCommand.infoLines(cow);
        List<String> keys = lines.stream().map(InstinctCommandGameTest::keyOf).toList();

        helper.assertTrue(keys.contains("command.instinct.info.header"), "report should open with the species header");
        helper.assertTrue(keys.contains("command.instinct.info.pets.non_member"), "a cow is not a pet");
        helper.assertTrue(keys.contains("command.instinct.info.livestock.member"), "a cow is livestock");
        helper.assertTrue(keys.contains("command.instinct.info.genetics"), "livestock report includes genetics");

        Component livestockLine = lines.get(keys.indexOf("command.instinct.info.livestock.member"));
        Object ruleArg = ((TranslatableContents) livestockLine.getContents()).getArgs()[0];
        helper.assertTrue(ruleArg instanceof Component rule
                        && "command.instinct.info.rule.tag".equals(keyOf((Component) rule)),
                "the cow's livestock membership should be granted by the tag layer");
        helper.succeed();
    }

    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : "";
    }

    private static void restoreConfig(byte[] original) {
        try {
            Files.write(CONFIG_FILE, original);
            InstinctConfig.reload();
        } catch (IOException e) {
            com.rfizzle.instinct.Instinct.LOGGER.error("Failed to restore config", e);
        }
    }
}
