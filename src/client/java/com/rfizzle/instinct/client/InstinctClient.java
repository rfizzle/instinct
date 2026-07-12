package com.rfizzle.instinct.client;

import com.rfizzle.instinct.client.whistle.WhistleClient;
import net.fabricmc.api.ClientModInitializer;

public class InstinctClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WhistleClient.register();
    }
}
