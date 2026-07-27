package com.rfizzle.instinct.coverage;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.config.InstinctConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Animal Coverage shell: gathers the per-type layer facts (config lists, {@code #instinct:*}
 * entity-type tags, heuristic capability) and hands them to {@link CoverageResolver}. Config and
 * tag layers are evaluated live on every query — both are hot-reloadable and the lookups are
 * cheap — so only the immutable Java-class capability is cached.
 *
 * <p>{@link #isPet}, {@link #isLivestock}, and {@link #isMount} answer a single set's question
 * without building the full record, which is what the goals, handlers, and per-tick gates that make
 * up most callers actually need; {@link #membershipOf} is for the few places that report all three
 * sets and the rule that decided each. Both run the same {@link CoverageResolver} ladder.
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
    public static final TagKey<EntityType<?>> MOUNTS_TAG = tag("mounts");
    public static final TagKey<EntityType<?>> MOUNTS_EXCLUDE_TAG = tag("mounts_exclude");
    /** Wild predators a guardian pet watches for (§8 Predator Watch). Membership is the tag plus
     *  {@code predatorsInclude}, minus {@code predatorsExclude}; there is no heuristic layer — a
     *  predator is only ever what the tag or config names. */
    public static final TagKey<EntityType<?>> PREDATORS_TAG = tag("predators");
    /** Small pets that can be scooped up and carried while downed (§7 Carry). A downed pets-set
     *  animal is carryable when it is a baby (a pup) or its type is in this tag; ships cat and
     *  parrot. Full-size pets and every mount stay where they fall. A mod adds its own small pets
     *  via this tag. */
    public static final TagKey<EntityType<?>> CARRYABLE_TAG = tag("carryable");

    private static final Map<EntityType<?>, AnimalCapability> CAPABILITIES = new ConcurrentHashMap<>();
    private static final Map<String, EntityType<?>> TYPES_BY_ID = new ConcurrentHashMap<>();

    /**
     * How many ids that resolve to nothing {@link #UNRESOLVED_IDS} will hold. Sized for the two
     * shoulder slots of each of a handful of players carrying a stale rider at once, which is the
     * access pattern that makes a repeated non-resolution worth remembering at all.
     */
    static final int UNRESOLVED_ID_CAP = 8;

    /**
     * Ids already found to name no registered type. Kept apart from {@link #TYPES_BY_ID} because
     * the two answer to different rules: the memo's keys come from the registry and it is bounded
     * by construction, while these come from save data and are bounded only by the cap.
     */
    private static final Set<String> UNRESOLVED_IDS = ConcurrentHashMap.newKeySet();

    /**
     * Guards the cap on {@link #UNRESOLVED_IDS}. A lock of its own rather than the set's own
     * monitor, so nothing reads as though the set's other operations took part in it: they do not,
     * and only the admit needs ordering.
     */
    private static final Object UNRESOLVED_LOCK = new Object();

    /**
     * The longest id worth a slot in {@link #UNRESOLVED_IDS} — past any {@code namespace:path} a
     * registry could answer to, so nothing that might resolve is turned away.
     */
    static final int MAX_UNRESOLVED_ID_LENGTH = 256;

    /**
     * How many times {@link #typeById} has reached a real parse. The seam for the property this
     * whole cache exists for — that re-asking about the same id is cheap — which neither set's size
     * can show, since both look identical whether the id was remembered or parsed afresh.
     */
    private static final AtomicInteger RESOLVE_ATTEMPTS = new AtomicInteger();

    private AnimalCoverage() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(AnimalCoverage::seedCapabilities);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CAPABILITIES.clear();
            clearTypeMemo();
        });
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
        InstinctConfig config = InstinctConfig.get();
        return inSet(type, config.petsExcludeSet, config.petsIncludeSet, PETS_EXCLUDE_TAG, PETS_TAG,
                config.autoDetectAnimals && capabilityOf(type) == AnimalCapability.TAMABLE);
    }

    /** Livestock-set membership for a type, after full config → tag → heuristic resolution. */
    public static boolean isLivestock(EntityType<?> type) {
        InstinctConfig config = InstinctConfig.get();
        return inSet(type, config.livestockExcludeSet, config.livestockIncludeSet,
                LIVESTOCK_EXCLUDE_TAG, LIVESTOCK_TAG,
                config.autoDetectAnimals && capabilityOf(type) == AnimalCapability.BREEDABLE);
    }

    /** Mounts-set membership for a type, after full config → tag → heuristic resolution. */
    public static boolean isMount(EntityType<?> type) {
        InstinctConfig config = InstinctConfig.get();
        return inSet(type, config.mountsExcludeSet, config.mountsIncludeSet,
                MOUNTS_EXCLUDE_TAG, MOUNTS_TAG,
                config.autoDetectAnimals && capabilityOf(type) == AnimalCapability.MOUNT);
    }

    /** Pets-set membership for a live entity, learning its capability first. */
    public static boolean isPet(Entity entity) {
        learn(entity);
        return isPet(entity.getType());
    }

    /** Livestock-set membership for a live entity, learning its capability first. */
    public static boolean isLivestock(Entity entity) {
        learn(entity);
        return isLivestock(entity.getType());
    }

    /** Mounts-set membership for a live entity, learning its capability first. */
    public static boolean isMount(Entity entity) {
        learn(entity);
        return isMount(entity.getType());
    }

    /**
     * Runs {@link CoverageResolver#inSet} for one set without materializing the {@code Layers} and
     * {@code Membership} records the full resolve builds. The registry id backing the config lookups
     * is only built when that set actually has config entries to match against — with the shipped
     * empty lists (the common case) a membership question costs two tag checks and no allocation.
     */
    private static boolean inSet(EntityType<?> type, Set<String> configExclude, Set<String> configInclude,
                                 TagKey<EntityType<?>> excludeTag, TagKey<EntityType<?>> includeTag,
                                 boolean heuristic) {
        String id = configExclude.isEmpty() && configInclude.isEmpty() ? null : idOf(type);
        return CoverageResolver.inSet(
                id != null && configExclude.contains(id),
                id != null && configInclude.contains(id),
                type.is(excludeTag),
                type.is(includeTag),
                heuristic);
    }

    /** Resolves a type's membership from the current config, bound tags, and capability cache. */
    public static CoverageResolver.Membership membershipOf(EntityType<?> type) {
        InstinctConfig config = InstinctConfig.get();
        String id = idOf(type);
        AnimalCapability capability = capabilityOf(type);
        return CoverageResolver.resolve(new CoverageResolver.Layers(
                config.petsExcludeSet.contains(id),
                config.petsIncludeSet.contains(id),
                config.livestockExcludeSet.contains(id),
                config.livestockIncludeSet.contains(id),
                config.mountsExcludeSet.contains(id),
                config.mountsIncludeSet.contains(id),
                type.is(PETS_EXCLUDE_TAG),
                type.is(PETS_TAG),
                type.is(LIVESTOCK_EXCLUDE_TAG),
                type.is(LIVESTOCK_TAG),
                type.is(MOUNTS_EXCLUDE_TAG),
                type.is(MOUNTS_TAG),
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

    /**
     * Resolves a stored entity-type id (the {@code "id"} of a serialized entity tag) to its
     * registered type, or {@code null} when the id is absent, malformed, or names no registered
     * type. Memoized: the id → type mapping is fixed for the life of the process, since the
     * entity-type registry is populated at bootstrap and is not datapack-reloadable. Only ids that
     * actually resolve are stored, so a save-edited tag carrying junk cannot grow the map.
     *
     * <p>Deliberately routed through {@code getOptional}: {@code BuiltInRegistries.ENTITY_TYPE} is
     * a defaulted registry, so plain {@code get} answers an unknown id with {@code minecraft:pig}
     * rather than nothing, which would silently resolve a bogus tag into a real animal.
     *
     * <p>The memo keys on the stored id string rather than the parsed {@link ResourceLocation},
     * so the probe comes before the parse — which is the whole point, since parsing is what the
     * hot path is here to avoid. A hand-edited tag spelling an id non-canonically therefore takes
     * its own entry; both entries resolve to the same type, and the registry bounds the total.
     *
     * <p>Naming nothing is as fixed as naming something, so an id that fails is remembered too —
     * the shoulder gates re-ask the same id every tick a bird is perched, and a stale rider whose
     * mod is gone would otherwise re-parse forever. That set is capped rather than memoized,
     * because its keys come from save data rather than from the registry: past {@link
     * #UNRESOLVED_ID_CAP} distinct failures an id simply parses again.
     *
     * <p>Safe from either thread that reaches it (the {@code aiStep} dismount branches vanilla
     * runs client-side as well as server-side): an entry exists only because some thread already
     * proved that exact id names no registered type, and a thread that misses a peer's write just
     * parses, which is the answer it would have reached anyway.
     */
    @Nullable
    public static EntityType<?> typeById(String id) {
        EntityType<?> memoized = TYPES_BY_ID.get(id);
        if (memoized != null) {
            return memoized;
        }
        if (UNRESOLVED_IDS.contains(id)) {
            return null;
        }
        RESOLVE_ATTEMPTS.incrementAndGet();
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            rememberUnresolved(id);
            return null;
        }
        EntityType<?> resolved = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (resolved != null) {
            TYPES_BY_ID.put(id, resolved);
        } else {
            rememberUnresolved(id);
        }
        return resolved;
    }

    /**
     * Admits an id to the unresolved set while it has room. The cap is checked twice on purpose:
     * the unlocked read keeps a full set off the lock altogether, so a world carrying more broken
     * ids than the cap holds pays nothing to be told so, while the locked read is what actually
     * bounds the set, since concurrent misses must not both pass a check only one of them should.
     * Only a miss reaches here, and a miss already pays for a parse, so neither check sits on the
     * repeat path this method exists to make cheap.
     *
     * <p>An id too long to name anything a registry could answer to is turned away rather than
     * given a slot. Shoulder tags reach a client over the network as well as off its own disk, and
     * a client connected to a remote server never fires the server-stop clear, so these keys are
     * worth treating as hostile even though the cap already bounds how many of them stick.
     */
    private static void rememberUnresolved(String id) {
        if (id.length() > MAX_UNRESOLVED_ID_LENGTH || UNRESOLVED_IDS.size() >= UNRESOLVED_ID_CAP) {
            return;
        }
        synchronized (UNRESOLVED_LOCK) {
            if (UNRESOLVED_IDS.size() < UNRESOLVED_ID_CAP) {
                UNRESOLVED_IDS.add(id);
            }
        }
    }

    /** How many ids the type memo currently holds — the growth guard's test seam. */
    static int memoizedTypeCount() {
        return TYPES_BY_ID.size();
    }

    /** How many failed ids are currently remembered — the cap's test seam. */
    static int unresolvedIdCount() {
        return UNRESOLVED_IDS.size();
    }

    /** How many lookups reached a real parse — the seam for the repeat path staying cheap. */
    static int typeResolveAttempts() {
        return RESOLVE_ATTEMPTS.get();
    }

    /** Drops every remembered id, resolved or not, so nothing carries across worlds in one JVM. */
    static void clearTypeMemo() {
        TYPES_BY_ID.clear();
        UNRESOLVED_IDS.clear();
        RESOLVE_ATTEMPTS.set(0);
    }

    /** Records an observed instance's capability, correcting a failed or missing probe result. */
    private static void learn(Entity entity) {
        AnimalCapability observed = capabilityOf(entity);
        if (CAPABILITIES.get(entity.getType()) != observed) {
            CAPABILITIES.put(entity.getType(), observed);
        }
    }

    private static AnimalCapability capabilityOf(EntityType<?> type) {
        return CAPABILITIES.getOrDefault(type, AnimalCapability.NONE);
    }

    private static String idOf(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private static AnimalCapability capabilityOf(Entity entity) {
        if (entity instanceof TamableAnimal) {
            return AnimalCapability.TAMABLE;
        }
        if (entity instanceof AbstractHorse) {
            return AnimalCapability.MOUNT;
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
