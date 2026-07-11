package com.rfizzle.instinct;

import com.rfizzle.instinct.command.InstinctCommand;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import com.rfizzle.instinct.inspection.Inspection;
import com.rfizzle.instinct.registry.InstinctCriteria;
import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.registry.InstinctSounds;
import com.rfizzle.instinct.selfpreservation.SelfPreservation;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
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
        InstinctItems.register();
        InstinctSounds.register();
        InstinctCriteria.register();
        AnimalCoverage.register();
        SelfPreservation.register();
        VeterancyHandler.register();
        GeneticsHandler.register();
        Inspection.register();
        InstinctCommand.init();
        LOGGER.info("Instinct initialized");
    }
}
