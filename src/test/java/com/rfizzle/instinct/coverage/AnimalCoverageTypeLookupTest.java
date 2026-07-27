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
 * {@code AnimalCoverage.typeById} is the memoized id → type lookup behind the shoulder path
 * ({@code mc-mod-testing} tier 2: it reads the real entity-type registry, so it bootstraps vanilla
 * rather than mocking one). Two properties carry the risk. The memo must only ever hold ids that
 * resolve, or a save-edited shoulder tag could grow it without bound; and an unknown id must read
 * as nothing, because {@code BuiltInRegistries.ENTITY_TYPE} is a defaulted registry whose plain
 * {@code get} answers {@code minecraft:pig} — which would quietly turn a bogus tag into a real
 * pets-set animal.
 *
 * <p>Ids that name nothing are remembered too, so a stale shoulder rider stops re-parsing every
 * tick, and that set carries the risk the memo's growth guard carries: its keys come from save
 * data, so the cap that bounds it is a property worth pinning rather than trusting.
 *
 * <p>The memo-size and cap assertions read shared static state, so they assume tests run
 * sequentially — which they do: the project sets no JUnit parallelism.
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
    void aRepeatedUnresolvableIdIsRememberedOnce() {
        for (int i = 0; i < 128; i++) {
            assertNull(AnimalCoverage.typeById(UNREGISTERED_ID),
                    "a remembered failure must keep reading as nothing, not soften into a type");
        }

        assertEquals(1, AnimalCoverage.typeResolveAttempts(),
                "128 ticks' worth of asking about one stale rider costs exactly one parse");
        assertEquals(1, AnimalCoverage.unresolvedIdCount(),
                "the stale rider a perched bird re-asks about every tick is remembered once");
        assertEquals(0, AnimalCoverage.memoizedTypeCount(),
                "and remembering the failure must not put anything in the resolved memo");
    }

    @Test
    void aMalformedIdIsRememberedWithoutReparsing() {
        // The tryParse-rejects path is the more expensive of the two failures, and the one a
        // hand-edited tag lands on, so it has to be remembered rather than re-parsed as well.
        for (int i = 0; i < 128; i++) {
            assertNull(AnimalCoverage.typeById(MALFORMED_ID));
        }

        assertEquals(1, AnimalCoverage.typeResolveAttempts());
        assertEquals(1, AnimalCoverage.unresolvedIdCount());
    }

    @Test
    void aRegisteredIdIsNotReparsedEither() {
        for (int i = 0; i < 128; i++) {
            assertSame(EntityType.PARROT, AnimalCoverage.typeById("minecraft:parrot"));
        }

        assertEquals(1, AnimalCoverage.typeResolveAttempts(),
                "the resolved memo must keep short-circuiting ahead of the unresolved set");
    }

    @Test
    void theUnresolvedSetStopsAtItsCap() {
        for (int i = 0; i < 128; i++) {
            assertNull(AnimalCoverage.typeById("instinct:junk_" + i));
            assertNull(AnimalCoverage.typeById("also bad " + i));
        }

        assertEquals(AnimalCoverage.UNRESOLVED_ID_CAP, AnimalCoverage.unresolvedIdCount(),
                "save-edited keys are bounded by the cap, so 256 distinct failures cannot grow it");

        // Past the cap an id parses on every ask — the cost this cache removes, handed back rather
        // than paid for with unbounded growth. Worth pinning: it is the trade the cap makes.
        int beforeReask = AnimalCoverage.typeResolveAttempts();
        assertNull(AnimalCoverage.typeById("instinct:junk_127"));
        assertEquals(beforeReask + 1, AnimalCoverage.typeResolveAttempts(),
                "an id the cap turned away is re-parsed rather than silently evicting a neighbour");
    }

    @Test
    void anAbsurdlyLongIdIsTurnedAwayRatherThanGivenASlot() {
        // A shoulder tag reaches a client over the network as well as off its own disk, and a
        // client on a remote server never fires the server-stop clear — so an id far too long to
        // name anything must not be able to park itself for the life of the JVM.
        String oversized = "instinct:" + "x".repeat(AnimalCoverage.MAX_UNRESOLVED_ID_LENGTH);

        assertNull(AnimalCoverage.typeById(oversized));

        assertEquals(0, AnimalCoverage.unresolvedIdCount(),
                "an id that could never name a real type is not worth remembering");
    }

    @Test
    void theMemoAgreesWithTheVanillaLookup() {
        for (String id : new String[]{"minecraft:parrot", "minecraft:cow", UNREGISTERED_ID}) {
            assertSame(EntityType.by(tagFor(id)).orElse(null), AnimalCoverage.typeById(id),
                    "memoized and vanilla resolution must agree for " + id);
            // Ask again, so the answer under test is the remembered one rather than the cold one:
            // a cached verdict that drifted from vanilla's would be the whole risk of caching.
            assertSame(EntityType.by(tagFor(id)).orElse(null), AnimalCoverage.typeById(id),
                    "the remembered answer must agree with vanilla too for " + id);
        }
    }

    @Test
    void theMemoHardensTheOneCaseVanillaThrowsOn() {
        // EntityType.by parses with ResourceLocation.parse, which throws on a malformed id — an
        // exception that would escape the shoulder check into vanilla's aiStep. The memoized
        // lookup parses with tryParse, so a hand-edited tag reads as "not a pet".
        assertThrows(ResourceLocationException.class, () -> EntityType.by(tagFor(MALFORMED_ID)));
        assertNull(AnimalCoverage.typeById(MALFORMED_ID));
    }

    private static CompoundTag tagFor(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        return tag;
    }
}
