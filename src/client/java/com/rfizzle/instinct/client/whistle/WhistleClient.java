package com.rfizzle.instinct.client.whistle;

import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.whistle.WhistleTogglePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.world.phys.HitResult;

/**
 * The command whistle's client wiring ({@code design/SPEC.md} §6). A left-click on air produces no
 * vanilla server event, so this reports the swing: while the whistle is in the main hand and the
 * crosshair rests on nothing (a block or entity left-click routes through the server attack
 * callbacks instead), it sends the empty {@code instinct:whistle_toggle} payload. The callback
 * fires every tick the attack key is held, so it gates on {@code clickCount != 0} — the leading
 * edge of a click — and the server re-validates and dedupes against the item cooldown. The swing
 * itself is never cancelled here.
 */
public final class WhistleClient {

    private WhistleClient() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount != 0
                    && player.getMainHandItem().is(InstinctItems.COMMAND_WHISTLE)
                    && (client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS)) {
                ClientPlayNetworking.send(new WhistleTogglePayload());
            }
            return false;
        });
    }
}
