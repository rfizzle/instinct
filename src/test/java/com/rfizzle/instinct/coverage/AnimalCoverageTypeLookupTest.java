package com.rfizzle.instinct.coverage;

import net.minecraft.ResourceLocationException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code AnimalCoverage.typeById} is the memoized replacement for {@code EntityType.by(tag)} on the
 * shoulder path ({@code mc-mod-testing} tier 2: it reads the real entity-type registry, so it
 * bootstraps vanilla rather than mocking one). Two properties carry the risk. The memo must only
 * ever hold ids that resolve, or a save-edited shoulder tag could grow it without bound; and an
 * unknown id must read as nothing, because {@code BuiltInRegistries.ENTITY_TYPE} is a defaulted
 * registry whose plain {@code get} answers {@code minecraft:pig} — which would quietly turn a bogus
 * tag into a real pets-set animal.
 */
class AnimalCoverageTypeLookupTest {

    private static final String UNREGISTERED_ID = "instinct:no_such_entity_type";
    private static final String MALFORMED_ID = "not a valid id!";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void clearMemo() {
        AnimalCoverage.clearTypeMemo();
    }

    @Test
    void aRegisteredIdResolvesAndIsMemoizedOnce() {
        assertEquals(0, AnimalCoverage.memoizedTypeCount(), "precondition: the memo starts empty");

        assertSame(EntityType.PARROT, AnimalCoverage.typeById("minecraft:parrot"));
        assertSame(EntityType.PARROT, AnimalCoverage.typeById("minecraft:parrot"));

        assertEquals(1, AnimalCoverage.memoizedTypeCount(),
                "a repeated lookup reuses the memoized entry rather than adding another");
    }

    @Test
    void anUnregisteredIdDoesNotFallBackToTheRegistryDefault() {
        EntityType<?> resolved = AnimalCoverage.typeById(UNREGISTERED_ID);

        assertNull(resolved, "a well-formed id naming no registered type resolves to nothing");
        assertNotSame(EntityType.PIG, resolved,
                "the defaulted registry's minecraft:pig fallback must not leak through");
    }

    @Test
    void aMalformedIdIsRejectedWithoutThrowing() {
        assertNull(AnimalCoverage.typeById(MALFORMED_ID));
    }

    @Test
    void anAbsentIdReadsAsUnresolved() {
        // CompoundTag.getString returns "" for a missing key, so this is the empty-shoulder shape.
        assertNull(AnimalCoverage.typeById(""));
    }

    @Test
    void idsThatDoNotResolveNeverGrowTheMemo() {
        for (int i = 0; i < 64; i++) {
            AnimalCoverage.typeById("instinct:junk_" + i);
            AnimalCoverage.typeById("also bad " + i);
        }

        assertEquals(0, AnimalCoverage.memoizedTypeCount(),
                "only ids that resolve are stored, so junk cannot grow the map without bound");
    }

    @Test
    void theMemoAgreesWithTheVanillaLookup() {
        for (String id : new String[]{"minecraft:parrot", "minecraft:cow", UNREGISTERED_ID}) {
            assertSame(EntityType.by(tagFor(id)).orElse(null), AnimalCoverage.typeById(id),
                    "memoized and vanilla resolution must agree for " + id);
        }
    }

    @Test
    void theMemoHardensTheOneCaseVanillaThrowsOn() {
        // EntityType.by parses with ResourceLocation.parse, which throws on a malformed id — an
        // exception that travelled out of the shoulder check into vanilla's aiStep. The memoized
        // lookup parses with tryParse instead, so a hand-edited tag reads as "not a pet".
        assertThrows(ResourceLocationException.class, () -> EntityType.by(tagFor(MALFORMED_ID)));
        assertNull(AnimalCoverage.typeById(MALFORMED_ID));
    }

    private static CompoundTag tagFor(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        return tag;
    }
}
