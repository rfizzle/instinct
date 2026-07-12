package com.rfizzle.instinct.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * ModMenu entrypoint. Only ModMenu itself ever class-loads this, and the Cloth-built screen lives
 * in {@link ClothConfigScreenBuilder} behind the {@code isModLoaded} guard, so ModMenu-without-Cloth
 * degrades to "no config screen" instead of resolving Cloth's classes and crashing.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> null;
        }
        return ClothConfigScreenBuilder::build;
    }
}
