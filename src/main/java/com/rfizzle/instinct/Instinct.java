package com.rfizzle.instinct;

import com.rfizzle.instinct.command.InstinctCommand;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.InstinctAttachments;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Instinct implements ModInitializer {
    public static final String MOD_ID = "instinct";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        InstinctConfig.init();
        InstinctAttachments.init();
        AnimalCoverage.register();
        InstinctCommand.init();
        LOGGER.info("Instinct initialized");
    }
}
