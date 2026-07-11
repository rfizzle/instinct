package com.rfizzle.instinct.gametest;

import com.rfizzle.instinct.gametest.util.MockPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Faithfulness guard for {@link MockPlayers#serverPlayerInLevel}: a later "simplification" to a
 * bare {@code new ServerPlayer(...)} must fail here loudly instead of silently breaking every
 * connection-dependent test.
 */
public class MockPlayersGameTest implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE)
    public void replicaIsConnectedRegisteredAndCreative(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            helper.assertTrue(player.connection != null, "replica should have a live connection");
            helper.assertTrue(player.getServer().getPlayerList().getPlayers().contains(player),
                    "replica should be registered in the player list");
            helper.assertTrue(player.level() == helper.getLevel(), "replica should be in the test level");
            helper.assertTrue(player.isCreative(), "replica should report creative");
            helper.assertFalse(player.isSpectator(), "replica should not report spectator");
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
