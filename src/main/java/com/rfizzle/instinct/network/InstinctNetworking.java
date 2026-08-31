package com.rfizzle.instinct.network;

import com.rfizzle.instinct.whistle.WhistleLocatePayload;
import com.rfizzle.instinct.whistle.WhistleTogglePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * The single place Instinct's wire surface is declared (mc-networking): every custom payload type
 * is registered here, called from {@link com.rfizzle.instinct.Instinct#onInitialize()} after the
 * config load. Receivers stay with the feature that owns them — the whistle's C2S handlers are
 * wired in {@link com.rfizzle.instinct.whistle.Whistle#register()}.
 */
public final class InstinctNetworking {

    private InstinctNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(WhistleTogglePayload.TYPE, WhistleTogglePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhistleLocatePayload.TYPE, WhistleLocatePayload.CODEC);
    }
}
