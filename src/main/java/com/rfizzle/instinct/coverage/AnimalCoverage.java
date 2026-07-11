package com.rfizzle.instinct.coverage;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Animal Coverage shell: gathers the per-type layer facts (config lists, {@code #instinct:*}
 * entity-type tags, heuristic capability) and hands them to {@link CoverageResolver}. Config and
 * tag layers are evaluated live on every query — both are hot-reloadable and the lookups are
 * cheap — so only the immutable Java-class capability is cached.
 *
 * <p>The capability cache is seeded once per server start by probing every registered entity type
 * ({@code EntityType#create} + {@code instanceof}, instance discarded, never added to the world;
 * a failing modded constructor degrades that one type to {@link AnimalCapability#NONE}), and
 * corrected opportunistically from real instances on entity load. It is cleared on server stop so
 * nothing leaks across worlds in the same JVM.
 */
public final class AnimalCoverage {

    public static final TagKey<EntityType<?>> PETS_TAG = tag("pets");
    public static final TagKey<EntityType<?>> PETS_EXCLUDE_TAG = tag("pets_exclude");
    public static final TagKey<EntityType<?>> LIVESTOCK_TAG = tag("livestock");
    public static final TagKey<EntityType<?>> LIVESTOCK_EXCLUDE_TAG = tag("livestock_exclude");

    private static final Map<EntityType<?>, AnimalCapability> CAPABILITIES = new ConcurrentHashMap<>();

    private AnimalCoverage() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(AnimalCoverage::seedCapabilities);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CAPABILITIES.clear());
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            try {
                learn(entity);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to learn coverage capability for {}", entity.getType(), e);
            }
        });
    }

    /** Pets-set membership for a type, after full config → tag → heuristic resolution. */
    public static boolean isPet(EntityType<?> type) {
        return membershipOf(type).pet();
    }

    /** Livestock-set membership for a type, after full config → tag → heuristic resolution. */
    public static boolean isLivestock(EntityType<?> type) {
        return membershipOf(type).livestock();
    }

    /** Resolves a type's membership from the current config, bound tags, and capability cache. */
    public static CoverageResolver.Membership membershipOf(EntityType<?> type) {
        InstinctConfig config = InstinctConfig.get();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        AnimalCapability capability = CAPABILITIES.getOrDefault(type, AnimalCapability.NONE);
        return CoverageResolver.resolve(new CoverageResolver.Layers(
                config.petsExcludeSet.contains(id),
                config.petsIncludeSet.contains(id),
                config.livestockExcludeSet.contains(id),
                config.livestockIncludeSet.contains(id),
                type.is(PETS_EXCLUDE_TAG),
                type.is(PETS_TAG),
                type.is(LIVESTOCK_EXCLUDE_TAG),
                type.is(LIVESTOCK_TAG),
                config.autoDetectAnimals,
                capability));
    }

    /**
     * Resolves membership for a live entity, learning its capability first — the preferred entry
     * point wherever an instance exists, since it never depends on the probe having succeeded.
     */
    public static CoverageResolver.Membership membershipOf(Entity entity) {
        learn(entity);
        return membershipOf(entity.getType());
    }

    /** Records an observed instance's capability, correcting a failed or missing probe result. */
    private static void learn(Entity entity) {
        AnimalCapability observed = capabilityOf(entity);
        if (CAPABILITIES.get(entity.getType()) != observed) {
            CAPABILITIES.put(entity.getType(), observed);
        }
    }

    private static AnimalCapability capabilityOf(Entity entity) {
        if (entity instanceof TamableAnimal) {
            return AnimalCapability.TAMABLE;
        }
        if (entity instanceof Animal) {
            return AnimalCapability.BREEDABLE;
        }
        return AnimalCapability.NONE;
    }

    /**
     * Probes every registered entity type once so {@code isPet}/{@code isLivestock} answer for
     * types no instance of which has loaded yet. One throwaway instance per type, on the server
     * thread, error-isolated per type.
     */
    private static void seedCapabilities(MinecraftServer server) {
        int probed = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (CAPABILITIES.containsKey(type)) {
                continue;
            }
            AnimalCapability capability = AnimalCapability.NONE;
            try {
                Entity probe = type.create(server.overworld());
                if (probe != null) {
                    capability = capabilityOf(probe);
                    probe.discard();
                }
            } catch (Throwable t) {
                Instinct.LOGGER.debug("Could not probe entity type {} for coverage; treating as uncovered",
                        BuiltInRegistries.ENTITY_TYPE.getKey(type), t);
            }
            CAPABILITIES.put(type, capability);
            probed++;
        }
        Instinct.LOGGER.debug("Animal coverage probed {} entity types", probed);
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Instinct.id(path));
    }
}
