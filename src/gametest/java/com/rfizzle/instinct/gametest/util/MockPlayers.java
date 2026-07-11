package com.rfizzle.instinct.gametest.util;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.util.UUID;

/**
 * Gametest mock-player factory. {@code GameTestHelper#makeMockServerPlayerInLevel()} is
 * deprecated for removal in 1.21.1 with no vanilla replacement; this replica reproduces its
 * construction faithfully with public, non-deprecated APIs: a real {@link Connection} backed by
 * an {@link EmbeddedChannel} (absorbs sent packets), registered through
 * {@code PlayerList#placeNewPlayer} so the player is in the player list and the level, forced
 * {@code !isSpectator()}/{@code isCreative()} as the vanilla factory did.
 * {@code MockPlayersGameTest} guards this faithfulness. Callers must {@code discard()} the
 * player when done — both factories spawn near world spawn, not in the test structure.
 */
public final class MockPlayers {

    private MockPlayers() {
    }

    public static ServerPlayer serverPlayerInLevel(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
