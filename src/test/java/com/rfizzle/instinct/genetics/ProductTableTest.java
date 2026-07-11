package com.rfizzle.instinct.genetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The genetics product data ({@code design/SPEC.md} §Animal Coverage): row parsing (optional fields,
 * {@code wool_coat}, unknown-id skips), the per-grade data-path drop counts including cooked-in-kind,
 * and the mirror-fallback candidate selection and ordering. Tier 2 — vanilla registries are needed
 * to resolve item ids, but no server or Fabric runtime.
 */
class ProductTableTest {

    private static final ResourceLocation FILE = ResourceLocation.parse("instinct:instinct/products/test.json");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static DoubleSupplier rolls(double... values) {
        Deque<Double> queue = new ArrayDeque<>();
        for (double v : values) {
            queue.add(v);
        }
        return () -> queue.size() > 1 ? queue.poll() : queue.peek();
    }

    // ---- parseEntity ----

    @Test
    void parseEntitySkipsMissingAndUnknownIds() {
        assertNull(ProductTable.parseEntity(json("{}"), FILE), "no entity field");
        assertNull(ProductTable.parseEntity(json("{\"entity\":\"minecraft:not_a_mob\"}"), FILE), "unknown entity");
        assertEquals(ResourceLocation.parse("minecraft:cow"),
                ProductTable.parseEntity(json("{\"entity\":\"minecraft:cow\"}"), FILE));
    }

    // ---- parseRow ----

    @Test
    void parseRowReadsAllFields() {
        ProductTable.ProductRow row = ProductTable.parseRow(json(
                "{\"entity\":\"minecraft:cow\",\"primary\":\"minecraft:beef\","
                        + "\"primary_cooked\":\"minecraft:cooked_beef\",\"secondary\":\"minecraft:leather\"}"), FILE);
        assertEquals(Items.BEEF, row.primary().orElseThrow());
        assertEquals(Items.COOKED_BEEF, row.primaryCooked().orElseThrow());
        assertEquals(Items.LEATHER, row.secondary().orElseThrow());
        assertFalse(row.woolCoat());
    }

    @Test
    void parseRowHonorsWoolCoatSpecialAndDropsTheSecondary() {
        ProductTable.ProductRow row = ProductTable.parseRow(json(
                "{\"entity\":\"minecraft:sheep\",\"primary\":\"minecraft:mutton\","
                        + "\"primary_cooked\":\"minecraft:cooked_mutton\",\"special\":\"wool_coat\"}"), FILE);
        assertTrue(row.woolCoat());
        assertTrue(row.secondary().isEmpty(), "wool_coat resolves the secondary dynamically, not from JSON");
    }

    @Test
    void parseRowLeavesAbsentOptionalsEmpty() {
        ProductTable.ProductRow goat = ProductTable.parseRow(json("{\"entity\":\"minecraft:goat\"}"), FILE);
        assertTrue(goat.primary().isEmpty() && goat.primaryCooked().isEmpty() && goat.secondary().isEmpty());
        assertFalse(goat.woolCoat());
    }

    @Test
    void parseRowSkipsAnUnknownItemId() {
        assertNull(ProductTable.parseRow(json(
                "{\"entity\":\"minecraft:cow\",\"primary\":\"othermod:phantom_meat\"}"), FILE),
                "a named-but-unknown item skips the whole row");
    }

    // ---- data-path counts ----

    @Test
    void dataBonusCountsPrimaryAndSecondaryByGrade() {
        ProductTable.ProductRow cow = new ProductTable.ProductRow(
                java.util.Optional.of(Items.BEEF), java.util.Optional.of(Items.COOKED_BEEF),
                java.util.Optional.of(Items.LEATHER), false);

        List<ItemStack> prime = ProductTable.dataBonus(null, cow, 2, false, rolls(0.99));
        assertEquals(2, primaryCount(prime, Items.BEEF), "prime = +2 beef");
        assertEquals(1, primaryCount(prime, Items.LEATHER), "prime = +1 leather");

        List<ItemStack> sturdyHit = ProductTable.dataBonus(null, cow, 1, false, rolls(0.49));
        assertEquals(1, primaryCount(sturdyHit, Items.BEEF), "sturdy = +1 beef");
        assertEquals(1, primaryCount(sturdyHit, Items.LEATHER), "sturdy = +1 leather on a sub-0.5 roll");

        List<ItemStack> sturdyMiss = ProductTable.dataBonus(null, cow, 1, false, rolls(0.5));
        assertEquals(1, primaryCount(sturdyMiss, Items.BEEF));
        assertEquals(0, primaryCount(sturdyMiss, Items.LEATHER), "sturdy secondary is a 50% chance");
    }

    @Test
    void dataBonusCooksThePrimaryInKindWhenBurning() {
        ProductTable.ProductRow pig = new ProductTable.ProductRow(
                java.util.Optional.of(Items.PORKCHOP), java.util.Optional.of(Items.COOKED_PORKCHOP),
                java.util.Optional.empty(), false);
        List<ItemStack> cooked = ProductTable.dataBonus(null, pig, 2, true, rolls(0.0));
        assertEquals(2, primaryCount(cooked, Items.COOKED_PORKCHOP), "burning → cooked in kind");
        assertEquals(0, primaryCount(cooked, Items.PORKCHOP));
    }

    @Test
    void goatRowYieldsNothing() {
        ProductTable.ProductRow goat = new ProductTable.ProductRow(
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), false);
        assertTrue(ProductTable.dataBonus(null, goat, 2, false, rolls(0.0)).isEmpty(),
                "a goat carries genetics but no products");
    }

    // ---- mirror fallback ----

    @Test
    void mirrorPicksLargestAsPrimarySecondLargestAsSecondary() {
        // Edible candidates only: the #instinct:mirror_products tag branch needs a datapack load
        // (a gametest), so this pins the size ordering with foods that resolve at Tier 2.
        List<ItemStack> drops = List.of(
                new ItemStack(Items.PORKCHOP, 1),      // edible, smaller
                new ItemStack(Items.BEEF, 3),          // edible, largest
                new ItemStack(Items.STICK, 9));        // not edible, not tagged → not a candidate
        List<ItemStack> bonus = ProductTable.mirrorBonus(drops, 2, rolls(0.0));
        assertEquals(2, primaryCount(bonus, Items.BEEF), "largest candidate → primary, prime +2");
        assertEquals(1, primaryCount(bonus, Items.PORKCHOP), "second-largest candidate → secondary");
        assertEquals(0, primaryCount(bonus, Items.STICK), "a non-candidate is never duplicated");
    }

    @Test
    void mirrorYieldsNothingWithoutCandidates() {
        List<ItemStack> drops = List.of(new ItemStack(Items.STICK, 4), new ItemStack(Items.STRING, 2));
        assertTrue(ProductTable.mirrorBonus(drops, 2, rolls(0.0)).isEmpty());
    }

    @Test
    void mirrorCandidacyIncludesEdibleItems() {
        assertTrue(new ItemStack(Items.BEEF).has(DataComponents.FOOD), "precondition: beef is edible");
        assertTrue(ProductTable.isMirrorCandidate(new ItemStack(Items.BEEF)));
        assertFalse(ProductTable.isMirrorCandidate(new ItemStack(Items.STICK)));
    }

    private static int primaryCount(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
