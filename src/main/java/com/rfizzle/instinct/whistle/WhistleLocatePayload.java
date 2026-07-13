package com.rfizzle.instinct.whistle;

import com.rfizzle.instinct.Instinct;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The {@code instinct:whistle_locate} client-to-server payload ({@code design/SPEC.md} §6). A
 * sneak + left-click on air fires no vanilla server event, so the client reports the gesture with
 * this empty-body packet; the server re-validates (main-hand whistle, off cooldown, feature enabled)
 * before answering with the lost-pet census. Carries no fields — the gesture is the whole message —
 * so its codec is a unit.
 */
public record WhistleLocatePayload() implements CustomPacketPayload {

    public static final Type<WhistleLocatePayload> TYPE = new Type<>(Instinct.id("whistle_locate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhistleLocatePayload> CODEC =
            StreamCodec.unit(new WhistleLocatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
