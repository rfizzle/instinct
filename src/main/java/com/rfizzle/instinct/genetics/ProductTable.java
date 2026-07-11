package com.rfizzle.instinct.genetics;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.data.InstinctItemTagProvider;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * The genetics product data ({@code design/SPEC.md} §Animal Coverage — genetics product data): each
 * covered species' primary/secondary death-drop products, loaded from
 * {@code data/<ns>/instinct/products/*.json} on server start and every {@code /reload}. A species
 * without a row falls back to the drop mirror. Vanilla rows ship in the jar as the bottom of the
 * pack stack; a pack or animal mod adds rows the same way. Rows naming an unknown entity or item
 * id are skipped and logged once at debug (a curated row for an absent mod stays inert).
 */
public final class ProductTable {

    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "instinct/products";

    /** The published table, keyed by entity id. Rebuilt whole and swapped in one volatile write. */
    private static volatile Map<ResourceLocation, ProductRow> ROWS = Map.of();

    private ProductTable() {
    }

    /**
     * A species' product row. {@code primaryCooked} mirrors whether the vanilla drop was cooked;
     * {@code woolCoat} (the {@code "special": "wool_coat"} case) resolves the secondary to the
     * sheep's coat-color wool at drop time. Any product may be absent (a goat row carries none).
     */
    public record ProductRow(Optional<Item> primary, Optional<Item> primaryCooked,
                             Optional<Item> secondary, boolean woolCoat) {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Instinct.id("products");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        load(manager);
                    }
                });
    }

    /** The product row for a type, or {@code null} if none is defined (mirror-fallback territory). */
    public static ProductRow rowFor(EntityType<?> type) {
        return ROWS.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /**
     * Builds the bonus death drops for a covered animal. A species with a product row uses it
     * (primary count by grade, cooked-in-kind when {@code cooked}, secondary count by grade); a
     * species without one mirrors its own drops when {@code mirrorEnabled}. Returns the bonus
     * stacks to spawn beside the vanilla loot — empty when there is nothing to add.
     */
    public static List<ItemStack> bonusDrops(Animal animal, int grade, boolean cooked,
                                             List<ItemStack> deathDrops, boolean mirrorEnabled,
                                             DoubleSupplier roll) {
        if (grade <= 0) {
            return List.of();
        }
        ProductRow row = rowFor(animal.getType());
        if (row != null) {
            return dataBonus(animal, row, grade, cooked, roll);
        }
        return mirrorEnabled ? mirrorBonus(deathDrops, grade, roll) : List.of();
    }

    /** The data-path bonus for a species with a product row. {@code animal} is used only for wool-coat. */
    static List<ItemStack> dataBonus(Animal animal, ProductRow row, int grade, boolean cooked,
                                     DoubleSupplier roll) {
        List<ItemStack> bonus = new ArrayList<>(2);
        Item primary = cooked && row.primaryCooked().isPresent()
                ? row.primaryCooked().get() : row.primary().orElse(null);
        int primaryCount = Genetics.primaryBonus(grade);
        if (primary != null && primaryCount > 0) {
            bonus.add(new ItemStack(primary, primaryCount));
        }
        int secondaryCount = Genetics.secondaryBonus(grade, roll);
        if (secondaryCount > 0) {
            Item secondary = row.woolCoat() ? woolFor(animal) : row.secondary().orElse(null);
            if (secondary != null) {
                bonus.add(new ItemStack(secondary, secondaryCount));
            }
        }
        return bonus;
    }

    /**
     * The mirror fallback ({@code design/SPEC.md} §Animal Coverage): the largest candidate death
     * drop (edible or in {@code #instinct:mirror_products}) is treated as the primary product, the
     * second-largest as the secondary. Deterministic given the drop roll; no candidates → no bonus.
     */
    static List<ItemStack> mirrorBonus(List<ItemStack> deathDrops, int grade, DoubleSupplier roll) {
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack stack : deathDrops) {
            if (!stack.isEmpty() && isMirrorCandidate(stack)) {
                candidates.add(stack);
            }
        }
        // Largest first; ties broken by registry id so the choice is stable across runs.
        candidates.sort(Comparator.comparingInt(ItemStack::getCount).reversed()
                .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())));
        List<ItemStack> bonus = new ArrayList<>(2);
        int primaryCount = Genetics.primaryBonus(grade);
        if (!candidates.isEmpty() && primaryCount > 0) {
            bonus.add(new ItemStack(candidates.get(0).getItem(), primaryCount));
        }
        int secondaryCount = Genetics.secondaryBonus(grade, roll);
        if (candidates.size() > 1 && secondaryCount > 0) {
            bonus.add(new ItemStack(candidates.get(1).getItem(), secondaryCount));
        }
        return bonus;
    }

    /** A drop the mirror may duplicate: edible, or tagged {@code #instinct:mirror_products}. */
    static boolean isMirrorCandidate(ItemStack stack) {
        return stack.has(DataComponents.FOOD) || stack.is(InstinctItemTagProvider.MIRROR_PRODUCTS);
    }

    private static Item woolFor(Animal animal) {
        if (animal instanceof Sheep sheep) {
            ItemLike wool = Sheep.ITEM_BY_DYE.get(sheep.getColor());
            return wool != null ? wool.asItem() : Items.WHITE_WOOL;
        }
        return null;
    }

    static void load(ResourceManager manager) {
        Map<ResourceLocation, ProductRow> next = new java.util.HashMap<>();
        Map<ResourceLocation, List<Resource>> found = manager.listResourceStacks(
                DATA_PATH, id -> id.getPath().endsWith(".json"));
        int rows = 0;
        for (Map.Entry<ResourceLocation, List<Resource>> fileEntry : found.entrySet()) {
            ResourceLocation fileId = fileEntry.getKey();
            for (Resource resource : fileEntry.getValue()) { // lowest pack first → higher packs win
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) {
                        continue;
                    }
                    ResourceLocation entity = parseEntity(json, fileId);
                    ProductRow parsed = parseRow(json, fileId);
                    if (entity != null && parsed != null) {
                        if (next.put(entity, parsed) == null) {
                            rows++;
                        }
                    }
                } catch (Exception e) {
                    Instinct.LOGGER.error("Failed to load genetics product file {}", fileId, e);
                }
            }
        }
        ROWS = Map.copyOf(next);
        Instinct.LOGGER.info("Loaded {} genetics product rows", rows);
    }

    /** The entity id a row is keyed by, or {@code null} if absent or unknown (skipped at debug). */
    static ResourceLocation parseEntity(JsonObject json, ResourceLocation fileId) {
        JsonElement entityEl = json.get("entity");
        if (entityEl == null || !entityEl.isJsonPrimitive()) {
            Instinct.LOGGER.debug("Genetics product row {} has no entity id; skipping", fileId);
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityEl.getAsString());
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            Instinct.LOGGER.debug("Genetics product row {} names unknown entity {}; skipping",
                    fileId, entityEl.getAsString());
            return null;
        }
        return id;
    }

    /**
     * Parses a product row's item fields. A named-but-unknown item id skips the whole row (the
     * mod is absent). {@code "special": "wool_coat"} replaces the secondary with the coat-color
     * case. Static and Fabric-free so the parse layer tests at Tier 2. Returns {@code null} on a
     * skip.
     */
    static ProductRow parseRow(JsonObject json, ResourceLocation fileId) {
        Optional<Item> primary = resolveItem(json, "primary", fileId);
        if (primary == null) {
            return null;
        }
        Optional<Item> primaryCooked = resolveItem(json, "primary_cooked", fileId);
        if (primaryCooked == null) {
            return null;
        }
        boolean woolCoat = json.has("special")
                && "wool_coat".equals(json.get("special").getAsString());
        Optional<Item> secondary = Optional.empty();
        if (!woolCoat) {
            secondary = resolveItem(json, "secondary", fileId);
            if (secondary == null) {
                return null;
            }
        }
        return new ProductRow(primary, primaryCooked, secondary, woolCoat);
    }

    /**
     * Resolves an optional item field: {@code Optional.empty()} when the key is absent,
     * {@code Optional.of(item)} when it resolves, and {@code null} (skip the row) when the key is
     * present but names an unknown item.
     */
    private static Optional<Item> resolveItem(JsonObject json, String key, ResourceLocation fileId) {
        if (!json.has(key)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(json.get(key).getAsString());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            Instinct.LOGGER.debug("Genetics product row {} names unknown item {} for {}; skipping",
                    fileId, json.get(key).getAsString(), key);
            return null;
        }
        return Optional.of(BuiltInRegistries.ITEM.get(id));
    }
}
