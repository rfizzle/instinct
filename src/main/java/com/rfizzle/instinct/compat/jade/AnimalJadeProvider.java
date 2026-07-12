package com.rfizzle.instinct.compat.jade;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.compat.common.AnimalProbeTooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Animal;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade adapter for the covered-animal tooltip — pure delegation to {@link AnimalProbeTooltip}, so
 * the lines match WTHIT's by construction.
 */
public enum AnimalJadeProvider implements IServerDataProvider<EntityAccessor>, IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        if (accessor.getEntity() instanceof Animal animal) {
            AnimalProbeTooltip.writeServerData(tag, animal);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        for (Component line : AnimalProbeTooltip.buildLines(accessor.getServerData())) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return Instinct.id("animal");
    }
}
