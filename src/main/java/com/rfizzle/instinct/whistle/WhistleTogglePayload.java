package com.rfizzle.instinct.whistle;

import com.rfizzle.instinct.Instinct;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The {@code instinct:whistle_toggle} client-to-server payload ({@code design/SPEC.md} §6). A
 * left-click on air fires no vanilla server event, so the client reports the swing with this
 * empty-body packet; the server re-validates (main-hand whistle, off cooldown, feature enabled)
 * before toggling. Carries no fields — the gesture is the whole message — so its codec is a unit.
 */
public record WhistleTogglePayload() implements CustomPacketPayload {

    public static final Type<WhistleTogglePayload> TYPE = new Type<>(Instinct.id("whistle_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhistleTogglePayload> CODEC =
            StreamCodec.unit(new WhistleTogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
