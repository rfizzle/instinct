package com.rfizzle.instinct.genetics;

import com.rfizzle.instinct.Instinct;
import com.rfizzle.instinct.api.Grade;
import com.rfizzle.instinct.api.InstinctAPI;
import com.rfizzle.instinct.api.InstinctAnimalBredCallback;
import com.rfizzle.instinct.api.Perk;
import com.rfizzle.instinct.config.InstinctConfig;
import com.rfizzle.instinct.coverage.AnimalCoverage;
import com.rfizzle.instinct.data.GeneticsData;
import com.rfizzle.instinct.data.InstinctAttachments;
import com.rfizzle.instinct.mixin.PanicGoalAccessor;
import com.rfizzle.instinct.registry.InstinctCriteria;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * The genetics engine ({@code design/SPEC.md} §3): grade and perk inheritance at breeding, the
 * fixed-id hardy/fleet attribute bonuses (applied at birth, re-asserted idempotently on load), the
 * placid {@link PanicGoal} swap, fertile love-cooldown scaling, and the graded death-drop bonuses.
 * The breeding and shear/egg hooks live in mixins that delegate here; this class owns the wiring
 * ({@code ENTITY_LOAD} re-assert + placid swap, {@code AFTER_DEATH} drops) and the pure-math glue
 * around {@link Genetics} and {@link ProductTable}.
 */
public final class GeneticsHandler {

    public static final ResourceLocation HEALTH_MODIFIER_ID = Instinct.id("genetic_health");
    public static final ResourceLocation SPEED_MODIFIER_ID = Instinct.id("genetic_speed");

    /** Hardy adds this much flat max health per grade; fleet adds this speed fraction per grade. */
    static final double HARDY_HEALTH_PER_GRADE = 1.0;
    static final double FLEET_SPEED_PER_GRADE = 0.04;

    /** Vanilla's post-breed love cooldown (both parents' {@code setAge(6000)}) — fertile scales it. */
    static final int LOVE_COOLDOWN_TICKS = 6000;

    /** Vanilla's chicken egg-interval base ({@code nextInt(6000) + 6000}) — grade scales the whole interval. */
    static final int EGG_INTERVAL_BASE = 6000;

    /** Radius around a dying animal in which its fresh loot is gathered for the mirror fallback. */
    private static final double DROP_SCAN_RADIUS = 2.0;

    private GeneticsHandler() {
    }

