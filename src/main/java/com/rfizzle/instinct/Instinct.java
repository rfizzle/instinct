package com.rfizzle.instinct;

import com.rfizzle.instinct.command.InstinctCommand;
import com.rfizzle.instinct.boating.Boating;
import com.rfizzle.instinct.compat.tribulation.TribulationCompat;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.downed.CarryHandler;
import com.rfizzle.instinct.downed.DownedHandler;
import com.rfizzle.instinct.friendlyfire.FriendlyFireHandler;
import com.rfizzle.instinct.genetics.GeneticsHandler;
import com.rfizzle.instinct.guard.Guard;
import com.rfizzle.instinct.herding.Herding;
import com.rfizzle.instinct.inspection.Inspection;
import com.rfizzle.instinct.keepsake.KeepsakeHandler;
import com.rfizzle.instinct.predatorwatch.PredatorWatch;
import com.rfizzle.instinct.registry.InstinctBlockEntities;
import com.rfizzle.instinct.registry.InstinctBlocks;
import com.rfizzle.instinct.registry.InstinctCriteria;
import com.rfizzle.instinct.registry.InstinctDataComponents;
import com.rfizzle.instinct.registry.InstinctItems;
import com.rfizzle.instinct.registry.InstinctSounds;
import com.rfizzle.instinct.selfpreservation.SelfPreservation;
import com.rfizzle.instinct.trough.Trough;
import com.rfizzle.instinct.veterancy.VeterancyHandler;
import com.rfizzle.instinct.whistle.Whistle;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
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
        InstinctBlocks.register();
        InstinctBlockEntities.register();
        InstinctDataComponents.register();
        InstinctItems.register();
        InstinctSounds.register();
        InstinctCriteria.register();
        AnimalCoverage.register();
        SelfPreservation.register();
        FriendlyFireHandler.register();
        VeterancyHandler.register();
        GeneticsHandler.register();
        Herding.register();
        Boating.register();
        PredatorWatch.register();
        Guard.register();
        CarryHandler.register();
        DownedHandler.register();
        KeepsakeHandler.register();
        Trough.register();
        Whistle.register();
        Inspection.register();
        InstinctCommand.init();
        // Sibling integrations — soft, guarded, class-loaded only when the target is present.
        if (FabricLoader.getInstance().isModLoaded("tribulation")) {
            TribulationCompat.register();
        }
        LOGGER.info("Instinct initialized");
    }
}