    public static void register() {
        ProductTable.init();
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Animal animal) || !AnimalCoverage.membershipOf(animal).livestock()) {
                return;
            }
            try {
                onLivestockLoad(animal);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to apply genetics on load to {}", entity.getType(), e);
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Animal animal)) {
                return;
            }
            try {
                onDeath(animal, source);
            } catch (Exception e) {
                Instinct.LOGGER.error("Failed to add genetics death drops for {}", entity.getType(), e);
            }
        });
    }

    /** Load-time reconciliation: re-assert the birth attribute bonuses and install the placid swap. */
    private static void onLivestockLoad(Animal animal) {
        reassertModifiers(animal);
        swapPlacidGoal(animal);
    }

    /**
     * Resolves a newborn's grade and perk at breeding ({@code AnimalMixin} delegates here before
     * {@code finalizeSpawnChildFromBreeding} clears the love state). Writes the child's genetics,
     * applies its birth attribute bonuses, fires the {@code bred_grade} criterion for the breeder,
     * and fires {@link InstinctAnimalBredCallback}. A pedigree-treat flag on either parent forces
     * the child prime and is consumed here (one treat per offspring).
     */
    public static void onBred(Animal parentA, Animal parentB, Animal child) {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableGenetics || !AnimalCoverage.membershipOf(child).livestock()) {
            return;
        }
        // The HEAD inject runs before vanilla's resetLove(): an uncaught throw here would strand the
        // parents in-love and re-throw every tick until the server crashes, and a third-party
        // InstinctAnimalBredCallback listener is untrusted. Degrade to "no genetics this breeding".
        try {
            resolveBirth(parentA, parentB, child, config);
        } catch (Exception e) {
            Instinct.LOGGER.error("Genetics resolution failed for {} bred from {}",
                    child.getType(), parentA.getType(), e);
        }
    }

    private static void resolveBirth(Animal parentA, Animal parentB, Animal child, InstinctConfig config) {
        DoubleSupplier roll = parentA.getRandom()::nextDouble;
        int gradeA = InstinctAPI.getGrade(parentA).level();
        int gradeB = InstinctAPI.getGrade(parentB).level();

        // Clear the flag on both parents that carry one (never short-circuit): this offspring is the
        // "next offspring" each treat promised, so a treated parent's promise never carries forward.
        boolean treatedA = consumeTreatFlag(parentA);
        boolean treatedB = consumeTreatFlag(parentB);
        boolean treated = treatedA || treatedB;
        // Well-fed feeds both the grade roll and the perk bias; scan for it once (the hay cube scan
        // is the costly part), and only when a roll will actually consult it.
        boolean wellFed = !treated && isWellFed(parentA, parentB, config);

        int childGrade;
        if (treated) {
            childGrade = Grade.PRIME.level();
        } else {
            boolean crowded = isCrowded(parentA, parentB, config);
            childGrade = Genetics.resolveGrade(gradeA, gradeB, wellFed, crowded,
                    config.gradeUpgradeChance, config.gradeDowngradeChance, roll);
        }

        Perk childPerk = Perk.NONE;
        if (childGrade >= Grade.STURDY.level()) {
            // A treated (born-prime) breeding still rolls the perk by the normal rules; its well-fed
            // bias needs the scan the grade path skipped.
            boolean perkWellFed = treated ? isWellFed(parentA, parentB, config) : wellFed;
            childPerk = Genetics.resolvePerk(InstinctAPI.getPerk(parentA), InstinctAPI.getPerk(parentB),
                    perkWellFed, roll);
        }

        GeneticsData existing = child.getAttachedOrCreate(InstinctAttachments.GENETICS);
        child.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(childGrade, childPerk, false, existing.lastTroughFeedTime()));
        applyGeneticModifiers(child, childGrade, childPerk);

        fireBredCriterion(parentA, parentB, childGrade);
        InstinctAnimalBredCallback.EVENT.invoker().onAnimalBred(parentA, parentB, child,
                Grade.fromLevel(childGrade));
    }

    /**
     * Scales each fertile parent's post-breed love cooldown ({@code AnimalMixin} delegates here at
     * the tail of {@code finalizeSpawnChildFromBreeding}, after vanilla set both to 6000). A
     * non-fertile parent is left untouched.
     */
    public static void scaleFertileCooldowns(Animal parentA, Animal parentB) {
        if (!InstinctConfig.get().enableGenetics) {
            return;
        }
        try {
            scaleFertileCooldown(parentA);
            scaleFertileCooldown(parentB);
        } catch (Exception e) {
            Instinct.LOGGER.error("Fertile cooldown scaling failed for {}", parentA.getType(), e);
        }
    }

    private static void scaleFertileCooldown(Animal parent) {
        if (!AnimalCoverage.membershipOf(parent).livestock()) {
            return;
        }
        Perk perk = InstinctAPI.getPerk(parent);
        if (perk != Perk.FERTILE) {
            return;
        }
        int scaled = Genetics.scaledLoveCooldown(LOVE_COOLDOWN_TICKS, perk,
                InstinctAPI.getGrade(parent).level());
        if (scaled != LOVE_COOLDOWN_TICKS) {
            parent.setAge(scaled);
        }
    }

    /** Clears and reports a parent's pedigree-treat flag; {@code true} if it was set. */
    private static boolean consumeTreatFlag(Animal parent) {
        GeneticsData data = parent.getAttached(InstinctAttachments.GENETICS);
        if (data == null || !data.primeNextOffspring()) {
            return false;
        }
        parent.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(data.grade(), data.perk(), false, data.lastTroughFeedTime()));
        return true;
    }

    /**
     * Well-fed ({@code design/SPEC.md} §3): a hay bale block within {@code hayRadiusBlocks} of
     * either parent, or either parent trough-fed within the last 24000 ticks.
     */
    private static boolean isWellFed(Animal parentA, Animal parentB, InstinctConfig config) {
        if (InstinctAPI.isTroughFed(parentA) || InstinctAPI.isTroughFed(parentB)) {
            return true;
        }
        return hayNearby(parentA, config.hayRadiusBlocks) || hayNearby(parentB, config.hayRadiusBlocks);
    }

    /** A spherical scan for a hay bale block within {@code radius} of the animal; early-exits. */
    private static boolean hayNearby(Animal animal, int radius) {
        Level level = animal.level();
        BlockPos center = animal.blockPosition();
        double radiusSq = (double) radius * radius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((double) dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isLoaded(cursor)) {
                        BlockState state = level.getBlockState(cursor);
                        if (state.is(Blocks.HAY_BLOCK)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Crowded ({@code design/SPEC.md} §3): more than {@code crowdingThreshold} covered animals
     * (any species, adults and babies, the parents included) within {@code crowdingRadiusBlocks} of
     * the pair's midpoint.
     */
    private static boolean isCrowded(Animal parentA, Animal parentB, InstinctConfig config) {
        double midX = (parentA.getX() + parentB.getX()) / 2.0;
        double midY = (parentA.getY() + parentB.getY()) / 2.0;
        double midZ = (parentA.getZ() + parentB.getZ()) / 2.0;
        double radius = config.crowdingRadiusBlocks;
        AABB box = new AABB(midX - radius, midY - radius, midZ - radius,
                midX + radius, midY + radius, midZ + radius);
        double radiusSq = radius * radius;
        List<Animal> nearby = parentA.level().getEntitiesOfClass(Animal.class, box,
                other -> AnimalCoverage.membershipOf(other).livestock());
        int count = 0;
        for (Animal animal : nearby) {
            double dx = animal.getX() - midX;
            double dy = animal.getY() - midY;
            double dz = animal.getZ() - midZ;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                count++;
                if (count > config.crowdingThreshold) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void fireBredCriterion(Animal parentA, Animal parentB, int grade) {
        ServerPlayer breeder = parentA.getLoveCause();
        if (breeder == null) {
            breeder = parentB.getLoveCause();
        }
        if (breeder != null) {
            InstinctCriteria.BRED_GRADE.trigger(breeder, grade);
        }
    }

    /**
     * The graded death-drop bonus ({@code design/SPEC.md} §3 yield): after any death that produced
     * loot, spawn the species' product bonus beside the vanilla drops. A species with a product row
     * uses it (cooked-in-kind when the animal died burning); a species without one mirrors its own
     * drops. Babies and grade-0 animals add nothing.
     */
    private static void onDeath(Animal animal, DamageSource source) {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableGenetics || animal.isBaby()
                || !AnimalCoverage.membershipOf(animal).livestock()) {
            return;
        }
        int grade = InstinctAPI.getGrade(animal).level();
        if (grade <= 0 || !(animal.level() instanceof ServerLevel level)) {
            return;
        }
        boolean cooked = animal.isOnFire() || source.is(DamageTypeTags.IS_FIRE);
        boolean mirrorEnabled = config.enableGenericDropMirror;
        List<ItemStack> deathDrops = mirrorEnabled && ProductTable.rowFor(animal.getType()) == null
                ? freshDropsNear(animal, level)
                : List.of();
        DoubleSupplier roll = animal.getRandom()::nextDouble;
        List<ItemStack> bonus = ProductTable.bonusDrops(animal, grade, cooked, deathDrops, mirrorEnabled, roll);
        for (ItemStack stack : bonus) {
            ItemEntity drop = new ItemEntity(level, animal.getX(), animal.getY(0.5), animal.getZ(), stack);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    /** The item entities that spawned from this animal's own death loot this tick (mirror input). */
    private static List<ItemStack> freshDropsNear(Animal animal, ServerLevel level) {
        List<ItemStack> drops = new ArrayList<>();
        AABB box = animal.getBoundingBox().inflate(DROP_SCAN_RADIUS);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getAge() == 0)) {
            drops.add(item.getItem());
        }
        return drops;
    }

    /**
     * Re-asserts (or strips) the fixed-id hardy/fleet bonuses from the animal's current genetics.
     * {@code addOrReplacePermanentModifier} recomputes so re-application replaces, never stacks, and
     * the modifiers persist in vanilla's own entity NBT. Called at birth and on every load.
     */
    public static void reassertModifiers(Animal animal) {
        boolean active = InstinctConfig.get().enableGenetics
                && AnimalCoverage.membershipOf(animal).livestock();
        if (active) {
            applyGeneticModifiers(animal, InstinctAPI.getGrade(animal).level(), InstinctAPI.getPerk(animal));
        } else {
            applyGeneticModifiers(animal, 0, Perk.NONE);
        }
    }

    /** Applies hardy (flat health) or fleet (base-multiplier speed) by grade, stripping otherwise. */
    static void applyGeneticModifiers(Animal animal, int grade, Perk perk) {
        AttributeInstance health = animal.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            if (perk == Perk.HARDY && grade > 0) {
                health.addOrReplacePermanentModifier(new AttributeModifier(
                        HEALTH_MODIFIER_ID, HARDY_HEALTH_PER_GRADE * grade,
                        AttributeModifier.Operation.ADD_VALUE));
            } else {
                health.removeModifier(HEALTH_MODIFIER_ID);
            }
        }
        AttributeInstance speed = animal.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (perk == Perk.FLEET && grade > 0) {
                speed.addOrReplacePermanentModifier(new AttributeModifier(
                        SPEED_MODIFIER_ID, FLEET_SPEED_PER_GRADE * grade,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else {
                speed.removeModifier(SPEED_MODIFIER_ID);
            }
        }
        if (animal.getHealth() > animal.getMaxHealth()) {
            animal.setHealth(animal.getMaxHealth());
        }
    }

    /**
     * Swaps an exact-class vanilla {@link PanicGoal} for a {@link PlacidPanicGoal} at the same
     * priority and speed. Idempotent: a re-load never double-swaps. A {@code PanicGoal} subclass
     * (rabbit) or a brain-based panic (goat) is left untouched, so those species keep vanilla panic
     * exactly and placid grants them state only.
     */
    private static void swapPlacidGoal(Animal animal) {
        GoalSelector selector = animal.goalSelector;
        WrappedGoal target = null;
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof PlacidPanicGoal) {
                return; // already swapped
            }
            if (wrapped.getGoal().getClass() == PanicGoal.class) {
                target = wrapped;
            }
        }
        if (target == null) {
            return;
        }
        int priority = target.getPriority();
        double speed = ((PanicGoalAccessor) target.getGoal()).instinct$getSpeedModifier();
        selector.removeGoal(target.getGoal());
        selector.addGoal(priority, new PlacidPanicGoal(animal, speed));
    }

    /** The bonus wool count a sheep shears at its grade: sturdy +1, prime +2 (§3 renewables). */
    public static int shearWoolBonus(Animal sheep) {
        if (!InstinctConfig.get().enableGenetics || !AnimalCoverage.membershipOf(sheep).livestock()) {
            return 0;
        }
        return Genetics.primaryBonus(InstinctAPI.getGrade(sheep).level());
    }

    /**
     * The chicken egg-timer's next random draw, shifted so the whole interval scales by the animal's
     * renewable-cadence factor (grade, plus the fertile perk — {@link Genetics#renewableIntervalFactor}).
     * Vanilla forms the interval as {@code raw + 6000}; returning {@code raw'} such that
     * {@code raw' + 6000 = factor × (raw + 6000)} keeps the mixin a single expression edit while
     * scaling the full interval. The returned shift is intentionally allowed to go negative — the
     * factor floors at 0.01, so the egg timer vanilla forms from it stays well positive. Clamping the
     * shift to zero would silently restore the vanilla interval for the fastest rolls.
     */
    public static int scaledEggRandom(Animal chicken, int raw) {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableGenetics || !AnimalCoverage.membershipOf(chicken).livestock()) {
            return raw;
        }
        double factor = Genetics.renewableIntervalFactor(InstinctAPI.getGrade(chicken).level(),
                InstinctAPI.getPerk(chicken), config.fertileRenewableReduction);
        if (factor >= 1.0) {
            return raw;
        }
        int interval = (int) Math.round((raw + EGG_INTERVAL_BASE) * factor);
        return interval - EGG_INTERVAL_BASE;
    }

    /**
     * The graze-roll modulus a sheep uses in {@code EatBlockGoal.canUse()}, shortened so a graded or
     * fertile sheep seeks grass — and so regrows shorn wool — faster ({@code design/SPEC.md} §3
     * renewables). Vanilla rolls {@code nextInt(bound) == 0}; scaling {@code bound} down by the
     * renewable-cadence factor raises the per-poll graze chance. Gated to covered sheep specifically
     * ({@code EatBlockGoal} is shared with other grazers), and floored at 1 so {@code nextInt} never
     * sees a zero bound. Returns {@code bound} unchanged for any other mob, an ordinary/non-fertile
     * sheep, or a genetics-disabled world.
     */
    public static int scaledGrazeInterval(Mob mob, int bound) {
        InstinctConfig config = InstinctConfig.get();
        if (!config.enableGenetics || !(mob instanceof Sheep sheep)
                || !AnimalCoverage.membershipOf(sheep).livestock()) {
            return bound;
        }
        double factor = Genetics.renewableIntervalFactor(InstinctAPI.getGrade(sheep).level(),
                InstinctAPI.getPerk(sheep), config.fertileRenewableReduction);
        if (factor >= 1.0) {
            return bound;
        }
        return Math.max(1, (int) Math.round(bound * factor));
    }

    /**
     * Sets an animal's bloodline grade outright (the {@code /instinct set grade} core), preserving
     * its perk, treat flag, and trough recency, and re-asserts the birth attribute bonuses so
     * hardy/fleet track the new grade.
     */
    public static void setGrade(Animal animal, Grade grade) {
        GeneticsData data = animal.getAttachedOrCreate(InstinctAttachments.GENETICS);
        animal.setAttached(InstinctAttachments.GENETICS,
                new GeneticsData(grade.level(), data.perk(), data.primeNextOffspring(),
                        data.lastTroughFeedTime()));
        reassertModifiers(animal);
    }

    /** Copies genetics across a conversion vanilla doesn't (mooshroom → cow shear); load re-asserts bonuses. */
    public static void copyGeneticsOnConversion(Animal from, Animal to) {
        GeneticsData data = from.getAttached(InstinctAttachments.GENETICS);
        if (data != null) {
            to.setAttached(InstinctAttachments.GENETICS, data);
        }
    }
}
